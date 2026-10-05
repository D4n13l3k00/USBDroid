package dev.usbdroid.data

import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.*
import dev.usbdroid.app
import dev.usbdroid.usb.RootShell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ImageLocks {
 private val locks = ConcurrentHashMap<String, Mutex>()
 suspend fun <T> use(id: String, block: suspend () -> T): T = locks.getOrPut(id) { Mutex() }.withLock { block() }
 suspend fun <T> useAll(ids: List<String>, block: suspend () -> T): T {
  val ordered = ids.distinct().sorted()
  suspend fun next(index: Int): T {
   return if(index == ordered.size) block() else use(ordered[index]) { next(index + 1) }
  }
  return next(0)
 }
}
class ImageWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context, parameters) {
 private val app = context.app
 private val dao = app.database.dao()
 private val library = app.library
 private lateinit var job: TransferJob
 private var lastUpdate = 0L
 private var lastBytes = 0L
 private var lastPhase = ""
 private var speed = 0.0
 override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
  job = dao.job(inputData.getString("id") ?: return@withContext Result.failure()) ?: return@withContext Result.failure()
  if(job.state in listOf("PAUSED", "CANCELLED", "DONE")) return@withContext Result.success()
  job = job.copy(state = "RUNNING", error = "", args = JSONObject(job.args).put("_owner", id.toString()).put("_phase", if(job.kind == "DOWNLOAD") "downloading" else "working").put("_speed", 0).put("_eta", -1).toString()); dao.putJob(job)
  try {
   setForeground(notification())
   val args = JSONObject(job.args)
   if(job.kind == "FOLDER_COPYBACK") execute(args) else ImageLocks.use(args.optString("image", job.id)) { execute(args) }
   job = job.copy(state = "DONE", progress = job.total.takeIf { it > 0 } ?: job.progress); dao.putJob(job)
   library.scan(); Result.success()
  } catch(e: CancellationException) { withContext(NonCancellable) { val current = dao.job(job.id); if(current?.state == "RUNNING" && JSONObject(current.args).optString("_owner") == id.toString()) dao.jobState(job.id, "PAUSED") }; throw e }
  catch(e: Exception) { val current = dao.job(job.id); if(current?.state !in listOf("PAUSED", "CANCELLED") && current != null && JSONObject(current.args).optString("_owner") == id.toString()) dao.putJob(job.copy(state = "FAILED", error = e.message ?: e.javaClass.simpleName)); Result.failure() }
 }
 private fun notification(): ForegroundInfo {
  val manager = applicationContext.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("operations", applicationContext.getString(dev.usbdroid.R.string.jobs), NotificationManager.IMPORTANCE_LOW))
  val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
  val notification = Notification.Builder(applicationContext, "operations").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(job.title).setContentText(job.kind).setProgress(100, if(job.total > 0) (100 * job.progress / job.total).toInt() else 0, job.total <= 0).setOngoing(true).addAction(Notification.Action.Builder(null, applicationContext.getString(dev.usbdroid.R.string.cancel), cancel).build()).build()
  return if(Build.VERSION.SDK_INT >= 29) ForegroundInfo(job.id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(job.id.hashCode(), notification)
 }
 private suspend fun progress(done: Long, total: Long, force: Boolean = false) {
  val now = android.os.SystemClock.elapsedRealtime()
  if(force || now - lastUpdate >= 500) {
   val current = dao.job(job.id)
   if(current?.state in listOf("PAUSED", "CANCELLED") || current == null || JSONObject(current.args).optString("_owner") != id.toString()) throw InterruptedException("Stopped")
   val args = JSONObject(job.args); val phase = args.optString("_phase")
   if(lastUpdate == 0L || phase != lastPhase || done < lastBytes) speed = 0.0
   else if(now > lastUpdate && done > lastBytes) {
    val sample = (done - lastBytes) * 1000.0 / (now - lastUpdate)
    speed = if(speed <= 0) sample else speed * .65 + sample * .35
   }
   lastUpdate = now; lastBytes = done; lastPhase = phase
   args.put("_speed", speed.toLong()).put("_eta", if(speed > 0 && total > done) ((total - done) / speed).toLong() else -1)
   job = job.copy(progress = done, total = total, args = args.toString()); dao.putJob(job)
   setProgress(workDataOf("bytes" to done, "total" to total)); setForeground(notification())
  }
 }

 private suspend fun copy(input: InputStream, output: OutputStream, total: Long) { input.use { source -> output.use { destination -> val buffer = ByteArray(1024 * 1024); var done = 0L; while(true) { ensureActive(); if(isStopped) throw InterruptedException("Stopped"); val n = source.read(buffer); if(n < 0) break; destination.write(buffer, 0, n); done += n; progress(done, total) }; destination.flush(); progress(done, total, true) } } }
 private fun localSource(image: ImageEntry): InputStream = try { library.input(image.location) } catch(e: Exception) { val stage = File(applicationContext.cacheDir, "${job.id}.source"); RootShell.run("cp ${RootShell.quote(image.physicalPath ?: throw e)} ${RootShell.quote(stage.path)}; chmod 644 ${RootShell.quote(stage.path)}", 600); stage.inputStream() }
 private suspend fun execute(a: JSONObject) {
  if(job.kind == "FILE_TRANSFER") {
   val paths = a.getJSONArray("paths").let { list -> (0 until list.length()).map { list.getString(it) } }
   progress(0, paths.size.toLong(), true)
   app.imageAccess.transfer(a.getString("mount"), paths, a.getString("parent"), a.getBoolean("move")) { done, total -> runBlocking { ensureActive(); if(isStopped) throw CancellationException("Stopped"); progress(done.toLong(), total.toLong(), true) } }
   job = job.copy(result = a.getString("image")); return
  }
  if(job.kind == "FOLDER_COPYBACK") {
   val image = dao.image(a.getString("image")) ?: error("Image no longer exists")
   val items = a.getJSONArray("items").let { list -> (0 until list.length()).map { list.getJSONObject(it) }.map { item -> dev.usbdroid.files.CopyBackItem(item.getString("relative"), item.getBoolean("directory"), item.getLong("bytes"), item.getString("imageHash"), if(item.has("targetHash") && !item.isNull("targetHash")) item.getString("targetHash") else null, item.getBoolean("blocked")) } }
   app.imageAccess.refresh()
   dev.usbdroid.files.FolderCopyBack(applicationContext).apply(dev.usbdroid.files.CopyBackPlan(image, a.getString("source"), items), a.getBoolean("replace")) { done, total -> ensureActive(); if(isStopped) throw CancellationException("Stopped"); progress(done, total, true) }
   job = job.copy(result = image.id); return
  }
  if(job.kind == "FOLDER_IMAGE") {
   val entry = dev.usbdroid.files.FolderImage(applicationContext).create(a.getString("source"), job.title, ImageFilesystem.valueOf(a.getString("filesystem")), a.getLong("extraMiB")) { value ->
    job = job.copy(args = JSONObject(job.args).put("_file", value.file).toString())
    progress(value.bytes, value.total, true)
   }
   job = job.copy(args = JSONObject(job.args).put("image", entry.id).toString())
   ImageLocks.use(entry.id) { saveHashes(entry, calculateAllHashes(File(entry.location))) }
   job = job.copy(result = entry.id)
   return
  }
  if(job.kind in listOf("CREATE", "DOWNLOAD", "IMPORT", "MOVE", "COPY_HOST")) Library.validateName(a.getString("name"))
  val temp = File(library.directory, ".${job.id}.partial")
  val image = if(a.has("image")) dao.image(a.getString("image")) ?: error("Image no longer exists") else null
  require(image?.isMtp != true) { "This operation requires a disk image" }
  if(image != null) library.assertDetached(image)
  when(job.kind) {
   "DOWNLOAD" -> {
    val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()
    if(!a.optBoolean("_downloadComplete") || !temp.isFile) {
     ResumableDownload(client).download(a.getString("url"), temp, DownloadIdentity(job.etag, job.lastModified), a.optBoolean("allowHttp"), { isStopped }, { identity, total -> runBlocking { job = job.copy(etag = identity.etag, lastModified = identity.modified, total = total); progress(temp.length(), total, true) } }, { done, total -> runBlocking { progress(done, total) } })
     job = job.copy(args = JSONObject(job.args).put("_downloadComplete", true).toString()); progress(temp.length(), temp.length(), true)
    }
    if(a.optBoolean("hybrid") && !a.optBoolean("_hybridComplete")) { job = job.copy(args = JSONObject(job.args).put("_phase", "hybrid").toString()); progress(0, 0, true); val original = File(library.directory, ".${job.id}.original"); if(original.isFile) original.copyTo(temp, overwrite = true) else temp.copyTo(original); try { hybrid(temp) } catch(e: CancellationException) { throw e } catch(e: Exception) { original.copyTo(temp, overwrite = true); job = job.copy(error = "isohybrid: ${e.message}") }; job = job.copy(args = JSONObject(job.args).put("_hybridComplete", true).toString()); progress(temp.length(), temp.length(), true); }
    val hashes = calculateAllHashes(temp)
    val original = File(library.directory, ".${job.id}.original")
    if(original.isFile) {
     val backups = File(library.directory, "originals").apply { mkdirs() }
     val backup = File(backups, "${job.id}-${a.getString("name")}")
     if(!original.renameTo(backup)) { original.copyTo(backup, overwrite = true); original.delete() }
     job = job.copy(args = JSONObject(job.args).put("_original", backup.path).toString()); progress(job.progress, job.total, true)
    }
    ensureActive(); if(isStopped || dao.job(job.id)?.state in listOf("PAUSED", "CANCELLED")) throw CancellationException("Stopped")
    job = job.copy(args = JSONObject(job.args).put("_phase", "saving").toString()); progress(job.progress, job.total, true)
    val entry = library.commit(temp, a.getString("name"), a.optString("storage").ifBlank { null }); saveHashes(entry, hashes); job = job.copy(result = entry.id)
   }
   "CREATE" -> {
    val bytes = Math.multiplyExact(a.getLong("mib"), 1048576L); require(bytes >= 4194304 && bytes <= 1099511627776) { "Size outside 4 MiB–1 TiB" }
    val format = a.optString("filesystem").takeIf { it.isNotBlank() }?.let { ImageFilesystem.valueOf(it) }
    if(format != null) require(bytes / 1048576 in format.minMiB..format.maxMiB) { "Invalid size for ${format.label}" }
    require(library.directory.usableSpace >= if(a.optBoolean("allocate")) bytes else format?.minimumWorkingSpace ?: 1048576) { "Not enough storage" }
    RandomAccessFile(temp, "rw").use { out -> out.setLength(bytes); if(a.optBoolean("allocate")) { val zeros = ByteArray(1048576); var done = 0L; while(done < bytes) { ensureActive(); out.write(zeros, 0, minOf(zeros.size.toLong(), bytes - done).toInt()); done += minOf(zeros.size.toLong(), bytes - done); progress(done, bytes) }; out.fd.sync() } }
    if(a.has("filesystem")) {
     job = job.copy(args = JSONObject(job.args).put("_phase", "formatting").toString()); progress(0, 0, true)
     ImageFormatter.format(applicationContext, temp, library.directory, ImageFilesystem.valueOf(a.getString("filesystem")))
    } else if(a.optBoolean("fat")) FatFormatter.format(temp, bytes)
    if(a.optBoolean("testBoot")) TestBootImage.install(temp, applicationContext.assets.open("testboot/bios.bin").use { it.readBytes() }, applicationContext.assets.open("testboot/bootx64.efi").use { it.readBytes() })
    if(a.optBoolean("testCD")) { applicationContext.assets.open("testboot/test-cd.iso").use { source -> temp.outputStream().use { source.copyTo(it) } }; RandomAccessFile(temp, "rw").use { it.setLength(bytes) } }
    val hashes = calculateAllHashes(temp)
    ensureActive(); if(isStopped || dao.job(job.id)?.state in listOf("PAUSED", "CANCELLED")) throw CancellationException("Stopped")
    job = job.copy(args = JSONObject(job.args).put("_phase", "saving").toString()); progress(job.progress, job.total, true)
    val entry = library.commit(temp, a.getString("name"), a.optString("storage").ifBlank { null }); saveHashes(entry, hashes); job = job.copy(result = entry.id)
   }
   "IMPORT", "COPY_HOST", "MOVE" -> {
    val source = image?.let { localSource(it) } ?: library.input(a.getString("uri"))
    copy(source, temp.outputStream(), image?.size ?: a.optLong("size", -1))
    ensureActive(); if(isStopped || dao.job(job.id)?.state in listOf("PAUSED", "CANCELLED")) throw CancellationException("Stopped")
    job = job.copy(args = JSONObject(job.args).put("_phase", "saving").toString()); progress(job.progress, job.total, true)
    val entry = library.commit(temp, a.getString("name"), if(job.kind == "COPY_HOST") "default" else a.optString("storage").ifBlank { null })
    if(job.kind == "MOVE") { library.delete(image!!, true); dao.putImage(entry.copy(title = image.title)) }
    job = job.copy(result = entry.id)
   }
   "EXPORT" -> {
    val original = if(image == null) File(a.getString("uriSource")) else null
    if(original != null) require(original.canonicalFile.parentFile == File(library.directory, "originals").canonicalFile && original.isFile)
    copy(image?.let { localSource(it) } ?: original!!.inputStream(), library.output(a.getString("uri")), image?.size ?: original!!.length()); job = job.copy(result = a.getString("uri"))
   }
   "CHECKSUM" -> {
    val algorithm = a.getString("algorithm"); require(algorithm in listOf("SHA-256", "SHA-1", "MD5")); val digest = MessageDigest.getInstance(algorithm)
    localSource(image!!).use { input -> val buffer = ByteArray(1048576); var done = 0L; while(true) { ensureActive(); val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n); done += n; progress(done, image.size) } }
    job = job.copy(result = "$algorithm\n" + digest.digest().joinToString("") { "%02x".format(it) })
   }
   "RESIZE", "HYBRID" -> {
    copy(localSource(image!!), temp.outputStream(), image.size)
    val shrink = job.kind == "RESIZE" && a.getLong("mib") * 1048576 < image.size
    val backup = if(shrink || job.kind == "HYBRID" || image.location.startsWith("content:") || !File(image.location).canWrite()) backup(image) else null
    if(job.kind == "HYBRID") hybrid(temp) else { val bytes = Math.multiplyExact(a.getLong("mib"), 1048576L); require(bytes in 4194304..1099511627776); RandomAccessFile(temp, "rw").use { it.setLength(bytes); it.fd.sync() } }
    try { replace(image, temp) } catch(e: Exception) { if(backup != null) withContext(NonCancellable) { restoreBackup(image, backup) }; throw e }
    job = job.copy(result = if(backup != null) "Backup: ${backup.path}" else image.id)
   }
   else -> error("Unknown operation")
  }
 File(library.directory, ".${job.id}.original").delete()
 File(applicationContext.cacheDir, "${job.id}.source").delete()
 }
 private suspend fun calculateAllHashes(file: File): Map<String, String> {
  job = job.copy(args = JSONObject(job.args).put("_phase", "hashing").toString())
  progress(0, file.length(), true)
  val digests = listOf("SHA-256", "SHA-1", "MD5").associateWith { MessageDigest.getInstance(it) }
  file.inputStream().use { input ->
   val buffer = ByteArray(1048576); var done = 0L
   while(true) {
    ensureActive(); if(isStopped) throw InterruptedException("Stopped")
    val count = input.read(buffer); if(count < 0) break
    digests.values.forEach { it.update(buffer, 0, count) }; done += count; progress(done, file.length())
   }
  }
  progress(file.length(), file.length(), true)
  return digests.mapValues { (_, digest) -> digest.digest().joinToString("") { "%02x".format(it) } }
 }
 private suspend fun saveHashes(image: ImageEntry, hashes: Map<String, String>) {
  hashes.forEach { (algorithm, hash) -> dao.putJob(TransferJob("${job.id}:hash:$algorithm", "CHECKSUM", "$algorithm · ${image.title}", JSONObject().put("image", image.id).put("algorithm", algorithm).toString(), state = "DONE", result = "$algorithm\n$hash", createdAt = maxOf(System.currentTimeMillis(), image.modified))) }
 }
 private suspend fun backup(image: ImageEntry): File {
  val backup = File(library.directory, "${image.title.replace(Regex("[^\\p{L}0-9._-]"), "_")}-${job.id}.backup")
  require(backup.parentFile!!.usableSpace >= image.size) { "Not enough space for backup" }
  copy(localSource(image), backup.outputStream(), image.size)
  job = job.copy(result = "Backup: ${backup.path}"); dao.putJob(job)
  return backup
 }
 private fun restoreBackup(image: ImageEntry, backup: File) { if(image.location.startsWith("content:")) backup.inputStream().use { input -> library.output(image.location).use { input.copyTo(it) } } else if(File(image.location).canWrite()) backup.copyTo(File(image.location), overwrite = true) else RootShell.run("cp ${RootShell.quote(backup.path)} ${RootShell.quote(image.location)}", 600) }
 private suspend fun replace(image: ImageEntry, temp: File) {
  if(image.location.startsWith("content:")) copy(temp.inputStream(), library.output(image.location), temp.length())
  else if(File(image.location).canWrite()) { val target = File(image.location); if(!temp.renameTo(target)) { val sibling = File(target.parent, ".${job.id}.replacement"); copy(temp.inputStream(), sibling.outputStream(), temp.length()); check(sibling.renameTo(target)) { "Cannot replace image atomically" } } }
  else RootShell.run("cp ${RootShell.quote(temp.path)} ${RootShell.quote(image.location)}", 600)
  dao.putImage(image.copy(size = if(temp.exists()) temp.length() else File(image.location).length(), modified = System.currentTimeMillis(), allocatedSize = library.allocated(image.location, image.physicalPath))); temp.delete()
 }
 private suspend fun hybrid(file: File) {
  val executable = File(applicationContext.applicationInfo.nativeLibraryDir, "libisohybrid.so")
  check(executable.exists()) { "Bundled isohybrid unavailable" }
  val log = File(applicationContext.cacheDir, "${job.id}.hybrid-log")
  val process = ProcessBuilder(executable.path, file.path).redirectErrorStream(true).redirectOutput(log).start()
  try {
   withTimeout(120000) { while(process.isAlive) delay(100) }
   check(process.exitValue() == 0) { log.readText().trim().ifBlank { "isohybrid failed" }.takeLast(2048) }
  } finally { if(process.isAlive) process.destroyForcibly(); log.delete() }
 }

 private suspend fun ensureActive() { currentCoroutineContext().ensureActive() }
}
