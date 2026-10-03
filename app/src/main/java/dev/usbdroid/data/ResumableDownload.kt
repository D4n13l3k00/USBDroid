package dev.usbdroid.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

data class DownloadIdentity(val etag: String = "", val modified: String = "")
class ResumableDownload(private val client: OkHttpClient) {
 fun download(url: String, partial: File, identity: DownloadIdentity, allowedHttp: Boolean, stopped: () -> Boolean, metadata: (DownloadIdentity, Long) -> Unit, progress: (Long, Long) -> Unit) {
  require(url.startsWith("https://") || (allowedHttp && url.startsWith("http://"))) { "HTTPS required; enable HTTP for this source explicitly" }
  val offset = if(identity.etag.isNotBlank() || identity.modified.isNotBlank()) partial.length() else 0
  val builder = Request.Builder().url(url)
  if(offset > 0) builder.header("Range", "bytes=$offset-").header("If-Range", identity.etag.ifBlank { identity.modified })
  client.newCall(builder.build()).execute().use { response ->
   require(response.request.url.isHttps || allowedHttp) { "Insecure redirect blocked" }
   if(response.code == 416) { partial.delete(); throw IllegalStateException("Remote size changed. Retry to restart download") }
   check(response.isSuccessful) { "HTTP ${response.code}" }
   val newIdentity = DownloadIdentity(response.header("ETag").orEmpty(), response.header("Last-Modified").orEmpty())
   val range = response.header("Content-Range").orEmpty()
   val append = offset > 0 && response.code == 206
   if(append) {
    require(range.startsWith("bytes $offset-")) { "Invalid Content-Range" }
    if(identity.etag.isNotBlank()) require(newIdentity.etag == identity.etag) { "ETag changed; retry from the beginning" }
    else require(newIdentity.modified == identity.modified) { "Remote file changed" }
   }
   require(response.code != 206 || append || range.startsWith("bytes 0-")) { "Unexpected partial response" }
   val body = response.body ?: error("Empty response")
   val start = if(append) offset else 0
   val total = if(append) range.substringAfter('/').toLongOrNull() ?: -1 else body.contentLength()
   require(total < 0 || partial.parentFile!!.usableSpace >= total - start) { "Not enough storage" }
   metadata(newIdentity, total)
   RandomAccessFile(partial, "rw").use { out ->
    if(!append) out.setLength(0)
    out.seek(start); var done = start
    body.byteStream().use { input ->
     val buffer = ByteArray(256 * 1024)
     while(true) { if(stopped()) throw InterruptedException("Download paused"); val count = input.read(buffer); if(count < 0) break; out.write(buffer, 0, count); done += count; progress(done, total) }
    }
    if(total >= 0) check(done == total) { "Incomplete response: $done / $total" }
    out.fd.sync()
   }
  }
 }
}
