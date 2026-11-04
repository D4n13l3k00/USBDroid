package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MtpOfflineDeviceTest {
 @Test fun startWithoutCableAndStopTwice() = runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.app
  assumeTrue(!File(app.filesDir,"folder-mtp.json").exists())
  assumeTrue(app.usb.inspect().luns.all { it.file.isBlank() })
  val source=File(app.cacheDir,"mtp-offline-test").apply { mkdirs() }
  File(source,"hello.txt").writeText("offline")
  try {
   app.folderUsb.start(source.path,false)
   assertNotNull(app.folderUsb.state.value)
   assertFalse(app.folderUsb.state.value!!.connected)
   app.folderUsb.stop(); app.folderUsb.stop()
   assertNull(app.folderUsb.state.value)
   assertFalse(File(app.filesDir,"folder-mtp.json").exists())
  } finally { app.folderUsb.stop(); source.deleteRecursively() }
 }
}
