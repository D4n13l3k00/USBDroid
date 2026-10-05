package dev.usbdroid.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.usbdroid.app
import dev.usbdroid.data.ImageEntry
import dev.usbdroid.data.ImageLocks
import dev.usbdroid.usb.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

// A null hash denotes a missing target; directories cannot be replaced by files.
data class CopyBackItem(val relative: String, val directory: Boolean, val bytes: Long, val imageHash: String, val targetHash: String?, val blocked: Boolean = false)
data class CopyBackPlan(val image: ImageEntry, val source: String, val items: List<CopyBackItem>)
class FolderCopyBack(private val context: Context) {
 private fun q(value: String) = RootShell.quote(value)
 private fun run(value: String) = RootShell.inMountNamespace(value, 600)
 private fun digest(file: DocumentFile): String = context.contentResolver.openInputStream(file.uri)!!.use { input ->
  val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(262144)
  while(true) { val count = input.read(buffer); if(count < 0) break; digest.update(buffer, 0, count) }
  digest.digest().joinToString("") { "%02x".format(it) }
 }
 private inner class Destination(val source: String) {
  val tree = if(source.startsWith("content:")) DocumentFile.fromTreeUri(context, Uri.parse(source)) ?: error("Folder unavailable") else null
  val root = if(tree == null) run("set -e; test -d ${q(source)}; readlink -f ${q(source)}").also { require(it.startsWith('/') && it != "/") } else ""
  fun document(relative: String, create: Boolean = false): DocumentFile? {
   var current = tree ?: return null
   val segments = AccessPolicy.relative(relative).split('/').filter(String::isNotBlank)
   for(segment in segments) {
    AccessPolicy.name(segment)
    current = current.findFile(segment) ?: if(create) current.createDirectory(segment) ?: error("Cannot create folder") else return null
   }
   return current
  }
  fun path(relative: String): String {
   AccessPolicy.relative(relative)
   val result = if(relative.isBlank()) root else "$root/$relative"
   // Check every existing ancestor, including the leaf, before touching it.
   var current = root
   relative.split('/').filter(String::isNotBlank).forEach { name ->
    current += "/${AccessPolicy.name(name)}"
    require(run("test -L ${q(current)} && echo link || true") != "link") { "Symbolic links are not supported" }
   }
   require(result == root || result.startsWith("$root/")); return result
  }
  fun hash(relative: String): String? {
   if(tree != null) return document(relative)?.let { if(it.isDirectory) "DIRECTORY" else digest(it) }
   val target = path(relative)
   return run("if [ -d ${q(target)} ]; then echo DIRECTORY; elif [ -f ${q(target)} ]; then sha256sum < ${q(target)}; elif [ -e ${q(target)} ]; then echo SPECIAL; fi").takeIf(String::isNotBlank)?.substringBefore(' ')
  }
  private fun attributes(target: String, reference: String, preserveMode: Boolean = false) {
   val owner = run("stat -c '%u:%g' ${q(reference)}")
   require(owner.matches(Regex("[0-9]+:[0-9]+")))
   val label = run("ls -Zd ${q(reference)}").substringBefore(' ')
   val shared = root.startsWith("/storage/") || root.startsWith("/mnt/")
   val mode = if(preserveMode) run("stat -c %a ${q(reference)}") else null
   if(mode != null) require(mode.matches(Regex("[0-7]{3,4}")))
   run("chown ${q(owner)} ${q(target)}${if(shared) " || true" else ""}")
   if(label.contains(':')) run("chcon ${q(label)} ${q(target)}${if(shared) " || true" else ""}")
   if(mode != null) run("chmod ${q(mode)} ${q(target)}${if(shared) " || true" else ""}")
  }
  fun mkdir(relative: String) {
   if(tree != null) check(document(relative, true)?.isDirectory == true)
   else {
    var current = root
    AccessPolicy.relative(relative).split('/').filter(String::isNotBlank).forEach { name ->
     val parent = current; current += "/${AccessPolicy.name(name)}"; path(current.removePrefix("$root/"))
     if(run("test -d ${q(current)} && echo yes || true") != "yes") { run("mkdir -- ${q(current)}"); attributes(current, parent) }
    }
   }
  }
  fun write(relative: String, cache: File) {
   val parent = relative.substringBeforeLast('/', ""); val name = AccessPolicy.name(relative.substringAfterLast('/'))
   mkdir(parent)
   if(tree == null) {
    val target = path(relative); val staged = "$root/${parent.takeIf(String::isNotBlank)?.plus('/') ?: ""}.usbdroid-${UUID.randomUUID()}"
    try {
     run("set -e; cp -- ${q(cache.path)} ${q(staged)}; sync")
     val reference = if(run("test -f ${q(target)} && echo yes || true") == "yes") target else File(target).parent!!
     attributes(staged, reference, reference == target)
     run("set -e; mv -f -- ${q(staged)} ${q(target)}; sync")
    }
    finally { runCatching { run("rm -f -- ${q(staged)}") } }
   } else {
    val folder = document(parent, true)!!
    val staged = folder.createFile("application/octet-stream", ".usbdroid-${UUID.randomUUID()}") ?: error("Cannot create file")
    var previous: DocumentFile? = null; var backupName: String? = null
    try {
     context.contentResolver.openOutputStream(staged.uri, "wt")!!.use { output -> cache.inputStream().use { it.copyTo(output) } }
     check(digest(staged) == cache.inputStream().use { input -> val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(262144); while(true) { val n = input.read(buffer); if(n < 0) break; hash.update(buffer, 0, n) }; hash.digest().joinToString("") { "%02x".format(it) } }) { "Copied file failed verification" }
     previous = folder.findFile(name)
     if(previous != null) { backupName = ".usbdroid-backup-${UUID.randomUUID()}"; check(previous.renameTo(backupName)) { "Provider cannot safely replace files" } }
     if(!staged.renameTo(name)) { if(previous != null) check(previous.renameTo(name)) { "Original file retained as $backupName" }; error("Provider cannot rename files") }
     previous?.delete()
    } finally { if(staged.name != name) staged.delete() }
   }
  }
 }
 suspend fun preview(image: ImageEntry): CopyBackPlan = withContext(Dispatchers.IO) { ImageLocks.use(image.id) {
  context.app.library.assertDetached(image)
  val metadata = JSONObject(File(context.filesDir, "folder-image.json").readText())
  require(metadata.getString("image") == image.id)
  val source = metadata.getString("source"); val destination = Destination(source)
  val mount = context.app.imageAccess.mount(image.physicalPath!!, image.title, true)
  try {
   val items = mutableListOf<CopyBackItem>()
   fun visit(parent: String, depth: Int) {
    require(depth < 128 && items.size <= 100000)
    context.app.imageAccess.children(mount.id, parent).forEach { file ->
     val existing = destination.hash(file.relative)
     if(file.directory) {
      items += CopyBackItem(file.relative, true, 0, "DIRECTORY", existing, existing != null && existing != "DIRECTORY")
      if(existing == null || existing == "DIRECTORY") visit(file.relative, depth + 1)
     } else {
      val hash = context.app.imageAccess.access(mount.id) { entry -> run("sha256sum < ${q(context.app.imageAccess.path(entry, file.relative))}").substringBefore(' ') }
      if(hash != existing) items += CopyBackItem(file.relative, false, file.size, hash, existing, existing in listOf("DIRECTORY", "SPECIAL"))
     }
    }
   }
   visit("", 0); CopyBackPlan(image, source, items)
  } finally { withContext(kotlinx.coroutines.NonCancellable) { context.app.imageAccess.unmount(mount.id) } }
 } }
 suspend fun apply(plan: CopyBackPlan, replace: Boolean, progress: suspend (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) { ImageLocks.use(plan.image.id) {
  context.app.library.assertDetached(plan.image)
  val destination = Destination(plan.source)
  val selected = plan.items.filter { !it.blocked && (it.directory || it.targetHash == null || replace) }
  // Recheck the complete preview before changing any destination.
  selected.forEach { require(destination.hash(it.relative) == it.targetHash) { "Folder changed since preview; check it again" } }
  val journal = File(context.filesDir, "folder-copyback.json")
  check(!journal.exists()) { "Review the interrupted file operation first" }
  val mount = context.app.imageAccess.mount(plan.image.physicalPath!!, plan.image.title, true)
  val record = JSONObject().put("image", plan.image.id).put("source", plan.source).put("completed", 0).put("total", selected.size)
  try {
   selected.filterNot { it.directory }.forEach { item ->
    val hash = context.app.imageAccess.access(mount.id) { entry -> run("sha256sum < ${q(context.app.imageAccess.path(entry, item.relative))}").substringBefore(' ') }
    require(hash == item.imageHash) { "Image changed since preview; check it again" }
   }
   DurableJournal.write(journal, record.toString())
   val totalBytes = selected.sumOf { it.bytes }; var copiedBytes = 0L
   selected.forEachIndexed { index, item ->
    record.put("file", item.relative); DurableJournal.write(journal, record.toString())
    require(destination.hash(item.relative) == item.targetHash) { "Folder changed since preview; check it again" }
    if(item.directory) destination.mkdir(item.relative) else {
     require(android.os.StatFs(context.cacheDir.path).availableBytes > item.bytes) { "Not enough space to copy this file" }
     val cache = File.createTempFile("copyback", ".tmp", context.cacheDir)
     try { context.app.imageAccess.copyOut(mount.id, item.relative, cache); require(run("sha256sum < ${q(cache.path)}").substringBefore(' ') == item.imageHash) { "Image changed during copying; check it again" }; require(destination.hash(item.relative) == item.targetHash) { "Folder changed during copying; check it again" }; destination.write(item.relative, cache) } finally { cache.delete() }
    }
    record.put("completed", index + 1); DurableJournal.write(journal, record.toString())
    copiedBytes += item.bytes; progress(copiedBytes, totalBytes)
   }
   journal.delete()
  } finally { withContext(kotlinx.coroutines.NonCancellable) { context.app.imageAccess.unmount(mount.id) } }
 } }
}
