package dev.usbdroid.usb

enum class ConnectionState { DETACHED, ATTACHED, CONFIGURED, SUSPENDED, UNKNOWN;
 companion object {
  fun from(raw: String): ConnectionState {
   val states = raw.lineSequence().map { it.trim().lowercase(java.util.Locale.ROOT).replace(' ', '-') }.filter { it.isNotEmpty() }.map {
    when(it) {
     "disconnected", "not-attached" -> DETACHED
     "connected", "attached", "powered", "default", "addressed" -> ATTACHED
     "configured" -> CONFIGURED
     "suspended" -> SUSPENDED
     else -> UNKNOWN
    }
   }.toSet()
   return states.singleOrNull() ?: UNKNOWN
  }
 }
}
