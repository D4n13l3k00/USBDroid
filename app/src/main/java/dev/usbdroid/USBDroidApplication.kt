package dev.usbdroid

import android.app.Application
import dev.usbdroid.data.*
import dev.usbdroid.usb.UsbController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class USBDroidApplication : Application() {
 val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 val database by lazy { AppDatabase.create(this) }
 val settings by lazy { SettingsStore(this) }
 val usb by lazy { UsbController(this) }
 val library by lazy { Library(this, database.dao(), usb) }
 override fun onCreate() { super.onCreate(); scope.launch { library.initialize(); library.scan() }; scope.launch { dev.usbdroid.update.UpdateChecks.check(this@USBDroidApplication) } }
}
val android.content.Context.app: USBDroidApplication get() = applicationContext as USBDroidApplication
