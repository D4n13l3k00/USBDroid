package dev.usbdroid.usb

import org.json.JSONObject

/** Stable AIDL IUsbGadget: transaction 1 is setCurrentUsbFunctions(long, callback, long, long). */
internal class UsbGadgetHal(private val io: UsbIo) {
 fun available(): Boolean = io.shell.run("service check $SERVICE").trim() == "Service $SERVICE: found"

 fun setFunctions(functions: Long, controllers: JSONObject, released: Boolean) {
  require(functions >= 0)
  val result = io.shell.run("service call $SERVICE 1 i64 $functions null i64 3000 i64 0")
  check(result.trim() == "Result: Parcel(NULL)") { "USB gadget HAL rejected function change: $result" }
  // The HAL tears down FunctionFS synchronously but rebinds it asynchronously.
  val paths = controllers.keys().asSequence().toList()
  paths.forEach(io::validate)
  repeat(50) {
   if(paths.all { path -> io.read(path) == if(released) "" else controllers.getString(path) }) return
   Thread.sleep(100)
  }
  error(if(released) "USB gadget HAL did not release the controller" else "USB gadget HAL did not restore the controller")
 }

 companion object {
  private const val SERVICE = "android.hardware.usb.gadget.IUsbGadget/default"
  fun functions(config: String): Long {
   require(config.isNotBlank()) { "Cannot capture USB HAL functions: empty USB configuration" }
   if(config == "none") return 0
   return config.split(',').fold(0L) { bits, function -> bits or when(function) {
    "adb" -> 1L
    "accessory" -> 2L
    "mtp" -> 4L
    "midi" -> 8L
    "ptp" -> 16L
    "rndis" -> 32L
    "audio_source" -> 64L
    "uvc" -> 128L
    "ncm" -> 1024L
    else -> error("Cannot safely restore USB HAL function: $function")
   } }
  }
 }
}
