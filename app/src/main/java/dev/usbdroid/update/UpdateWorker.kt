package dev.usbdroid.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.*
import dev.usbdroid.R
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class UpdateWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
 override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
  val directory = File(applicationContext.filesDir, "updates").apply { mkdirs() }
  val partial = File(directory, "$id.partial")
  val target = File(directory, "update.apk")
  try {
   setForeground(notification(0))
   val url = inputData.getString("url") ?: error("asset")
   require(url.startsWith("https://github.com/${GitHubUpdates.repository}/releases/download/")) { "asset" }
   val expectedSize = inputData.getLong("size", 0)
   require(expectedSize in 1..536870912L) { "asset" }
   require(directory.usableSpace > expectedSize + 1024 * 1024) { "space" }
   val hash = MessageDigest.getInstance("SHA-256")
   GitHubUpdates.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
    check(response.isSuccessful && response.request.url.isHttps) { "network" }
    val body = response.body ?: error("network")
    body.byteStream().use { source -> partial.outputStream().use { destination ->
     val buffer = ByteArray(65536); var bytes = 0L; var last = 0L
     while(true) {
      ensureActive()
      val count = source.read(buffer); if(count < 0) break
      bytes += count; require(bytes <= expectedSize) { "invalid_apk" }
      destination.write(buffer, 0, count); hash.update(buffer, 0, count)
      val now = android.os.SystemClock.elapsedRealtime()
      if(now - last >= 500) { last = now; val progress = (bytes * 100 / expectedSize).toInt(); setProgress(workDataOf("progress" to progress)); setForeground(notification(progress)) }
     }
     require(bytes == expectedSize) { "invalid_apk" }
    } }
   }
   val digest = inputData.getString("digest").orEmpty()
   if(digest.isNotEmpty()) require(digest == "sha256:" + hash.digest().joinToString("") { "%02x".format(it) }) { "invalid_apk" }
   GitHubUpdates.validate(applicationContext, partial)
   ensureActive()
   check(partial.renameTo(target)) { "space" }
   Result.success(workDataOf("version" to inputData.getString("version")))
  } catch(e: CancellationException) { throw e }
  catch(e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "network"))) }
  finally { partial.delete() }
 }
 private fun notification(progress: Int): ForegroundInfo {
  val context = applicationContext
  context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("updates", context.getString(R.string.update_title), NotificationManager.IMPORTANCE_LOW))
  val notification = Notification.Builder(context, "updates").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(context.getString(R.string.update_downloading)).setProgress(100, progress, false).setOngoing(true).addAction(Notification.Action.Builder(null, context.getString(R.string.cancel), WorkManager.getInstance(context).createCancelPendingIntent(id)).build()).build()
  return if(Build.VERSION.SDK_INT >= 29) ForegroundInfo(9102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(9102, notification)
 }
}
