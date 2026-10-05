package dev.usbdroid.files

import android.app.*
import android.content.Intent
import android.os.IBinder
import dev.usbdroid.MainActivity
import dev.usbdroid.R
import dev.usbdroid.app
import kotlinx.coroutines.*

class FolderSharingService : Service() {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 private var monitor: Job? = null
 private var wakeLock: android.os.PowerManager.WakeLock? = null
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onCreate() {
  super.onCreate()
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("folder-sharing", getString(R.string.folder_share), NotificationManager.IMPORTANCE_LOW))
 }
 private fun notification(error: Boolean = false): Notification {
  val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
  val stop = PendingIntent.getService(this, 0, Intent(this, FolderSharingService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this, "folder-sharing").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(getString(if(error) R.string.folder_mtp_stopped else R.string.folder_mtp_active)).setContentText(app.folderUsb.state.value?.folder).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, getString(R.string.folder_stop), stop).build()).build()
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  startForeground(8, notification())
  if(wakeLock?.isHeld != true) wakeLock = getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "USBDroid:FolderShare").also { it.acquire() }
  if(intent?.action == "stop") scope.launch { try { app.folderUsb.stop(); stopSelf() } catch(e: CancellationException) { throw e } catch(e: Exception) { android.util.Log.e("USBDroid", "MTP cleanup failed", e); getSystemService(NotificationManager::class.java).notify(8, notification(true)) } }
  else if(monitor?.isActive != true) monitor = scope.launch {
   try {
   app.folderUsb.refresh()
   if(app.folderUsb.state.value == null) { stopSelf(); return@launch }
   while(isActive) {
    delay(3000)
    if(app.folderUsb.state.value == null) { stopSelf(); break }
    if(!app.folderUsb.alive()) { getSystemService(NotificationManager::class.java).notify(8, notification(true)); break }
   }
   } catch(e: CancellationException) { throw e } catch(e: Exception) { android.util.Log.e("USBDroid", "MTP monitoring failed", e); getSystemService(NotificationManager::class.java).notify(8, notification(true)) }
  }
  return START_STICKY
 }
 override fun onDestroy() { scope.cancel(); wakeLock?.let { if(it.isHeld) it.release() }; super.onDestroy() }
}
