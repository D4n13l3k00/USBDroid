package dev.usbdroid.files

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import dev.usbdroid.app
import kotlinx.coroutines.launch
import dev.usbdroid.usb.RootShell
import dev.usbdroid.usb.UsbController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class LocalImage(val id: String, val image: String, val title: String, val directory: String, val loop: String, val readOnly: Boolean, val offset: Long, val alias: String = "")
data class ImageFile(val relative: String, val name: String, val directory: Boolean, val size: Long, val modified: Long)

class ImageAccess(private val context: Context, private val usb: UsbController) {
 private val journal = File(context.filesDir, "local-mounts.json")
 private val _mounts = MutableStateFlow<List<LocalImage>>(emptyList())
 val mounts = _mounts.asStateFlow()
 val saveError = MutableStateFlow<String?>(null)
 private val activeEdits = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
 fun editIsActive(name: String) = name in activeEdits
 private val leases = mutableMapOf<String, Int>()
 private fun q(s: String) = RootShell.quote(s)
 private fun run(s: String, timeout: Long = 30) = RootShell.inMountNamespace(s, timeout)
 private fun boot() = run("cat /proc/sys/kernel/random/boot_id")
 private fun mounted(path: String): Boolean = RootShell.isMounted(path)
 private fun load(): List<LocalImage> {
  if(!journal.exists()) return emptyList()
  val state = JSONObject(journal.readText())
  if(state.getString("boot") != boot()) return emptyList()
  val list = state.getJSONArray("mounts")
  return (0 until list.length()).map { list.getJSONObject(it) }.map { LocalImage(it.getString("id"), it.getString("image"), it.getString("title"), it.getString("directory"), it.getString("loop"), it.getBoolean("readOnly"), it.optLong("offset"), it.optString("alias")) }
 }
 private fun save(values: List<LocalImage>) {
  val array = JSONArray()
  values.forEach { array.put(JSONObject().put("id", it.id).put("image", it.image).put("title", it.title).put("directory", it.directory).put("loop", it.loop).put("readOnly", it.readOnly).put("offset", it.offset).put("alias", it.alias)) }
  val temp = File(context.filesDir, "local-mounts.tmp")
  temp.writeText(JSONObject().put("boot", boot()).put("mounts", array).toString()); check(temp.renameTo(journal))
  _mounts.value = values.filter { mounted(it.directory) }
  context.contentResolver.notifyChange(DocumentsContract.buildRootsUri("${context.packageName}.images"), null)
 }
 suspend fun refresh() = usb.serialized { _mounts.value = load().filter { mounted(it.directory) } }
 suspend fun mount(image: String, title: String, readOnly: Boolean): LocalImage {
  val status = usb.inspect(); check(status.root) { status.error ?: "Root required" }
  return usb.serialized {
   val path = run("readlink -f ${q(image)}"); require(path.startsWith('/'))
   val entries = load(); entries.firstOrNull { it.image == path && mounted(it.directory) }?.let { return@serialized it }
   require(status.luns.none { it.file == path || it.file == image }) { "Eject the image from USB first" }
   val currentUsb = run("for p in /config/usb_gadget/*/functions/mass_storage.*/lun.*/file /sys/kernel/config/usb_gadget/*/functions/mass_storage.*/lun.*/file /sys/class/android_usb/android*/f_mass_storage/lun*/file; do [ -f \"\$p\" ] && cat \"\$p\"; done; true")
   require(currentUsb.lines().none { it == path || it == image }) { "Eject the image from USB first" }
   val header = File.createTempFile("image-header", ".bin", context.cacheDir)
   val offset = try { run("set -e; dd if=${q(path)} of=${q(header.path)} bs=512 count=34 2>/dev/null; chmod 644 ${q(header.path)}"); if(path.endsWith(".iso", true)) 0L else AccessPolicy.offset(header.readBytes(), run("stat -c %s ${q(path)}").toLong()) } finally { header.delete() }
   val id = UUID.randomUUID().toString(); val directory = "/data/local/tmp/usbdroid-images/$id"
   val loop = run("losetup -f"); require(loop.matches(Regex("/dev/(block/)?loop[0-9]+"))) { "No loop device available" }
   val ro = readOnly || path.endsWith(".iso", true)
   val entry = LocalImage(id, path, title, directory, loop, ro, offset, AccessPolicy.loopAlias(id))
   run("mkdir -p ${q(directory)}")
   save(entries + entry)
   try {
    run("set -e; mkdir -p /data/local/tmp/ud-loop; chmod 700 /data/local/tmp/ud-loop; test ! -e ${q(entry.alias)}; test ! -L ${q(entry.alias)}; touch ${q(entry.alias)}; mount --bind ${q(path)} ${q(entry.alias)}")
    run("losetup ${if(ro) "-r" else ""} -o $offset ${q(loop)} ${q(entry.alias)}")
    run("mount -o ${if(ro) "ro" else "rw"},nodev,nosuid,noexec ${q(loop)} ${q(directory)}")
    check(mounted(directory)) { "Filesystem was not mounted" }; save(entries + entry); entry
   } catch(e: Exception) {
    val cleanup = runCatching { if(mounted(directory)) run("umount ${q(directory)}"); if(ownsLoop(entry)) run("losetup -d ${q(loop)}"); removeAlias(entry) }
    if(cleanup.isSuccess) save(entries) else e.addSuppressed(cleanup.exceptionOrNull()!!)
    throw e
   }
  }
 }
 private fun ownsLoop(entry: LocalImage): Boolean {
  val backing = run("cat ${q("/sys/block/${File(entry.loop).name}/loop/backing_file")} 2>/dev/null || true")
  val description = run("losetup ${q(entry.loop)} 2>/dev/null || true")
  return backing == entry.image || "/$backing" == entry.image || (entry.alias.isNotBlank() && (backing == entry.alias || "/$backing" == entry.alias || description.contains(entry.alias)))
 }
 private fun removeAlias(entry: LocalImage) {
  if(entry.alias.isBlank()) return
  require(entry.alias == AccessPolicy.loopAlias(entry.id))
  if(mounted(entry.alias)) run("umount ${q(entry.alias)}")
  // Only the empty bind target or our own older symlink may be removed.
  run("set -e; if [ -L ${q(entry.alias)} ]; then test \"\$(readlink ${q(entry.alias)})\" = ${q(entry.image)}; rm -f ${q(entry.alias)}; elif [ -e ${q(entry.alias)} ]; then test -f ${q(entry.alias)}; test ! -s ${q(entry.alias)}; rm -f ${q(entry.alias)}; fi")
 }
 suspend fun unmount(id: String) = usb.serialized {
  val entries = load(); val entry = entries.firstOrNull { it.id == id } ?: error("Mount not found")
  val mtpJournal = File(context.filesDir, "folder-mtp.json")
  if(mtpJournal.exists()) {
   val share = JSONObject(mtpJournal.readText())
   if(share.optString("boot") == boot()) require(share.optString("folder") != entry.directory && !share.optString("folder").startsWith("${entry.directory}/")) { "Stop MTP sharing first" }
  }
  require((leases[id] ?: 0) == 0) { "Close files in the file manager first" }
  if(mounted(entry.directory)) run("set -e; sync; umount ${q(entry.directory)}")
  if(ownsLoop(entry)) run("losetup -d ${q(entry.loop)}"); removeAlias(entry); save(entries.filter { it.id != id })
 }
 /** DocumentsProvider is a synchronous Android boundary; all operations share USB's mutex. */
 fun <T> access(id: String, write: Boolean = false, block: (LocalImage) -> T): T = runBlocking { usb.serialized {
  val entry = load().firstOrNull { it.id == id } ?: error("Mount not found")
  check(mounted(entry.directory)) { "Image is not mounted" }; require(!write || !entry.readOnly) { "Image is read only" }; block(entry)
 } }
 fun path(entry: LocalImage, relative: String): String {
  AccessPolicy.relative(relative)
  val path = if(relative.isBlank()) entry.directory else "${entry.directory}/$relative"
  val canonical = run("readlink -f ${q(path)}")
  require(canonical == entry.directory || canonical.startsWith("${entry.directory}/")) { "Path is outside the image" }
  require(canonical == path) { "Symbolic links are not supported in the image browser" }
  return canonical
 }
 fun info(entry: LocalImage, relative: String): ImageFile {
  val p = path(entry, relative); val value = run("stat -c '%s %Y' ${q(p)}").split(' ')
  return ImageFile(relative, File(p).name, run("test -d ${q(p)} && echo yes || true") == "yes", value[0].toLong(), value[1].toLong() * 1000)
 }
 fun children(id: String, relative: String): List<ImageFile> = access(id) { entry ->
  val p = path(entry, relative)
  val listing = File.createTempFile("image-list", ".bin", context.cacheDir)
  val scan = "for p do [ -L \"\$p\" ] && continue; if [ -d \"\$p\" ]; then kind=dir; else kind=file; fi; meta=\$(stat -c '%s %Y' \"\$p\") || exit 1; printf '%s\\0%s\\0%s\\0' \"\$p\" \"\$kind\" \"\$meta\"; done"
  val output = try {
   run("set -e; find ${q(p)} -mindepth 1 -maxdepth 1 -exec /system/bin/sh -c ${q(scan)} sh {} + > ${q(listing.path)}")
   require(listing.length() <= 32L * 1048576) { "Directory listing is too large" }; listing.readText()
  } finally { listing.delete() }
  val fields = output.split('\u0000').dropLast(1); require(fields.size % 3 == 0)
  fields.chunked(3).map { (child, kind, metadata) ->
   val relativePath = child.removePrefix("${entry.directory}/"); AccessPolicy.relative(relativePath)
   val values = metadata.split(' ')
   ImageFile(relativePath, File(child).name, kind == "dir", values[0].toLong(), values[1].toLong() * 1000)
  }.sortedWith(compareByDescending<ImageFile> { it.directory }.thenBy { it.name.lowercase() })
 }
 fun create(id: String, parent: String, name: String, directory: Boolean): String = access(id, true) { entry ->
  val valid = AccessPolicy.name(name); val p = "${path(entry, parent)}/$valid"
  run("test ! -e ${q(p)} && test ! -L ${q(p)} && ${if(directory) "mkdir" else "touch"} ${q(p)}")
  listOf(parent, valid).filter { it.isNotBlank() }.joinToString("/")
 }
 fun rename(id: String, relative: String, name: String): String = access(id, true) { entry ->
  require((leases[id] ?: 0) == 0) { "Close files in the file manager first" }
  require(relative.isNotBlank()); val p = path(entry, relative); val dest = "${File(p).parent}/${AccessPolicy.name(name)}"
  run("test ! -e ${q(dest)} && test ! -L ${q(dest)} && mv ${q(p)} ${q(dest)}"); dest.removePrefix("${entry.directory}/")
 }
 fun delete(id: String, relative: String) = access(id, true) { entry ->
  require((leases[id] ?: 0) == 0) { "Close files in the file manager first" }
  require(relative.isNotBlank()); val p = path(entry, relative)
  // Never follow symlinks or recursively remove an arbitrary caller path.
  require(p.startsWith("${entry.directory}/")); run("rm -r -- ${q(p)}")
 }
 fun transfer(id: String, relatives: List<String>, parent: String, move: Boolean, progress: (Int, Int) -> Unit = { _, _ -> }) = access(id, true) { entry ->
  require(!move || (leases[id] ?: 0) == 0) { "Close files in the file manager first" }
  val destination = path(entry, parent)
  require(run("test -d ${q(destination)} && echo yes || true") == "yes") { "Destination is not a folder" }
  val sources = TransferPolicy.sources(relatives, parent)
  sources.forEach { relative ->
   val from = path(entry, relative); val target = "$destination/${AccessPolicy.name(File(from).name)}"
   require(run("test -e ${q(target)} || test -L ${q(target)}; echo $?").trim() == "1") { "A file with this name already exists" }
   require(run("find ${q(from)} -type l -print -quit").isBlank()) { "Symbolic links cannot be copied" }
  }
  val journalFile = File(context.filesDir, "file-transfer.json")
  check(!journalFile.exists()) { "Review the interrupted file operation first" }
  val record = JSONObject().put("mount", id).put("image", entry.image).put("parent", parent).put("move", move).put("boot", boot()).put("pending", JSONArray(sources)).put("completed", 0).put("total", sources.size)
  DurableJournal.write(journalFile, record.toString())
  sources.forEachIndexed { index, relative ->
   val from = path(entry, relative); val target = "$destination/${File(from).name}"
   if(move) run("set -e; test ! -e ${q(target)}; test ! -L ${q(target)}; mv -- ${q(from)} ${q(target)}; sync")
   else {
    val staging = "$destination/.usbdroid-${UUID.randomUUID()}"
    record.put("staging", staging); DurableJournal.write(journalFile, record.toString())
    // Keep interrupted copies for review; source files are never removed.
    run("set -e; cp -R -- ${q(from)} ${q(staging)}; sync; test ! -e ${q(target)}; test ! -L ${q(target)}; mv -- ${q(staging)} ${q(target)}; sync", 600)
   }
   record.put("pending", JSONArray(sources.drop(index + 1))).put("completed", index + 1).remove("staging")
   DurableJournal.write(journalFile, record.toString()); progress(index + 1, sources.size)
  }
  journalFile.delete()
 }
 fun copyIn(id: String, parent: String, name: String, source: File) = access(id, true) { entry ->
  val target = "${path(entry, parent)}/${AccessPolicy.name(name)}"
  require(run("test -e ${q(target)} && echo yes || true") != "yes") { "A file with this name already exists" }
  val staging = "${File(target).parent}/.usbdroid-${UUID.randomUUID()}"
  try { run("cp ${q(source.path)} ${q(staging)} && mv ${q(staging)} ${q(target)}") } finally { runCatching { run("rm -f ${q(staging)}") } }
 }
 fun copyOut(id: String, relative: String, target: File) = access(id) { entry -> run("cat ${q(path(entry, relative))} > ${q(target.path)}", 600) }
 fun readText(id: String, relative: String): Pair<String, String> = access(id) { entry ->
  val file = info(entry, relative); require(!file.directory && file.size <= 1048576) { "Text editor accepts files up to 1 MiB" }
  val temp = File.createTempFile("image-text", ".txt", context.cacheDir)
  try {
   val source = path(entry, relative); run("cat ${q(source)} > ${q(temp.path)}")
   val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
   decoder.decode(java.nio.ByteBuffer.wrap(temp.readBytes())).toString() to run("stat -c '%s:%y:%i' ${q(source)}")
  } finally { temp.delete() }
 }
 fun writeText(id: String, relative: String, text: String, signature: String) = access(id, true) { entry ->
  val target = path(entry, relative); require(run("stat -c '%s:%y:%i' ${q(target)}") == signature) { "File changed while editing" }
  val bytes = text.toByteArray(); require(bytes.size <= 1048576)
  val temp = File.createTempFile("image-text", ".txt", context.cacheDir); val staging = "${File(target).parent}/.usbdroid-${UUID.randomUUID()}"
  try { temp.writeBytes(bytes); run("cp -p ${q(target)} ${q(staging)} && cat ${q(temp.path)} > ${q(staging)} && sync && mv ${q(staging)} ${q(target)}") }
  finally { temp.delete(); runCatching { run("rm -f ${q(staging)}") } }
 }
 fun open(id: String, relative: String, mode: String): ParcelFileDescriptor = access(id, mode != "r") { entry ->
  val source = path(entry, relative); require(run("test -f ${q(source)} && echo yes || true") == "yes")
  val temp = File.createTempFile("image-document", ".cache", context.cacheDir)
  val signature = run("stat -c '%s:%y:%i' ${q(source)}")
  require(android.os.StatFs(context.cacheDir.path).availableBytes > run("stat -c %s ${q(source)}").toLong()) { "Not enough space to open this file" }
  try { run("cat ${q(source)} > ${q(temp.path)}") } catch(e: Exception) { temp.delete(); throw e }
  val editJournal = if(mode != "r") File(context.filesDir, "document-edit-${UUID.randomUUID()}.json") else null
  editJournal?.let { activeEdits.add(it.name); DurableJournal.write(it, JSONObject().put("image", entry.image).put("relative", relative).put("cache", temp.path).put("signature", signature).toString()) }
  leases[id] = (leases[id] ?: 0) + 1
  try {
   ParcelFileDescriptor.open(temp, ParcelFileDescriptor.parseMode(mode), Handler(Looper.getMainLooper())) { failure ->
    context.app.scope.launch {
     try {
      if(failure != null && mode != "r") throw failure
      if(failure == null && mode != "r") access(id, true) { live ->
       val target = path(live, relative); require(run("stat -c '%s:%y:%i' ${q(target)}") == signature) { "File changed while editing" }
       val staging = "${File(target).parent}/.usbdroid-${UUID.randomUUID()}"
       try { run("cp -p ${q(target)} ${q(staging)} && cat ${q(temp.path)} > ${q(staging)} && sync && mv ${q(staging)} ${q(target)}") } finally { runCatching { run("rm -f ${q(staging)}") } }
      }
     } catch(e: Exception) { editJournal?.let { activeEdits.remove(it.name) }; android.util.Log.e("USBDroid", "Document save failed; edited copy retained at ${temp.path}", e); saveError.value = "${e.message}. ${temp.path}"; return@launch }
     finally { editJournal?.let { activeEdits.remove(it.name) }; runBlocking { usb.serialized { leases[id] = ((leases[id] ?: 1) - 1).coerceAtLeast(0) } } }
     temp.delete(); editJournal?.delete()
    }
   }
  } catch(e: Exception) { leases[id] = ((leases[id] ?: 1) - 1).coerceAtLeast(0); temp.delete(); editJournal?.let { activeEdits.remove(it.name); it.delete() }; throw e }
 }
 fun restoreDocument(journalName: String) {
  require(journalName.matches(Regex("document-edit-[a-f0-9-]+\\.json")))
  val journalFile = File(context.filesDir, journalName); val record = JSONObject(journalFile.readText())
  val image = record.getString("image"); val mount = mounts.value.firstOrNull { it.image == image } ?: error("Open the image on the phone first")
  val cache = File(record.getString("cache")).canonicalFile
  require(cache.parentFile == context.cacheDir.canonicalFile && cache.name.startsWith("image-document") && cache.isFile)
  access(mount.id, true) { entry ->
   val target = path(entry, record.getString("relative"))
   require(run("stat -c '%s:%y:%i' ${q(target)}") == record.getString("signature")) { "Original file changed; saved copy retained" }
   val staged = "${File(target).parent}/.usbdroid-${UUID.randomUUID()}"
   try { run("set -e; cp -p ${q(target)} ${q(staged)}; cat ${q(cache.path)} > ${q(staged)}; sync; mv -- ${q(staged)} ${q(target)}; sync") }
   finally { runCatching { run("rm -f -- ${q(staged)}") } }
  }
  cache.delete(); journalFile.delete(); saveError.value = null
 }
 fun documentUri(id: String, relative: String = ""): Uri = DocumentsContract.buildDocumentUri("${context.packageName}.images", "$id:$relative")
}
