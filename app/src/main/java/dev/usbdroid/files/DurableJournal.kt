package dev.usbdroid.files
import java.io.File

internal object DurableJournal {
 fun write(file: File, value: String) {
  val staging = File(file.parentFile, "${file.name}.tmp")
  staging.outputStream().use { it.write(value.toByteArray()); it.fd.sync() }
  check(staging.renameTo(file)) { "Cannot save operation journal" }
 }
}
