package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.usbdroid.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class PartitionImageDeviceTest {
 @Test fun allFilesystemsPreservePartitionMetadata() = runBlocking {
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  for(layout in listOf(ImageLayout.MBR, ImageLayout.GPT)) for(fs in ImageFilesystem.entries.filter { it != ImageFilesystem.NONE }) {
   val image = File.createTempFile(".format-test-", ".partial", app.library.directory)
   try {
    RandomAccessFile(image, "rw").use { it.setLength(130L * 1048576) }
    ImageFormatter.format(app, image, app.library.directory, fs, layout)
    RandomAccessFile(image, "r").use { input ->
     val header = ByteArray(17408); input.readFully(header)
     assertEquals(1048576L, dev.usbdroid.files.AccessPolicy.offset(header, image.length()))
     val signatureOffset = when(fs) { ImageFilesystem.EXT4 -> 1024 + 56; ImageFilesystem.BTRFS -> 65536 + 64; else -> 3 }
     input.seek(1048576L + signatureOffset)
     if(fs == ImageFilesystem.EXT4) { assertEquals(0x53, input.readUnsignedByte()); assertEquals(0xef, input.readUnsignedByte()) }
     else {
      val signature = ByteArray(8); input.readFully(signature)
      assertEquals(when(fs) { ImageFilesystem.EXFAT -> "EXFAT   "; ImageFilesystem.NTFS -> "NTFS    "; ImageFilesystem.BTRFS -> "_BHRfS_M"; else -> "USBDROID" }, String(signature))
     }
    }
   } finally { image.delete() }
  }
 }
 @Test fun partitionedImagesCanBeMountedAndEdited() = runBlocking {
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  for(layout in listOf(ImageLayout.MBR, ImageLayout.GPT)) {
   val image = File.createTempFile(".partition-test-", ".partial", app.library.directory)
   var mounted: String? = null
   try {
    RandomAccessFile(image, "rw").use { it.setLength(130L * 1048576) }
    ImageFormatter.format(app, image, app.library.directory, ImageFilesystem.FAT32, layout)
    val access = app.imageAccess
    val mount = access.mount(image.path, "Partition test", false); mounted = mount.id
    assertEquals(1048576L, mount.offset)
    access.create(mount.id, "", "test.txt", false)
    val before = access.readText(mount.id, "test.txt")
    access.writeText(mount.id, "test.txt", "partition works", before.second)
    assertEquals("partition works", access.readText(mount.id, "test.txt").first)
    access.unmount(mount.id); mounted = null
    val readOnly = access.mount(image.path, "Partition test", true); mounted = readOnly.id
    assertEquals("partition works", access.readText(readOnly.id, "test.txt").first)
   } finally {
    mounted?.let { app.imageAccess.unmount(it) }
    image.delete()
   }
  }
 }
}
