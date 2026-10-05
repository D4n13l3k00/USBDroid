package dev.usbdroid
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import androidx.room.withTransaction
import dev.usbdroid.data.*
import dev.usbdroid.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class InterruptedOperation(val journal: String, val imageId: String?, val file: String, val completed: Int, val total: Int)
data class UndoRemoval(val token: String, val ids: List<String>)
data class OperationNotice(val token: String, val title: String)
data class AppState(val images: List<ImageEntry> = emptyList(), val preferences: Preferences = Preferences(), val usb: UsbStatus = UsbStatus(), val catalog: List<Release> = emptyList(), val jobs: List<TransferJob> = emptyList(), val storage: List<StorageLocation> = emptyList(), val repositories: List<CatalogRepository> = emptyList(), val operations: Set<String> = emptySet(), val message: String? = null, val settingsLoaded: Boolean = false, val rootChecked: Boolean = false, val rootBusy: Boolean = false, val messageIsError: Boolean = false, val undoRemoval: UndoRemoval? = null, val notice: OperationNotice? = null, val duplicateDownload: Release? = null, val diagnostics: String = "", val localImages: List<dev.usbdroid.files.LocalImage> = emptyList(), val folderShare: dev.usbdroid.files.FolderShare? = null, val temporaryImageId: String? = null, val interrupted: List<InterruptedOperation> = emptyList())
class AppViewModel(application: Application): AndroidViewModel(application) {
 private val app = application as USBDroidApplication
 val usb get() = app.usb
 private val dao = app.database.dao()
 private val _state = MutableStateFlow(AppState())
 val state = _state.asStateFlow()
 private val sortMutex = Mutex()
 private val downloadNameMutex = Mutex()
 private var pendingSortWrites = 0
 init {
  viewModelScope.launch { app.imageAccess.mounts.collect { values -> _state.update { it.copy(localImages = values) } } }
  viewModelScope.launch { app.folderUsb.state.collect { value -> _state.update { it.copy(folderShare = value) } } }
  viewModelScope.launch { app.imageAccess.saveError.collect { value -> if(value != null) { notifyError(value); refreshInterrupted() } } }

  viewModelScope.launch { usb.changes.collect { value -> _state.update { it.copy(usb = value) } } }
  viewModelScope.launch { dao.images().collect { value -> _state.update { it.copy(images = value) } } }
  viewModelScope.launch { dao.jobs().collect { value ->
   _state.update { current ->
   val completed = value.firstOrNull { job -> job.kind != "CHECKSUM" && job.state == "DONE" && current.jobs.any { it.id == job.id && it.running } }
   current.copy(jobs = value, notice = completed?.let { OperationNotice(UUID.randomUUID().toString(), it.title) } ?: current.notice)
  }; refreshInterrupted() } }
  viewModelScope.launch { dao.storage().collect { value -> _state.update { it.copy(storage = value) } } }
  viewModelScope.launch { dao.repositories().collect { value -> _state.update { it.copy(repositories = value) } } }
  viewModelScope.launch { app.settings.flow.collect { value -> _state.update { current -> val p = current.preferences; val shown = if(pendingSortWrites == 0) value else value.copy(imageSort = p.imageSort, imageDescending = p.imageDescending, downloadSort = p.downloadSort, downloadDescending = p.downloadDescending, jobSort = p.jobSort, jobDescending = p.jobDescending); current.copy(preferences = shown, settingsLoaded = true) }; if(value.welcomeComplete && !_state.value.rootChecked && !_state.value.rootBusy) inspect() } }
 }
 fun clearMessage() { _state.update { it.copy(message = null) } }
 fun notify(message: String) { _state.update { it.copy(message = message, messageIsError = false) } }
 fun notifyError(message: String) { _state.update { it.copy(message = message, messageIsError = true) } }
 fun clearNotice(token: String) { _state.update { if(it.notice?.token == token) it.copy(notice = null) else it } }
 fun clearUndo(token: String) { _state.update { if(it.undoRemoval?.token == token) it.copy(undoRemoval = null) else it } }
 fun clearDuplicate() { _state.update { it.copy(duplicateDownload = null) } }
 fun favorite(ids: Set<String>, enabled: Boolean) = launch("favorite") { app.settings.favorites(ids, enabled) }
 fun undoRemoval(token: String) = launch("undo") {
  val undo = _state.value.undoRemoval?.takeIf { it.token == token } ?: return@launch
  dao.allImages().filter { it.id in undo.ids && it.hidden }.forEach { dao.putImage(it.copy(hidden = false)) }
  clearUndo(token)
 }
 fun hideImages(images: List<ImageEntry>, finished: () -> Unit = {}) = launch("bulk-hide", finished) {
  require(images.isNotEmpty())
  images.forEach { require(!_state.value.imageWorking(it.id)); app.library.assertDetached(it) }
  daoWithHidden(images)
  _state.update { it.copy(undoRemoval = UndoRemoval(UUID.randomUUID().toString(), images.map(ImageEntry::id))) }
 }
 fun deleteImages(images: List<ImageEntry>, finished: () -> Unit = {}) = launch("bulk-delete", finished) {
  require(images.isNotEmpty())
  var deleted = 0
  try {
   ImageLocks.useAll(images.map { it.id }) {
    val current = images.map { dao.image(it.id) ?: error("Image no longer exists") }
    current.forEach { image -> require(_state.value.jobs.none { it.running && runCatching { JSONObject(it.args).optString("image") == image.id }.getOrDefault(false) }); app.library.assertDetached(image) }
    current.forEach { app.library.delete(it, true); deleted++ }
   }
   app.settings.favorites(images.map { it.id }.toSet(), false)
   _state.update { it.copy(notice = OperationNotice(UUID.randomUUID().toString(), app.getString(R.string.files_deleted, deleted))) }
  } catch(e: CancellationException) { throw e } catch(e: Exception) { throw IllegalStateException(app.getString(R.string.files_delete_failed, deleted, images.size) + "\n" + e.message, e) }
 }
 private suspend fun daoWithHidden(images: List<ImageEntry>) { app.database.withTransaction {
  images.forEach { image -> dao.image(image.id)?.let { dao.putImage(it.copy(hidden = true)) } }
 } }
 fun ejectImages(images: List<ImageEntry>, finished: () -> Unit = {}) = launch("eject-all", finished) {
  val paths = images.mapNotNull { it.physicalPath }.toSet()
  usb.inspect().luns.filter { it.file in paths }.forEach { usb.eject(it) }
  _state.update { it.copy(usb = usb.inspect()) }; if(!usb.hasSessions()) app.stopService(Intent(app, HostingService::class.java))
 }
 private fun launch(key: String = "", finished: () -> Unit = {}, block: suspend () -> Unit) {
  synchronized(_state) {
   if(key.isNotBlank() && (_state.value.working(key) || key == "eject-all" && _state.value.usbWorking() || (key.startsWith("host:") || key.startsWith("eject:")) && _state.value.usbWorking(if(key.startsWith("host:")) key.substringAfter(':').substringBeforeLast(':') else key.substringAfter(':')))) return
   if(key.isNotBlank()) _state.update { it.copy(operations = it.operations + key) }
  }
  viewModelScope.launch {
   try { withContext(Dispatchers.IO) { block() }; finished() }
   catch(e: CancellationException) { throw e }
   catch(e: Exception) { notifyError(e.message ?: "Operation failed"); refreshInterrupted() }
   finally { if(key.isNotBlank()) _state.update { it.copy(operations = it.operations - key) } }
  }
 }
 fun refresh() = launch("refresh") { app.library.scan(); usb.inspect(); refreshAccessNow() }
 private suspend fun refreshAccessNow() {
  app.imageAccess.refresh(); app.folderUsb.refresh(); refreshInterrupted()
  val temporary = File(app.filesDir, "folder-image.json")
  _state.update { it.copy(temporaryImageId = if(temporary.exists()) runCatching { JSONObject(temporary.readText()).getString("image") }.getOrNull() else null) }
 }
 fun refreshAccess() = launch("access-refresh") { refreshAccessNow() }
 fun mountLocal(image: ImageEntry, readOnly: Boolean) = launch("local-mount:${image.id}") { ImageLocks.use(image.id) {
  require(dao.image(image.id) != null) { "Image no longer exists" }
  app.imageAccess.mount(image.physicalPath ?: error(app.getString(R.string.copy_required)), image.title, readOnly)
 } }
 fun unmountLocal(id: String) = launch("local-unmount:$id") {
  val mounted = app.imageAccess.mounts.value.firstOrNull { it.id == id }
  require(!_state.value.working("image-file:$id")) { "Wait for the file operation to finish" }
  app.imageAccess.unmount(id)
  if(mounted != null) {
   dao.jobs().first().filter { it.kind == "CHECKSUM" && JSONObject(it.args).optString("image") == _state.value.images.firstOrNull { image -> image.physicalPath == mounted.image }?.id }.forEach { dao.deleteJob(it.id) }
   app.library.scan()
  }
 }
 fun addMtpFolder(path: String, title: String, readOnly: Boolean, finished: () -> Unit) = launch("folder-create", finished) {
  require(title.isNotBlank())
  val canonical = dev.usbdroid.usb.RootShell.inMountNamespace("set -e; test -d ${dev.usbdroid.usb.RootShell.quote(path)}; readlink -f ${dev.usbdroid.usb.RootShell.quote(path)}")
  require(canonical.startsWith('/') && canonical != "/")
  val previous = dao.allImages().firstOrNull { it.isMtp && it.physicalPath == canonical }
  dao.putImage(ImageEntry(previous?.id ?: java.util.UUID.randomUUID().toString(), title.trim(), "mtp:$canonical", canonical, 0, System.currentTimeMillis(), "mtp", kind = "MTP", mtpReadOnly = readOnly))
 }
 fun removeMtpFolder(image: ImageEntry, finished: () -> Unit) = launch("remove:${image.id}", finished) {
  require(image.isMtp)
  check(app.folderUsb.state.value?.folder != image.physicalPath) { "Disconnect the folder first" }
  dao.deleteImage(image.id)
 }
 fun startFolderMtp(path: String, readOnly: Boolean, entry: ImageEntry? = null) = launch("folder-mtp") {
  app.folderUsb.start(path, readOnly)
  try { app.startForegroundService(Intent(app, dev.usbdroid.files.FolderSharingService::class.java)) } catch(e: Exception) { app.folderUsb.stop(); throw e }
  if(entry != null) dao.image(entry.id)?.takeIf { it.isMtp && it.physicalPath == path }?.let { dao.putImage(it.copy(mtpReadOnly = readOnly)) }
 }
 fun stopFolderMtp() = launch("folder-mtp-stop") { app.folderUsb.stop(); app.stopService(Intent(app, dev.usbdroid.files.FolderSharingService::class.java)); _state.update { it.copy(usb = usb.inspect()) } }
 fun folderImage(source: String, title: String, filesystem: String, extraMiB: Long) = submit("FOLDER_IMAGE", title, JSONObject().put("source", source).put("filesystem", filesystem).put("extraMiB", extraMiB), { refreshAccess() })
 fun keepFolderImage() = launch("folder-keep") { File(app.filesDir, "folder-image.json").delete(); _state.update { it.copy(temporaryImageId = null) } }
 fun discardFolderImage(image: ImageEntry, finished: () -> Unit = {}) = launch("folder-discard", finished) {
  ImageLocks.use(image.id) { app.library.delete(image, true) }
  File(app.filesDir, "folder-image.json").delete(); _state.update { it.copy(temporaryImageId = null) }
 }
 private fun refreshInterrupted() {
  val records = (listOf("file-transfer.json", "folder-copyback.json") + app.filesDir.listFiles().orEmpty().filter { it.name.startsWith("document-edit-") && it.name.endsWith(".json") }.map { it.name }).mapNotNull { name ->
   val file = File(app.filesDir, name)
   if(!file.exists() || app.imageAccess.editIsActive(name)) null else runCatching {
    val json = JSONObject(file.readText()); val image = json.optString("image")
    val active = _state.value.jobs.any { it.running && (name == "file-transfer.json" && it.operationKey() == "image-file:${json.optString("mount")}" || name == "folder-copyback.json" && JSONObject(it.args).optString("image") == image) }
    if(active) return@runCatching null
    InterruptedOperation(name, _state.value.images.firstOrNull { it.id == image || it.physicalPath == image }?.id, json.optString("file", json.optString("relative", json.optString("parent"))), json.optInt("completed"), json.optInt("total", json.optJSONArray("pending")?.length() ?: 0))
   }.getOrNull()
  }
  _state.update { it.copy(interrupted = records) }
 }
 fun exportInterrupted(record: InterruptedOperation, uri: Uri, finished: () -> Unit) = launch("recovery", finished) {
  require(record.journal.matches(Regex("document-edit-[a-f0-9-]+\\.json")))
  require(!app.imageAccess.editIsActive(record.journal))
  val data = JSONObject(File(app.filesDir, record.journal).readText())
  val cache = File(data.getString("cache")).canonicalFile
  require(cache.parentFile == app.cacheDir.canonicalFile && cache.name.startsWith("image-document") && cache.isFile)
  app.contentResolver.openOutputStream(uri, "wt")!!.use { output -> cache.inputStream().use { it.copyTo(output) } }
 }
 fun reviewInterrupted(record: InterruptedOperation, finished: () -> Unit) = launch("recovery", finished) {
  require(record.journal in listOf("file-transfer.json", "folder-copyback.json") || record.journal.matches(Regex("document-edit-[a-f0-9-]+\\.json")))
  val journal = File(app.filesDir, record.journal)
  if(record.journal == "file-transfer.json" || record.journal.startsWith("document-edit-")) {
   val image = dao.image(record.imageId ?: error("Image no longer exists")) ?: error("Image no longer exists")
   app.imageAccess.mount(image.physicalPath ?: error("Image path unavailable"), image.title, false)
  }
  if(record.journal.startsWith("document-edit-")) app.imageAccess.restoreDocument(record.journal) else check(journal.renameTo(File(app.filesDir, "${record.journal}.reviewed-${System.currentTimeMillis()}")))
  refreshInterrupted()
 }
 fun checkUsb(system: String, mode: String, loaded: (String) -> Unit) = launch("usb-check") {
  val status = usb.inspect()
  val candidates = status.luns.filter { system != "configfs" || it.path.contains("/functions/") }
  val node = usb.serialized {
   RootShell.inMountNamespace(when(system) {
    "functions" -> "test -e /sys/class/android_usb/android0/functions && echo yes || true"
    "samsung" -> "test -e /sys/devices/platform/android_usb/UsbMenuSel && echo yes || true"
    "configfs" -> "for p in /config/usb_gadget/*/UDC /sys/kernel/config/usb_gadget/*/UDC; do [ -f \"${'$'}p\" ] && echo yes && break; done; true"
    else -> "getprop sys.usb.controller"
   }).isNotBlank()
  }
  val result = when {
   !status.root -> app.getString(R.string.error_root)
   !node && system != "auto" -> app.getString(R.string.usb_check_missing, system)
   mode == "none" -> app.getString(R.string.usb_check_disabled)
   candidates.isEmpty() -> app.getString(R.string.usb_check_no_lun)
   else -> app.getString(R.string.usb_check_ready, candidates.size, candidates.count { it.supportsReadOnly }, candidates.count { it.supportsCdrom })
  }
  withContext(Dispatchers.Main) { loaded(result) }
 }
 fun estimateFolder(source: String, filesystem: String, extra: Long, loaded: (dev.usbdroid.files.FolderEstimate) -> Unit) = launch("folder-estimate") {
  val estimate = dev.usbdroid.files.FolderImage(app).estimate(source, ImageFilesystem.valueOf(filesystem), extra)
  withContext(Dispatchers.Main) { loaded(estimate) }
 }
 fun previewFolderCopyBack(image: ImageEntry, loaded: (dev.usbdroid.files.CopyBackPlan) -> Unit) = launch("folder-preview:${image.id}") {
  val plan = dev.usbdroid.files.FolderCopyBack(app).preview(image)
  withContext(Dispatchers.Main) { loaded(plan) }
 }
 fun applyFolderCopyBack(plan: dev.usbdroid.files.CopyBackPlan, replace: Boolean, finished: () -> Unit) = submit("FOLDER_COPYBACK", plan.image.title, JSONObject().put("image", plan.image.id).put("source", plan.source).put("replace", replace).put("items", org.json.JSONArray().also { items -> plan.items.forEach { item -> items.put(JSONObject().put("relative", item.relative).put("directory", item.directory).put("bytes", item.bytes).put("imageHash", item.imageHash).put("targetHash", item.targetHash).put("blocked", item.blocked)) } }), finished)
 fun fileTransfer(id: String, relatives: List<String>, parent: String, move: Boolean, finished: () -> Unit) {
  val mount = _state.value.localImages.firstOrNull { it.id == id } ?: return
  val image = _state.value.images.firstOrNull { it.physicalPath == mount.image } ?: return
  submit("FILE_TRANSFER", image.title, JSONObject().put("image", image.id).put("mount", id).put("paths", org.json.JSONArray(relatives)).put("parent", parent).put("move", move), finished)
 }
 fun fileDeleteMany(id: String, relatives: List<String>, finished: () -> Unit) = launch("image-file:$id", finished) { relatives.forEach { app.imageAccess.delete(id, it) } }
 fun fileCreate(id: String, parent: String, name: String, directory: Boolean, finished: () -> Unit) = launch("image-file:$id", finished) { app.imageAccess.create(id, parent, name, directory) }
 fun fileRename(id: String, relative: String, name: String, finished: () -> Unit) = launch("image-file:$id", finished) { app.imageAccess.rename(id, relative, name) }
 fun fileDelete(id: String, relative: String, finished: () -> Unit) = launch("image-file:$id", finished) { app.imageAccess.delete(id, relative) }
 fun fileReadText(id: String, relative: String, loaded: (String, String) -> Unit) = launch("image-file:$id") {
  val result = app.imageAccess.readText(id, relative); withContext(Dispatchers.Main) { loaded(result.first, result.second) }
 }
 fun fileWriteText(id: String, relative: String, text: String, signature: String, finished: () -> Unit) = launch("image-file:$id", finished) { app.imageAccess.writeText(id, relative, text, signature) }
 fun fileImport(id: String, parent: String, uri: Uri, finished: () -> Unit) = launch("image-file:$id", finished) {
  val temp = File.createTempFile("image-import", ".tmp", app.cacheDir)
  try { app.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }; app.imageAccess.copyIn(id, parent, app.library.name(uri.toString()), temp) } finally { temp.delete() }
 }
 fun fileExport(id: String, relative: String, uri: Uri, finished: () -> Unit) = launch("image-file:$id", finished) {
  val temp = File.createTempFile("image-export", ".tmp", app.cacheDir)
  try { app.imageAccess.copyOut(id, relative, temp); temp.inputStream().use { input -> app.contentResolver.openOutputStream(uri, "wt")!!.use { input.copyTo(it) } } } finally { temp.delete() }
 }
 fun settings(p: Preferences, finished: () -> Unit = {}) = launch("settings", finished) { app.settings.save(p) }
 fun sorting(p: Preferences) {
  val previous = _state.value.preferences
  pendingSortWrites++
  _state.update { it.copy(preferences = p) }
  viewModelScope.launch {
   try { sortMutex.withLock { app.settings.saveSorting(previous, p) } }
   catch(e: Exception) { notify(e.message ?: "Could not save sorting") }
   finally { pendingSortWrites-- }
  }
 }
 fun inspect() { if(_state.value.rootBusy) return; _state.update { it.copy(rootBusy = true) }; launch("inspect") { try { val value = usb.inspect(); _state.update { it.copy(usb = value, rootChecked = true) }; if(value.root) refreshAccessNow() } finally { _state.update { it.copy(rootBusy = false) } } } }
 fun prepare() = launch("prepare") { val value = usb.prepareConfigfs(); _state.update { it.copy(usb = value) } }
 private fun submit(kind: String, title: String, args: JSONObject, finished: () -> Unit = {}, queued: () -> Unit = {}) {
  var completed = false
  launch(operationKey(kind, args), { if(completed) finished() }) {
   val id = UUID.randomUUID().toString()
   downloadNameMutex.withLock {
    if(kind == "DOWNLOAD" && args.optBoolean("_copy")) args.put("name", app.library.copyFilename(args.getString("name"), args.optString("storage").ifBlank { null }))
    dao.putJob(TransferJob(id, kind, title, args.toString())); enqueue(id)
   }
   withContext(Dispatchers.Main) { queued() }
   val result = dao.jobs().first { jobs -> jobs.none { it.id == id && it.running } }.firstOrNull { it.id == id }
   completed = result?.state == "DONE"
   if(result?.state == "FAILED") error(result.error.ifBlank { "Operation failed" })
  }
 }
 private fun enqueue(id: String) { val request = OneTimeWorkRequestBuilder<ImageWorker>().setInputData(workDataOf("id" to id)).build(); WorkManager.getInstance(app).enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, request) }
 fun import(uri: Uri) { submit("IMPORT", app.getString(R.string.import_image), JSONObject().put("uri", uri.toString()).put("name", app.library.name(uri.toString()))) }
 fun add(uri: Uri) = launch("add-uri") { app.library.addUri(uri) }
 fun addPath(path: String, finished: () -> Unit = {}) = launch("add-path", finished) { app.library.addPath(path) }
 fun create(name: String, size: Long, fat: Boolean, allocate: Boolean) = submit("CREATE", name, JSONObject().put("name", if(name.endsWith(".img", true)) name else "$name.img").put("mib", size).put("fat", fat).put("allocate", allocate))
 fun createFormattedAt(name: String, size: Long, filesystem: String, allocate: Boolean, storageId: String?, finished: () -> Unit = {}) = submit("CREATE", name, JSONObject().put("name", name).put("mib", size).put("filesystem", filesystem).put("allocate", allocate).put("storage", storageId), finished)
 fun createAt(name: String, size: Long, fat: Boolean, allocate: Boolean, storageId: String?, finished: () -> Unit = {}) = submit("CREATE", name, JSONObject().put("name", name).put("mib", size).put("fat", fat).put("allocate", allocate).put("storage", storageId), finished)
 fun rename(image: ImageEntry, title: String, finished: () -> Unit = {}) = launch("rename:${image.id}", finished) { require(title.isNotBlank()); dao.putImage(image.copy(title = title.trim())) }
 fun remove(image: ImageEntry, delete: Boolean, finished: () -> Unit = {}) = launch("remove:${image.id}", finished) { ImageLocks.use(image.id) { app.library.delete(image, delete) }; if(!delete) _state.update { it.copy(undoRemoval = UndoRemoval(UUID.randomUUID().toString(), listOf(image.id))) } }
 fun resize(image: ImageEntry, size: Long, finished: () -> Unit = {}) = submit("RESIZE", image.title, JSONObject().put("image", image.id).put("mib", size), finished)
 fun move(image: ImageEntry, directory: String, finished: () -> Unit = {}) = submit("MOVE", image.title, JSONObject().put("image", image.id).put("name", app.library.name(image.location)).put("storage", directory), finished)
 fun checksum(image: ImageEntry, algorithm: String) = submit("CHECKSUM", "$algorithm · ${image.title}", JSONObject().put("image", image.id).put("algorithm", algorithm))
 fun convert(image: ImageEntry, finished: () -> Unit = {}) = submit("HYBRID", "isohybrid · ${image.title}", JSONObject().put("image", image.id), finished)
 fun copyForHost(image: ImageEntry) = submit("COPY_HOST", image.title, JSONObject().put("image", image.id).put("name", "${System.currentTimeMillis()}-${app.library.name(image.location)}"))
 fun export(image: ImageEntry, uri: Uri) = submit("EXPORT", image.title, JSONObject().put("image", image.id).put("uri", uri.toString()))
 fun exportOriginal(job: TransferJob, uri: Uri) = submit("EXPORT", job.title, JSONObject().put("uriSource", JSONObject(job.args).getString("_original")).put("uri", uri.toString()))
 fun host(image: ImageEntry, lun: Lun, mode: HostMode, finished: () -> Unit = {}) = launch("host:${lun.path}:${image.id}", finished) { ImageLocks.use(image.id) {
  require(dao.image(image.id)?.hidden == false && !_state.value.working("bulk-hide") && !_state.value.working("bulk-delete")) { app.getString(R.string.selection_busy) }
  val path = image.physicalPath ?: error(app.getString(R.string.copy_required))
  val current = usb.inspect()
  _state.update { it.copy(usb = current) }
  check(current.root) { current.error ?: app.getString(R.string.root_required_title) }
  require(current.luns.none { it.file == path }) { app.getString(R.string.image_already_connected) }
  val selected = current.luns.firstOrNull { it.path == lun.path } ?: error(app.getString(R.string.mode_unavailable))
  require(selected.file.isBlank()) { app.getString(R.string.mode_unavailable) }
  usb.host(DiskImage(path, image.title, image.size, image.modified), selected, mode, _state.value.preferences)
  try { app.startForegroundService(Intent(app, HostingService::class.java).putExtra("title", image.title)) } catch(e: Exception) { usb.eject(lun); throw e }
  app.settings.rememberHost(mode.name)
  _state.update { it.copy(usb = usb.inspect()) }
 } }
 fun eject(lun: Lun) = launch("eject:${lun.path}") { usb.eject(lun); val value = usb.inspect(); _state.update { it.copy(usb = value) }; if(!usb.hasSessions()) app.stopService(Intent(app, HostingService::class.java)) }
 fun ejectAll() = launch("eject-all") { usb.ejectAll(); _state.update { it.copy(usb = usb.inspect()) }; app.stopService(Intent(app, HostingService::class.java)) }
 fun ejectImage(image: ImageEntry) = launch("eject-image:${image.id}") {
  usb.inspect().luns.filter { it.file == image.physicalPath }.forEach { usb.eject(it) }
  _state.update { it.copy(usb = usb.inspect()) }; if(!usb.hasSessions()) app.stopService(Intent(app, HostingService::class.java))
 }
 fun mode(mode: String, permanent: Boolean, finished: () -> Unit = {}) = launch("usb-mode", finished) { usb.setMode(mode, permanent); _state.update { it.copy(usb = usb.inspect()) } }
 fun catalog() = launch("catalog") { val (releases, warnings) = Catalog.load(OkHttpClient(), dao.allRepositories(), app.cacheDir); _state.update { it.copy(catalog = releases) }; if(warnings.isNotEmpty()) notify(warnings.joinToString("\n")) }
 fun download(release: Release, finished: () -> Unit = {}, allowDuplicate: Boolean = false) {
  val state = _state.value
  val previous = state.jobs.firstOrNull { it.kind == "DOWNLOAD" && runCatching { JSONObject(it.args).optString("url") == release.url }.getOrDefault(false) && it.state != "CANCELLED" }
  if(previous != null && previous.state in listOf("QUEUED", "RUNNING", "PAUSED")) { notifyError(app.getString(R.string.download_already_queued)); return }
  val name = downloadFilename(release)
  if(!allowDuplicate && (previous?.state == "DONE" && state.images.any { it.id == previous.result } || state.images.any { File(it.location).name.equals(name, true) })) { _state.update { it.copy(duplicateDownload = release) }; return }
  submit("DOWNLOAD", "${release.name} ${release.version}", JSONObject().put("url", release.url).put("name", name).put("allowHttp", release.allowHttp).put("_copy", allowDuplicate).put("hybrid", state.preferences.autoHybrid && File(name).extension.equals("iso", true)), queued = finished)
 }

 fun pause(id: String) = launch("job:$id") {
  val current = dao.job(id) ?: return@launch
  if(!current.running || JSONObject(current.args).optString("_phase") == "saving") return@launch
  dao.jobState(id, "PAUSED"); WorkManager.getInstance(app).cancelUniqueWork(id).result.get()
 }
 fun resume(id: String) = launch("job:$id") {
  if(dao.job(id)?.state !in listOf("PAUSED", "FAILED")) return@launch
  WorkManager.getInstance(app).cancelUniqueWork(id).result.get()
  val previous = dao.job(id) ?: return@launch
  require(previous.state in listOf("PAUSED", "FAILED"))
  val restart = previous.kind == "DOWNLOAD" && listOf("etag changed", "remote file changed", "invalid content-range", "remote size changed").any { previous.error.lowercase().contains(it) }
  if(restart) { File(app.library.directory, ".$id.partial").delete(); File(app.library.directory, ".$id.original").delete(); dao.putJob(previous.copy(state = "QUEUED", etag = "", lastModified = "", progress = 0, total = 0, error = "", args = JSONObject(previous.args).put("_downloadComplete", false).put("_hybridComplete", false).toString())) }
  else dao.putJob(previous.copy(state = "QUEUED", error = ""))
  enqueue(id)
 }
 fun cancel(id: String) = launch("job:$id") {
  val current = dao.job(id) ?: return@launch
  if(!current.running || JSONObject(current.args).optString("_phase") == "saving") return@launch
  dao.jobState(id, "CANCELLED"); WorkManager.getInstance(app).cancelUniqueWork(id).result.get()
  File(app.library.directory, ".$id.partial").delete(); File(app.library.directory, ".$id.original").delete()
 }
 fun dismissJob(id: String) = launch("job:$id") { val job = dao.job(id) ?: return@launch; require(job.state !in listOf("RUNNING", "QUEUED")); dao.deleteJob(id); File(app.library.directory, ".$id.partial").delete(); File(app.library.directory, ".$id.original").delete() }
 fun addStorage(uri: Uri) = launch("add-storage") { app.library.addStorage(uri) }
 fun addRootStorage(path: String) = launch("add-storage") { app.library.addRootStorage(path) }
 fun primaryStorage(storage: StorageLocation) = launch("storage-primary") { dao.clearPrimary(); dao.putStorage(storage.copy(primary = true)) }
 fun removeStorage(storage: StorageLocation, finished: () -> Unit = {}) = launch("storage:${storage.id}", finished) { require(!storage.primary); dao.deleteStorage(storage.id) }
 fun repository(repository: CatalogRepository, finished: () -> Unit = {}) = launch("repository:${repository.id}", finished) { dao.putRepository(repository) }
 fun deleteRepository(id: String, finished: () -> Unit = {}) = launch("repository:$id", finished) { dao.deleteRepository(id) }
 fun restoreHidden() = launch("restore-hidden") { dao.revealImages(); app.library.scan() }
 fun report() = launch("report") {
  val usbReport = usb.report()
  val current = _state.value
  val report = buildString {
   appendLine(usbReport)
   appendLine("\n[Application]")
   appendLine("Package: ${app.packageName}; versionCode: ${BuildConfig.VERSION_CODE}; debug: ${BuildConfig.DEBUG}")
   appendLine("Notifications: ${androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled()}")
   appendLine("APK installation allowed: ${app.packageManager.canRequestPackageInstalls()}")
   appendLine("USB backend: ${current.preferences.usbSystem}; automatic: ${current.preferences.autoUsb}; mode: ${current.preferences.usbMode}")
   appendLine("Language: ${current.preferences.language}; theme: ${current.preferences.theme}; AMOLED: ${current.preferences.amoled}")
   appendLine("\n[Storage]")
   appendLine("Local free bytes: ${app.library.directory.usableSpace}")
   current.storage.forEach { appendLine("${it.title} | ${it.kind} | primary=${it.primary} | ${it.location}") }
   appendLine("\n[Library]")
   current.images.forEach { appendLine("${it.title} | bytes=${it.size} | allocated=${it.allocatedSize ?: "unknown"} | ${it.location}") }
   appendLine("\n[Jobs]")
   current.jobs.filter { it.running || it.state == "FAILED" }.forEach { appendLine("${it.kind} | ${it.title} | ${it.state} | ${it.progress}/${it.total} | ${it.error}") }
   appendLine("\n[Repositories]")
   current.repositories.forEach { appendLine("${it.title} | enabled=${it.enabled} | http=${it.allowHttp} | ${it.url}") }
  }
  _state.update { it.copy(diagnostics = report) }
 }
 fun setup(step: Int? = null, lun: String? = null, disk: String? = null, boot: String? = null) = launch("setup") { app.settings.setup(step = step, lun = lun, disk = disk, boot = boot) }
 fun restartSetup() = launch("setup") { app.settings.setup(step = -1, complete = false); clearMessage() }
 fun setupCreate(cdrom: Boolean = false) = launch("setup-create") {
  val existing = dao.job(_state.value.preferences.setupJob)
  require(existing?.state !in listOf("RUNNING", "QUEUED"))
  val oldImage = existing?.let { dao.image(it.result) }
  val oldLun = usb.inspect().luns.firstOrNull { it.file == oldImage?.physicalPath }
  if(oldLun != null) { usb.eject(oldLun); _state.update { it.copy(usb = usb.inspect()) } }
  val id = UUID.randomUUID().toString()
  val args = JSONObject().put("name", "USBDroid-test-${System.currentTimeMillis()}.${if(cdrom) "iso" else "img"}").put("mib", 32).put("fat", !cdrom).put("allocate", false).put("storage", "default").put("testBoot", !cdrom).put("testCD", cdrom)
  dao.putJob(TransferJob(id, "CREATE", app.getString(R.string.setup_test_title), args.toString()))
  app.settings.setup(job = id); enqueue(id)
  val result = dao.jobs().first { jobs -> jobs.any { it.id == id && !it.running } }.first { it.id == id }
  if(result.state == "FAILED") error(result.error.ifBlank { "Operation failed" })
 }
 fun finishSetup(finished: () -> Unit = {}) = launch("setup-finish", finished) {
  val job = dao.job(_state.value.preferences.setupJob)
  val image = job?.let { dao.image(it.result) }
  val lun = usb.inspect().luns.firstOrNull { it.path == _state.value.preferences.setupLun && it.file == image?.physicalPath }
  if(lun != null) usb.eject(lun)
  _state.update { it.copy(usb = usb.inspect()) }
  if(!usb.hasSessions()) app.stopService(Intent(app, HostingService::class.java))
  app.settings.setup(step = 8, complete = true)
 }
}
