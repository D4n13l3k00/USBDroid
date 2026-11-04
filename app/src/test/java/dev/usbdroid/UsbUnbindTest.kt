package dev.usbdroid

import dev.usbdroid.usb.Shell
import dev.usbdroid.usb.UsbIo
import dev.usbdroid.usb.UsbControllerBusyException
import org.junit.Assert.*
import org.junit.Test

class UsbUnbindTest {
 @Test fun alreadyUnboundDoesNotWrite() {
  var writes=0
  val io=UsbIo(object: Shell { override fun run(script: String): String { if(script.startsWith("cat ")) return ""; writes++; error("printf: write: No such device") } })
  io.write("/config/usb_gadget/usbdroid_mtp/UDC","")
  assertEquals(0,writes)
 }
 @Test fun acceptsWriteErrorOnlyWhenUnbindingSucceeded() {
  var current="controller"
  val io=UsbIo(object: Shell { override fun run(script: String): String { if(script.startsWith("cat ")) return current; current=""; error("printf: write: No such device") } })
  io.write("/config/usb_gadget/usbdroid_mtp/UDC","")
  assertEquals("",current)
 }
 @Test(expected=IllegalStateException::class) fun doesNotHideFailedUnbind() {
  val io=UsbIo(object: Shell { override fun run(script: String): String { if(script.startsWith("cat ")) return "controller"; error("printf: write: No such device") } })
  io.write("/config/usb_gadget/usbdroid_mtp/UDC","")
 }
 @Test fun identifiesControllerTakenByAnotherGadget() {
  val failure=IllegalStateException("printf: write: Device or resource busy")
  val io=UsbIo(object: Shell { override fun run(script: String): String = when {
   script.startsWith("cat ") -> ""
   script.startsWith("for p ") -> "/config/usb_gadget/g1/UDC"
   else -> throw failure
  } })
  val error=assertThrows(UsbControllerBusyException::class.java) { io.write("/config/usb_gadget/usbdroid/UDC","controller") }
  assertEquals("controller",error.controller)
  assertEquals("/config/usb_gadget/g1",error.owner)
  assertSame(failure,error.cause)
 }
 @Test fun busyWithoutAnotherControllerOwnerKeepsOriginalError() {
  val failure=IllegalStateException("printf: write: Device or resource busy")
  val io=UsbIo(object: Shell { override fun run(script: String): String {
   if(script.startsWith("cat ") || script.startsWith("for p ")) return ""
   throw failure
  } })
  assertSame(failure,assertThrows(IllegalStateException::class.java) { io.write("/config/usb_gadget/usbdroid/UDC","controller") })
 }
 @Test fun busyImageWriteIsNotAControllerConflict() {
  val failure=IllegalStateException("printf: write: Device or resource busy")
  val io=UsbIo(object: Shell { override fun run(script: String): String = throw failure })
  assertSame(failure,assertThrows(IllegalStateException::class.java) { io.write("/config/usb_gadget/usbdroid/functions/mass_storage.0/lun.0/file","/image.img") })
 }
 @Test fun failedOwnerInspectionKeepsOriginalError() {
  val failure=IllegalStateException("printf: write: Device or resource busy")
  val io=UsbIo(object: Shell { override fun run(script: String): String {
   if(script.startsWith("cat ")) return ""
   if(script.startsWith("for p ")) error("Root command timed out")
   throw failure
  } })
  assertSame(failure,assertThrows(IllegalStateException::class.java) { io.write("/config/usb_gadget/usbdroid/UDC","controller") })
 }
}
