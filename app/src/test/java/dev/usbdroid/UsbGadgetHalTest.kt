package dev.usbdroid

import dev.usbdroid.usb.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UsbGadgetHalTest {
 private val gadget = "/config/usb_gadget/usbdroid"
 private val system = "/config/usb_gadget/g1/UDC"
 private val controller = "controller0"
 private val lun = Lun("$gadget/functions/mass_storage.0/lun.0", "", true, false, true, true)
 private fun snapshot() = JSONObject().put("gadget", gadget).put("config", "$gadget/configs/c.1")
  .put("udc", "").put("links", JSONObject()).put("hiddenLinks", JSONObject()).put("descriptors", JSONObject())
  .put("otherControllers", JSONObject().put(system, controller)).put("compatibility", true).put("halFunctions", 1L)

 private inner class HalShell : Shell {
  var systemController = controller
  var privateController = ""
  var functions = 1L
  var reject = false
  var failBind = false
  var linked = true
  override fun run(script: String): String {
   val values = Regex("'([^']*)'").findAll(script).map { it.groupValues[1] }.toList()
   return when {
    script.startsWith("service call ") -> {
     if(reject) return "Result: Parcel(ffffffff)"
     val requested = Regex("1 i64 (\\d+) null").find(script)!!.groupValues[1].toLong()
     check(requested == 0L || privateController.isEmpty()) { "Controller still owned by USBDroid" }
     functions = requested; systemController = if(requested == 0L) "" else controller
     "Result: Parcel(NULL)"
    }
    script.startsWith("cat ") -> when(values.single()) { system -> systemController; "$gadget/UDC" -> privateController; else -> error("Unexpected node") }
    script.startsWith("printf ") -> {
     val path = values[2]; val value = values[1]
     if(path == "$gadget/UDC") {
      check(value.isEmpty() || (!failBind && systemController.isEmpty())) { "Device or resource busy" }
      privateController = value
     } else error("HAL must restore its own controller")
     ""
    }
    script.startsWith("ls /sys/class/udc") -> controller
    script.startsWith("for p ") -> if(linked) lun.path.substringBefore("/lun.") else ""
    script.startsWith("ln -s ") -> { linked = true; "" }
    script.startsWith("if [ -L ") -> if(script.contains("rm ")) { linked = false; "" } else lun.path.substringBefore("/lun.")
    else -> error("Unexpected command: $script")
   }
  }
 }
 @Test fun releasesHalBeforeBindingAndRestoresFromPersistedSnapshot() {
  val shell = HalShell(); val state = snapshot()
  ConfigFsBackend(UsbIo(shell), gadget).activate(lun, "mass_storage", state)
  assertEquals(controller, shell.privateController); assertEquals("", shell.systemController); assertEquals(0L, shell.functions)
  ConfigFsBackend(UsbIo(shell), gadget).restore(JSONObject(state.toString()))
  assertEquals("", shell.privateController); assertEquals(controller, shell.systemController); assertEquals(1L, shell.functions)
 }
 @Test fun failedPrivateBindCanRestoreOriginalHalFunctions() {
  val shell = HalShell().apply { failBind = true }; val state = snapshot()
  val backend = ConfigFsBackend(UsbIo(shell), gadget)
  assertThrows(IllegalStateException::class.java) { backend.activate(lun, "mass_storage", state) }
  backend.restore(state)
  assertEquals(controller, shell.systemController); assertEquals("", shell.privateController); assertEquals(1L, shell.functions)
 }
 @Test fun rejectedHalReleaseDoesNotTakeController() {
  val shell = HalShell().apply { reject = true }
  assertThrows(IllegalStateException::class.java) { ConfigFsBackend(UsbIo(shell), gadget).activate(lun, "mass_storage", snapshot()) }
  assertEquals(controller, shell.systemController); assertEquals("", shell.privateController)
 }
 @Test fun preservesCombinedFunctionsAndRejectsUnknownVendorModes() {
  assertEquals(5L, UsbGadgetHal.functions("mtp,adb"))
  assertEquals(1025L, UsbGadgetHal.functions("ncm,adb"))
  assertEquals(0L, UsbGadgetHal.functions("none"))
  assertThrows(IllegalStateException::class.java) { UsbGadgetHal.functions("diag,adb") }
  assertThrows(IllegalArgumentException::class.java) { UsbGadgetHal.functions("") }
 }
}
