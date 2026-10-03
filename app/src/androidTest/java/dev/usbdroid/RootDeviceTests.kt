package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.usbdroid.data.*
import dev.usbdroid.usb.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class RootDeviceTests {
 @Test fun pcFixture() = runBlocking {
  val args = InstrumentationRegistry.getArguments(); val action = args.getString("pcMode")
  Assume.assumeTrue("Requires explicit pcMode argument", action != null)
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  val image = File(app.library.directory, if(action == "CDROM") ".pc-test.iso" else ".pc-test.img")
  if(action == "EJECT") {
   if(app.usb.hasSessions()) app.usb.ejectAll()
   File(app.library.directory, ".pc-test.iso").delete(); image.delete(); return@runBlocking
  }
  if(!image.exists()) {
   if(action == "CDROM") app.assets.open("testboot/test-cd.iso").use { source -> image.outputStream().use { source.copyTo(it) } }
   else { RandomAccessFile(image, "rw").use { it.setLength(32L * 1048576) }; FatFormatter.format(image, image.length()); TestBootImage.install(image, app.assets.open("testboot/bios.bin").use { it.readBytes() }, app.assets.open("testboot/bootx64.efi").use { it.readBytes() }) }
  }
  val mode = when(action) { "RO" -> HostMode.READ_ONLY; "RW" -> HostMode.WRITABLE; "CDROM" -> HostMode.CDROM; else -> error("Invalid pcMode") }
  val lun = app.usb.inspect().luns.first { it.file.isBlank() || it.file == image.canonicalPath }
  app.usb.host(DiskImage(image.path, image.name, image.length(), image.lastModified()), lun, mode, Preferences())
  assertEquals(image.canonicalPath, app.usb.inspect().luns.first { it.path == lun.path }.file)
 }
 @Test fun kernelModesAndControllerRestartRestoreOriginalUsb() = runBlocking {
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  val original = app.usb.inspect()
  assertTrue(original.error, original.root)
  val lun = original.luns.firstOrNull { it.file.isBlank() } ?: error("No unused LUN")
  val image = File(app.library.directory, ".kernel-test.img")
  RandomAccessFile(image, "rw").use { it.setLength(32L * 1048576) }
  FatFormatter.format(image, image.length())
  val gadget = lun.path.substringBefore("/functions/")
  val udc = RootShell.run("cat ${RootShell.quote("$gadget/UDC")}")
  try {
   for(mode in HostMode.entries.filter { (!it.cd || lun.supportsCdrom) && (it.ro || lun.supportsReadOnly) }) {
    app.usb.host(DiskImage(image.path, image.name, image.length(), image.lastModified()), lun, mode, Preferences())
    val active = app.usb.inspect().luns.first { it.path == lun.path }
    assertEquals(image.canonicalPath, active.file)
    assertEquals(mode.ro, active.readOnly); assertEquals(mode.cd, active.cdrom)
    // A fresh controller has no process state; it must recover from the on-disk journal.
    UsbController(app).eject(active)
    val restored = app.usb.inspect().luns.first { it.path == lun.path }
    assertEquals(lun.file, restored.file); assertEquals(lun.readOnly, restored.readOnly); assertEquals(lun.cdrom, restored.cdrom)
    assertEquals(udc, RootShell.run("cat ${RootShell.quote("$gadget/UDC")}"))
   }
  } finally { if(app.usb.hasSessions()) app.usb.ejectAll(); image.delete() }
 }
}
