package dev.usbdroid.usb
import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import dev.usbdroid.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class HostingService: Service() {
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
 private var wakeLock: PowerManager.WakeLock? = null
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onCreate() { super.onCreate(); getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("hosting", getString(R.string.hosting), NotificationManager.IMPORTANCE_LOW)) }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
  val eject = PendingIntent.getService(this, 1, Intent(this, HostingService::class.java).setAction("eject"), PendingIntent.FLAG_IMMUTABLE)
  val notification = Notification.Builder(this, "hosting").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(getString(R.string.hosting_active)).setContentText(intent?.getStringExtra("title") ?: getString(R.string.hosting)).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, getString(R.string.eject_all), eject).build()).build()
  startForeground(7, notification)
  scope.launch {
   if(!app.usb.hasSessions()) { stopSelf(); return@launch }
   if(app.settings.flow.first().keepAwake && wakeLock == null) wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBDroid:hosting").apply { acquire(12 * 60 * 60 * 1000L) }
   if(intent?.action == "eject") { try { app.usb.ejectAll(); app.usb.inspect(); stopSelf() } catch(e: Exception) { getSystemService(NotificationManager::class.java).notify(7, Notification.Builder(this@HostingService, "hosting").setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle(getString(R.string.eject_failed)).setContentText(e.message).setContentIntent(open).addAction(Notification.Action.Builder(null, getString(R.string.eject_all), eject).build()).build()) } }
  }
  return START_STICKY
 }
 override fun onDestroy() { wakeLock?.let { if(it.isHeld) it.release() }; scope.cancel(); super.onDestroy() }
}
