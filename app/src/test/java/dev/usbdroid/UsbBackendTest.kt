package dev.usbdroid
import dev.usbdroid.usb.*
import org.junit.Test
import org.junit.Assert.*

class UsbBackendTest {
 class FakeShell: Shell {
  val calls = mutableListOf<String>()
  private val nodes = mutableMapOf<String, String>(); private val links = mutableMapOf<String, String>()
  override fun run(script: String): String {
   calls += script
   val values = Regex("'([^']*)'").findAll(script).map { it.groupValues[1] }.toList()
   return when {
    script.startsWith("find") && script.contains("/configs") -> "/config/usb_gadget/custom/configs/c.2"
    script.startsWith("cat") -> nodes[values.first()] ?: "controller0"
    script.startsWith("printf") -> { nodes[values[2]] = values[1]; "" }
    script.startsWith("ln -s") -> { links[values[1]] = values[0]; "" }
    script.startsWith("for p") -> links.values.joinToString("\n")
    script.startsWith("if [ -L") && !script.contains("rm ") -> links[values.first()].orEmpty()
    script.contains("rm ") -> { links.remove(values.first()); "" }
    else -> ""
   }
  }
 }
 val lun = Lun("/config/usb_gadget/custom/functions/mass_storage.usbdroid/lun.0", "", true, false, true, true)
 @Test fun arbitraryGadgetConfigurationAndJournalBeforeMutation() {
  val shell = FakeShell(); var persisted = false; val backend = ConfigFsBackend(UsbIo(shell), "/config/usb_gadget/custom") { persisted = true }
  val state = backend.capture(lun); assertEquals("/config/usb_gadget/custom/configs/c.2", state.getString("config")); backend.activate(lun, "mass_storage,adb", state)
  assertTrue(persisted); assertEquals(1, state.getJSONObject("links").length()); assertFalse(shell.calls.any { it.contains("b.1") || it.contains("g1/") })
  backend.restore(state); assertTrue(shell.calls.any { it.contains("readlink -f") && it.contains("rm ") }); assertTrue(shell.calls.last().contains("controller0"))
 }
 @Test fun legacyRestoresItsOwnBackend() { val shell = FakeShell(); val backend = LegacyBackend(UsbIo(shell), "functions"); val snapshot = backend.capture(lun); backend.activate(lun, "mass_storage,adb", snapshot); backend.restore(snapshot); assertTrue(shell.calls.last().contains("/enable")); assertFalse(shell.calls.any { it.contains("setprop") }) }
 @Test fun rootValuesAreQuoted() { assertEquals("'a'\"'\"'b'", RootShell.quote("a'b")); assertThrows(IllegalArgumentException::class.java) { UsbIo(FakeShell()).validate("/sys/../data/file") } }
}
