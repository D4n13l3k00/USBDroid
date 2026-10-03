package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import dev.usbdroid.data.*
import dev.usbdroid.ui.latestChecksum
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class DownloadLifecycleTests {
 @Test fun downloadPauseResumePreservesBytesAndCreatesReadyHashes() = runBlocking {
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  app.library.initialize()
  val payload = ByteArray(4 * 1048576) { (it % 251).toByte() }
  val server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
  val running = AtomicBoolean(true); val resumed = AtomicBoolean(false)
  val responder = Thread {
   while(running.get()) runCatching { server.accept().use { socket ->
    val reader = socket.getInputStream().bufferedReader(); val headers = mutableListOf<String>()
    while(true) { val line = reader.readLine() ?: break; if(line.isEmpty()) break; headers += line }
    val offset = headers.firstOrNull { it.startsWith("Range:", true) }?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
    if(offset > 0) resumed.set(true)
    val output = socket.getOutputStream()
    val range = if(offset > 0) "Content-Range: bytes $offset-${payload.lastIndex}/${payload.size}\r\n" else ""
    output.write("HTTP/1.1 ${if(offset > 0) "206 Partial Content" else "200 OK"}\r\nContent-Length: ${payload.size - offset}\r\nETag: fixture-v1\r\n${range}Connection: close\r\n\r\n".toByteArray())
    var at = offset
    while(at < payload.size && running.get()) { val count = minOf(65536, payload.size - at); output.write(payload, at, count); output.flush(); at += count; Thread.sleep(35) }
   } }
  }.apply { isDaemon = true; start() }
  val id = UUID.randomUUID().toString(); val model = AppViewModel(app)
  val args = JSONObject().put("url", "http://127.0.0.1:${server.localPort}/fixture.iso").put("name", "ux-resume-$id.iso").put("allowHttp", true).put("storage", "default")
  var result: ImageEntry? = null
  try {
   app.database.dao().putJob(TransferJob(id, "DOWNLOAD", "Pause/resume fixture", args.toString()))
   WorkManager.getInstance(app).enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ImageWorker>().setInputData(workDataOf("id" to id)).build()).result.get()
   withTimeout(15000) { while(true) { val job = app.database.dao().job(id)!!; if(job.progress >= 1048576 && JSONObject(job.args).optLong("_speed") > 0) break; delay(100) } }
   withContext(Dispatchers.Main) { model.pause(id) }
   withTimeout(10000) { while(app.database.dao().job(id)!!.state != "PAUSED" || model.state.value.working("job:$id")) delay(50) }
   val paused = app.database.dao().job(id)!!; assertTrue(paused.progress > 0 && paused.progress < payload.size)
   withContext(Dispatchers.Main) { model.resume(id) }
   val completed = withTimeout(20000) { while(true) { val job = app.database.dao().job(id)!!; if(job.state in listOf("DONE", "FAILED")) return@withTimeout job; delay(100) }; error("Unreachable") }
   assertEquals(completed.error, "DONE", completed.state); assertTrue(resumed.get())
   result = app.database.dao().image(completed.result)!!
   assertArrayEquals(payload, File(result.physicalPath!!).readBytes())
   val jobs = app.database.dao().jobs().first()
   for(algorithm in listOf("MD5", "SHA-1", "SHA-256")) {
    val hash = latestChecksum(jobs, result, algorithm)!!
    assertEquals("$algorithm\n" + MessageDigest.getInstance(algorithm).digest(payload).joinToString("") { "%02x".format(it) }, hash.result)
   }
  } finally {
   running.set(false); server.close(); responder.join(1000)
   WorkManager.getInstance(app).cancelUniqueWork(id).result.get()
   result?.let { File(it.physicalPath!!).delete(); app.database.dao().deleteImage(it.id) }
   app.database.dao().jobs().first().filter { it.id == id || it.id.startsWith("$id:hash:") }.forEach { app.database.dao().deleteJob(it.id) }
   File(app.library.directory, ".$id.partial").delete()
  }
  Unit
 }
}
