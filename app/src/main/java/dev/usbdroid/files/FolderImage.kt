package dev.usbdroid.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.usbdroid.app
import dev.usbdroid.data.*
import dev.usbdroid.usb.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class FolderImageProgress(val bytes: Long = 0, val total: Long = 0, val file: String = "")
data class FolderEstimate(val fileBytes: Long, val files: Int, val reserveBytes: Long, val imageBytes: Long, val availableBytes: Long)
class FolderImage(private val context: Context) {
 companion object {
  fun imageBytes(total: Long, count: Int, filesystem: ImageFilesystem, extraMiB: Long): Long {
   require(total >= 0 && count >= 0 && extraMiB in 16..1048576 && filesystem != ImageFilesystem.NONE)
   val bytes = Math.addExact(Math.addExact(total, total / 4), Math.addExact(Math.multiplyExact(extraMiB, 1048576L), Math.multiplyExact(count.toLong(), 4096L)))
   val mib = maxOf(filesystem.minMiB, 128L, Math.addExact(bytes, 1048575) / 1048576)
   require(mib <= filesystem.maxMiB) { "Folder is too large" }
   return Math.multiplyExact(mib, 1048576L)
  }
 }
 private data class Source(val relative: String, val size: Long, val directory: Boolean, val file: DocumentFile?)
 suspend fun estimate(location: String, filesystem: ImageFilesystem, extraMiB: Long): FolderEstimate = withContext(Dispatchers.IO) {
  val (sources, _) = scan(location)
  val total = sources.filterNot { it.directory }.fold(0L) { sum, file -> Math.addExact(sum, file.size) }
  FolderLayoutPolicy.validate(sources.map { it.relative to if(it.directory) 0L else it.size }, filesystem)
  val imageBytes = imageBytes(total, sources.size, filesystem, extraMiB)
  FolderEstimate(total, sources.count { !it.directory }, imageBytes - total, imageBytes, android.os.StatFs(context.app.library.directory.path).availableBytes)
 }
 private fun scan(location: String): Pair<List<Source>, String?> {
  val sources: List<Source>
  val physical: String?
  if(location.startsWith("content:")) {
   physical = null
   val root = DocumentFile.fromTreeUri(context, Uri.parse(location)) ?: error("Folder unavailable")
   val result = mutableListOf<Source>()
   fun visit(parent: DocumentFile, relative: String) {
    require(relative.count { it == '/' } < 128) { "Folder nesting is too deep" }
    parent.listFiles().forEach { file ->
     val name = AccessPolicy.name(file.name ?: error("Unnamed file")); val path = listOf(relative, name).filter(String::isNotBlank).joinToString("/")
     result += Source(path, file.length(), file.isDirectory, file)
     require(result.size <= 100000) { "Too many files" }
     if(file.isDirectory) visit(file, path)
    }
   }
   visit(root, ""); sources = result
  } else {
   physical = RootShell.inMountNamespace("readlink -f ${RootShell.quote(location)}")
   require(physical.startsWith('/') && physical != "/")
   val listing = File.createTempFile("folder-list", ".bin", context.cacheDir)
   val scan = "for p do if [ -L \"\$p\" ]; then kind=link; size=0; elif [ -d \"\$p\" ]; then kind=dir; size=0; elif [ -f \"\$p\" ]; then kind=file; size=\$(stat -c %s \"\$p\") || exit 1; else kind=other; size=0; fi; printf '%s\\0%s\\0%s\\0' \"\$p\" \"\$kind\" \"\$size\"; done"
   val output = try {
    RootShell.inMountNamespace("set -e; test -d ${RootShell.quote(physical)}; find ${RootShell.quote(physical)} -mindepth 1 -exec /system/bin/sh -c ${RootShell.quote(scan)} sh {} + > ${RootShell.quote(listing.path)}", 120)
    require(listing.length() <= 32L * 1048576) { "Folder listing is too large" }; listing.readText()
   } finally { listing.delete() }
   val fields = output.split('\u0000').dropLast(1); require(fields.size % 3 == 0)
   require(fields.size / 3 <= 100000) { "Too many files" }
   sources = fields.chunked(3).map { (path, kind, size) ->
    require(kind in listOf("dir", "file")) { "Links and special files cannot be copied into the image" }
    val relative = path.removePrefix("$physical/"); AccessPolicy.relative(relative); relative.split('/').forEach(AccessPolicy::name)
    Source(relative, size.toLong(), kind == "dir", null)
   }
  }
  return sources to physical
 }
 suspend fun create(location: String, title: String, filesystem: ImageFilesystem, extraMiB: Long, progress: suspend (FolderImageProgress) -> Unit): ImageEntry = withContext(Dispatchers.IO) {
  require(filesystem != ImageFilesystem.NONE)
  require(extraMiB in 16..1048576)
  val (sources, physical) = scan(location)
  val total = sources.filterNot { it.directory }.fold(0L) { sum, file -> Math.addExact(sum, file.size) }
  FolderLayoutPolicy.validate(sources.map { it.relative to if(it.directory) 0L else it.size }, filesystem)
  val mib = imageBytes(total, sources.size, filesystem, extraMiB) / 1048576
  val library = context.app.library; val directory = library.directory
  require(android.os.StatFs(directory.path).availableBytes >= Math.multiplyExact(mib, 1048576L)) { "Not enough space for the temporary image" }
  val staging = File(directory, ".${UUID.randomUUID()}.partial")
  var mounted: LocalImage? = null
  try {
   RandomAccessFile(staging, "rw").use { it.setLength(mib * 1048576) }
   ImageFormatter.format(context, staging, directory, filesystem)
   mounted = try { context.app.imageAccess.mount(staging.path, title, false) } catch(e: Exception) {
    throw IllegalStateException(context.getString(dev.usbdroid.R.string.folder_format_mount_failed, filesystem.label), e)
   }
   var copied = 0L
   sources.filter { it.directory }.sortedBy { it.relative.count { c -> c == '/' } }.forEach { source ->
    coroutineContext.ensureActive(); val parent = source.relative.substringBeforeLast('/', ""); val name = source.relative.substringAfterLast('/')
    context.app.imageAccess.create(mounted!!.id, parent, name, true)
   }
   sources.filterNot { it.directory }.forEach { source ->
    coroutineContext.ensureActive(); progress(FolderImageProgress(copied, total, source.relative))
    val cache = if(physical == null) File.createTempFile("folder-import", ".tmp", context.cacheDir) else null
    try {
     if(cache != null) context.contentResolver.openInputStream(source.file!!.uri)!!.use { input -> cache.outputStream().use { val buffer = ByteArray(262144); while(true) { coroutineContext.ensureActive(); val count = input.read(buffer); if(count < 0) break; it.write(buffer, 0, count) } } }
     context.app.imageAccess.access(mounted!!.id, true) { entry ->
      val parent = context.app.imageAccess.path(entry, source.relative.substringBeforeLast('/', "")); val target = "$parent/${AccessPolicy.name(source.relative.substringAfterLast('/'))}"
      val from = cache?.canonicalPath ?: "$physical/${source.relative}"
      RootShell.inMountNamespace("set -e; test ! -L ${RootShell.quote(from)}; test \"\$(readlink -f ${RootShell.quote(from)})\" = ${RootShell.quote(from)}; cp -- ${RootShell.quote(from)} ${RootShell.quote(target)}", 600)
     }
    } finally { cache?.delete() }
    copied = Math.addExact(copied, source.size); progress(FolderImageProgress(copied, total, source.relative))
   }
   context.app.imageAccess.unmount(mounted!!.id); mounted = null
   val file = File(directory, "folder-${System.currentTimeMillis()}.img"); check(staging.renameTo(file))
   val entry = ImageEntry(UUID.randomUUID().toString(), title, file.path, file.canonicalPath, file.length(), file.lastModified(), "default", allocatedSize = library.allocated(file.path))
   context.app.database.dao().putImage(entry)
   val metadata = File(context.filesDir, "folder-image.tmp")
   metadata.writeText(org.json.JSONObject().put("image", entry.id).put("source", location).toString())
   check(metadata.renameTo(File(context.filesDir, "folder-image.json")))
   entry
  } catch(e: Exception) {
   val mount = mounted
   if(mount != null) withContext(NonCancellable) { runCatching { context.app.imageAccess.unmount(mount.id) }.onFailure { e.addSuppressed(it) } }
   // Keep the staging file if its mount could not be detached.
   if(mount == null || context.app.imageAccess.mounts.value.none { it.id == mount.id }) staging.delete()
   throw e
  }
 }
}
