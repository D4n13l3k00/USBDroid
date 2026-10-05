package dev.usbdroid.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import dev.usbdroid.usb.RootShell
import dev.usbdroid.usb.UsbController
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class Library(val context: Context, val dao: AppDao, private val usb: UsbController) {
 val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "images").apply { mkdirs() }
 fun allocated(location: String, physicalPath: String? = null): Long? = runCatching {
  val path = physicalPath ?: location.takeIf { it.startsWith("/") } ?: if(location.startsWith("file:")) Uri.parse(location).path else null
  val direct = path?.let { runCatching { android.system.Os.stat(it) }.getOrNull() }
  val stat = direct ?: if(location.startsWith("content:") || location.startsWith("file:")) context.contentResolver.openFileDescriptor(Uri.parse(location), "r")?.use { android.system.Os.fstat(it.fileDescriptor) } else null
  stat?.takeIf { android.system.OsConstants.S_ISREG(it.st_mode) }?.let { Math.multiplyExact(it.st_blocks, 512L) }
 }.getOrNull()
 suspend fun initialize() {
  if(dao.allStorage().isEmpty()) dao.putStorage(StorageLocation("default", "USBDroid", directory.path, "FILE", true))
  if(dao.allRepositories().isEmpty()) {
   dao.putRepository(CatalogRepository("main", "Linux", "https://repositories.drivedroid.io/main.json"))
   dao.putRepository(CatalogRepository("syslinux", "Syslinux", "https://repositories.drivedroid.io/syslinux.json"))
  }
  val oldDefaultTitles = mapOf("main" to "DriveDroid main", "syslinux" to "DriveDroid syslinux")
  dao.allRepositories().filter { it.title == oldDefaultTitles[it.id] }.forEach { repo -> dao.putRepository(repo.copy(title = if(repo.id == "main") "Linux" else "Syslinux")) }
 }
 suspend fun scan() {
  val previous = dao.allImages().associateBy { it.location }
  for(storage in dao.allStorage()) {
   val files = when(storage.kind) {
    "SAF" -> DocumentFile.fromTreeUri(context, Uri.parse(storage.location))?.listFiles()?.filter { it.isFile }?.map { ImageEntry(it.uri.toString(), it.name.orEmpty().substringBeforeLast('.'), it.uri.toString(), physical(it.uri), it.length(), it.lastModified(), storage.id) }.orEmpty()
    "ROOT" -> runCatching { RootShell.run("find ${RootShell.quote(storage.location)} -maxdepth 1 -type f -print") }.getOrDefault("").lines().filter { it.startsWith("/") }.map { p -> val info = RootShell.run("stat -c '%s %Y %b' ${RootShell.quote(p)}").split(' '); ImageEntry(p, File(p).nameWithoutExtension, p, p, info[0].toLong(), info[1].toLong() * 1000, storage.id, allocatedSize = info[2].toLong() * 512) }
    else -> File(storage.location).listFiles()?.filter { it.isFile }?.map { ImageEntry(it.path, it.nameWithoutExtension, it.path, it.canonicalPath, it.length(), it.lastModified(), storage.id) }.orEmpty()
   }
   for(entry in files.filter { extension(it.location) in listOf("iso", "img") }) dao.putImage(entry.copy(id = previous[entry.location]?.id ?: entry.id, title = previous[entry.location]?.title ?: entry.title, hidden = previous[entry.location]?.hidden ?: false, allocatedSize = entry.allocatedSize ?: allocated(entry.location, entry.physicalPath)))
  }
  for(entry in previous.values.filter { it.storageId == "external" }) dao.imageAllocation(entry.id, allocated(entry.location, entry.physicalPath))
 }
 fun extension(location: String): String = if(location.startsWith("content:")) DocumentFile.fromSingleUri(context, Uri.parse(location))?.name.orEmpty().substringAfterLast('.').lowercase() else File(location).extension.lowercase()
 fun name(location: String): String = if(location.startsWith("content:")) DocumentFile.fromSingleUri(context, Uri.parse(location))?.name ?: "image.img" else File(location).name
 fun physical(uri: Uri): String? {
  if(uri.scheme == "file") return uri.path
  // ExternalStorageProvider document IDs have a stable primary-volume path. Never map arbitrary provider IDs.
  if(uri.authority != "com.android.externalstorage.documents") return null
  val id = runCatching { android.provider.DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
  if(!id.startsWith("primary:")) return null
  val relative = id.removePrefix("primary:")
  if(relative.split('/').any { it == ".." }) return null
  return File("/storage/emulated/0", relative).path
 }
 fun input(location: String): InputStream = if(location.startsWith("content:") || location.startsWith("file:")) context.contentResolver.openInputStream(Uri.parse(location)) ?: error("Cannot open source") else File(location).inputStream()
 fun output(location: String): OutputStream = if(location.startsWith("content:") || location.startsWith("file:")) context.contentResolver.openOutputStream(Uri.parse(location), "wt") ?: error("Cannot open destination") else File(location).outputStream()
 suspend fun addUri(uri: Uri): ImageEntry {
  val doc = DocumentFile.fromSingleUri(context, uri) ?: error("Cannot open document")
  require(doc.isFile && extension(uri.toString()) in listOf("iso", "img")) { "ISO / IMG required" }
  val entry = ImageEntry(UUID.randomUUID().toString(), doc.name.orEmpty().substringBeforeLast('.'), uri.toString(), physical(uri), doc.length(), doc.lastModified(), "external")
  val measured = entry.copy(allocatedSize = allocated(entry.location, entry.physicalPath)); dao.putImage(measured); return measured
 }
 suspend fun addPath(path: String) {
  require(path.startsWith("/") && !path.contains('\n'))
  require(File(path).extension.lowercase() in listOf("iso", "img"))
  val info = RootShell.run("stat -c '%s %Y %b' ${RootShell.quote(path)}").split(' ')
  dao.putImage(ImageEntry(path, File(path).nameWithoutExtension, path, path, info[0].toLong(), info[1].toLong() * 1000, "external", allocatedSize = info[2].toLong() * 512))
 }
 suspend fun primary(): StorageLocation = dao.allStorage().firstOrNull { it.primary } ?: dao.allStorage().first()
 suspend fun addStorage(uri: Uri) { val doc = DocumentFile.fromTreeUri(context, uri) ?: error("Cannot open directory"); dao.putStorage(StorageLocation(uri.toString(), doc.name ?: "Documents", uri.toString(), "SAF")); scan() }
 suspend fun addRootStorage(path: String) { require(path.startsWith("/") && !path.contains('\n')); RootShell.run("test -d ${RootShell.quote(path)}"); dao.putStorage(StorageLocation(path, File(path).name, path, "ROOT")); scan() }
 suspend fun writableLocal(name: String): File {
  validateName(name)
  // Temporary processing always uses local storage; commit supports all configured destinations.
  return File(directory, name).also { require(!it.exists()) { "File already exists" } }
 }
 suspend fun copyFilename(filename: String, storageId: String? = null): String {
  validateName(filename)
  val storage = dao.allStorage().find { it.id == storageId } ?: primary()
  val names = when(storage.kind) {
   "SAF" -> DocumentFile.fromTreeUri(context, Uri.parse(storage.location))?.listFiles()?.mapNotNull { it.name } ?: error("Directory permission revoked")
   "ROOT" -> RootShell.run("find ${RootShell.quote(storage.location)} -maxdepth 1 -type f -print").lines().map { File(it).name }
   else -> File(storage.location).list()?.toList().orEmpty()
  }.toMutableSet()
  dao.pendingJobs().forEach { job -> runCatching { org.json.JSONObject(job.args).optString("name") }.getOrNull()?.let(names::add) }
  val normalized = names.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
  val source = File(filename)
  var number = 2
  while(number < 100000) { val candidate = "${source.nameWithoutExtension} ($number).${source.extension}"; if(candidate.lowercase(java.util.Locale.ROOT) !in normalized) return candidate; number++ }
  error("Too many copies")
 }
 suspend fun commit(tmp: File, filename: String, storageId: String? = null): ImageEntry {
  validateName(filename)
  val storage = dao.allStorage().find { it.id == storageId } ?: primary()
  val location: String
  when(storage.kind) {
   "SAF" -> {
    val tree = DocumentFile.fromTreeUri(context, Uri.parse(storage.location)) ?: error("Directory permission revoked")
    require(tree.findFile(filename) == null) { "File already exists" }
    val partial = tree.createFile("application/octet-stream", ".$filename.partial") ?: error("Cannot create document")
    try { output(partial.uri.toString()).use { out -> tmp.inputStream().use { it.copyTo(out) } }; check(partial.renameTo(filename)); location = partial.uri.toString() }
    catch(e: Exception) { partial.delete(); throw e }
    tmp.delete()
   }
   "ROOT" -> {
    location = File(storage.location, filename).path
    val partial = "$location.usbdroid-partial"
    RootShell.run("set -e; test ! -e ${RootShell.quote(location)}; test ! -e ${RootShell.quote(partial)}; cp ${RootShell.quote(tmp.path)} ${RootShell.quote(partial)}; mv ${RootShell.quote(partial)} ${RootShell.quote(location)}", 600)
    tmp.delete()
   }
   else -> {
    val target = File(storage.location, filename); require(!target.exists()) { "File already exists" }
    if(!tmp.renameTo(target)) { val partial = File(target.parent, ".${UUID.randomUUID()}.partial"); try { tmp.copyTo(partial); check(partial.renameTo(target)); tmp.delete() } finally { partial.delete() } }
    location = target.path
   }
  }
  val entry = ImageEntry(UUID.randomUUID().toString(), filename.substringBeforeLast('.'), location, if(location.startsWith("content:")) physical(Uri.parse(location)) else location, if(location.startsWith("content:")) DocumentFile.fromSingleUri(context, Uri.parse(location))!!.length() else if(storage.kind == "ROOT") RootShell.run("stat -c '%s' ${RootShell.quote(location)}").toLong() else File(location).length(), System.currentTimeMillis(), storage.id)
  val measured = entry.copy(allocatedSize = allocated(entry.location, entry.physicalPath)); dao.putImage(measured); return measured
 }
 suspend fun assertDetached(image: ImageEntry) {
  val status = usb.inspect()
  if(image.physicalPath != null) {
   usb.assertLocalDetached(image.physicalPath)
   check(status.root) { status.error ?: "Root required to verify USB state" }
   val canonical = RootShell.run("readlink -f ${RootShell.quote(image.physicalPath)}")
   check(status.luns.none { it.file == canonical || it.file == image.physicalPath }) { "Eject the image first" }
  }
 }
 suspend fun delete(image: ImageEntry, file: Boolean) {
  require(!image.isMtp) { "MTP entries must be removed without deleting the folder" }
  assertDetached(image)
  if(file) {
   if(image.location.startsWith("content:")) check(DocumentFile.fromSingleUri(context, Uri.parse(image.location))?.delete() == true) { "Cannot delete document" }
   else {
    val path = if(image.location.startsWith("file:")) Uri.parse(image.location).path ?: error("Invalid file URI") else image.location
    if(File(path).canWrite()) check(File(path).delete()) { "Cannot delete file" } else RootShell.run("rm -- ${RootShell.quote(path)}")
   }
   dao.deleteImage(image.id)
  }
  else dao.putImage(image.copy(hidden = true))
 }
 companion object {
  fun validateName(name: String) { require(name.isNotBlank() && name == File(name).name && name.none { it == '\u0000' || it == '/' || it == '\\' || it == '\n' } && File(name).extension.lowercase() in listOf("iso", "img")) { "Invalid ISO / IMG filename" } }
 }
}
