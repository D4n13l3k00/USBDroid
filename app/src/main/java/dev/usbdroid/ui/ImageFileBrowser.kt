package dev.usbdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.files.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ImageFileBrowser(mount: LocalImage, state: AppState, model: AppViewModel) {
 val context = LocalContext.current
 var path by rememberSaveable(mount.id) { mutableStateOf("") }
 var query by rememberSaveable(mount.id) { mutableStateOf("") }
 var selection by rememberSaveable(mount.id) { mutableStateOf(listOf<String>()) }
 var clipboardFiles by rememberSaveable(mount.id) { mutableStateOf(listOf<String>()) }
 var cut by rememberSaveable(mount.id) { mutableStateOf(false) }
 var bulkDelete by remember { mutableStateOf(false) }
 var revision by remember { mutableIntStateOf(0) }
 var files by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
 var loading by remember { mutableStateOf(true) }
 var selected by remember { mutableStateOf<ImageFile?>(null) }
 var operation by remember { mutableStateOf<String?>(null) }
 var menuPath by remember { mutableStateOf<String?>(null) }
 var text by remember { mutableStateOf("") }
 var signature by remember { mutableStateOf("") }
 var exportPath by remember { mutableStateOf("") }
 val busy = state.working("image-file:${mount.id}")
 fun done() { operation = null; selected = null; selection = emptyList(); bulkDelete = false; revision++ }
 val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) model.fileImport(mount.id, path, uri) { done() } }
 val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri != null) model.fileExport(mount.id, exportPath, uri) { done() } }
 LaunchedEffect(mount.id, path) { selection = emptyList(); query = "" }
 LaunchedEffect(busy) { if(!busy) revision++ }
 LaunchedEffect(mount.id, path, revision) {
  loading = true; menuPath = null
  try { files = withContext(Dispatchers.IO) { context.app.imageAccess.children(mount.id, path) } }
  catch(e: kotlinx.coroutines.CancellationException) { throw e }
  catch(e: Exception) { files = emptyList(); model.notifyError(e.message ?: "Cannot read image directory") }
  finally { loading = false }
 }
 Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
  Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    if(path.isNotBlank()) IconButton({ path = path.substringBeforeLast('/', "") }, enabled = !busy && !loading) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.local_parent)) }
    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
     TextButton({ path = "" }, enabled = !busy && !loading) { Text(stringResource(R.string.fm_root)) }
     path.split('/').filter(String::isNotBlank).forEachIndexed { index, segment ->
      Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton({ path = path.split('/').take(index + 1).joinToString("/") }, enabled = !busy && !loading) { Text(segment) }
     }
    }
    IconButton({ revision++ }, enabled = !busy && !loading) { Icon(Icons.Rounded.Refresh, stringResource(R.string.ui_72)) }
   }
   OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.fm_search)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
   if(selection.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(R.string.selection_count, selection.size), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    IconButton({ selection = files.filter { it.name.contains(query, true) }.map { it.relative } }, enabled = !busy) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.selection_all)) }
    if(!mount.readOnly) {
     IconButton({ clipboardFiles = selection; cut = false; selection = emptyList() }, enabled = !busy) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.fm_copy)) }
     IconButton({ clipboardFiles = selection; cut = true; selection = emptyList() }, enabled = !busy) { Icon(Icons.Rounded.ContentCut, stringResource(R.string.fm_move)) }
     IconButton({ bulkDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.local_delete)) }
    }
    IconButton({ selection = emptyList() }) { Icon(Icons.Rounded.Close, stringResource(R.string.selection_clear)) }
   }
   if(clipboardFiles.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(if(cut) R.string.fm_moving_count else R.string.fm_copying_count, clipboardFiles.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    TextButton({ model.fileTransfer(mount.id, clipboardFiles, path, cut) { clipboardFiles = emptyList(); done() } }, enabled = !busy && !loading) { Text(stringResource(R.string.fm_paste)) }
    IconButton({ clipboardFiles = emptyList() }, enabled = !busy) { Icon(Icons.Rounded.Close, stringResource(R.string.cancel)) }
   }
   if(!mount.readOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
    IconButton({ operation = "mkdir" }, enabled = !busy && !loading) { Icon(Icons.Rounded.CreateNewFolder, stringResource(R.string.local_folder_new)) }
    IconButton({ operation = "new" }, enabled = !busy && !loading) { Icon(Icons.Rounded.NoteAdd, stringResource(R.string.local_file_new)) }
    IconButton({ importer.launch(arrayOf("*/*")) }, enabled = !busy && !loading) { Icon(Icons.Rounded.FileDownload, stringResource(R.string.local_import)) }
   }
   if(loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
   if(!loading && files.isEmpty()) Text(stringResource(R.string.local_directory_empty), Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
   LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
   items(files.filter { it.name.contains(query, ignoreCase = true) }, key = { it.relative }) { file ->
    Row(Modifier.fillMaxWidth().combinedClickable(enabled = !busy && !loading, onLongClick = { selection = (selection + file.relative).distinct(); menuPath = null }, onClick = { if(selection.isNotEmpty()) selection = if(file.relative in selection) selection - file.relative else selection + file.relative else if(file.directory) path = file.relative else { selected = file; menuPath = file.relative } }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
     if(selection.isNotEmpty()) Checkbox(file.relative in selection, null) else Icon(if(file.directory) Icons.Rounded.Folder else Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.primary)
     Column(Modifier.weight(1f)) { Text(file.name); if(!file.directory) Text(byteText(file.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
     Box {
      if(!file.directory || !mount.readOnly) IconButton({ selected = file; menuPath = file.relative }, enabled = !busy && !loading) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.ui_80)) }
      DropdownMenu(menuPath == file.relative, { menuPath = null }, modifier = Modifier.widthIn(min = 240.dp, max = 340.dp), shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
       if(!file.directory) {
        DropdownMenuItem(text = { Text(stringResource(R.string.local_export)) }, leadingIcon = { Icon(Icons.Rounded.FileUpload, null) }, onClick = { menuPath = null; exportPath = file.relative; exporter.launch(file.name) })
        if(!mount.readOnly && file.size <= 1048576) DropdownMenuItem(text = { Text(stringResource(R.string.local_edit)) }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) }, onClick = { menuPath = null; model.fileReadText(mount.id, file.relative) { content, version -> text = content; signature = version; operation = "edit" } })
       }
       if(!mount.readOnly) {
        DropdownMenuItem(text = { Text(stringResource(R.string.local_rename)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menuPath = null; operation = "rename" })
        DropdownMenuItem(text = { Text(stringResource(R.string.local_delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menuPath = null; operation = "delete" })
       }
      }
     }
    }
   }
   }
  }
 }
 RetainedPopup(bulkDelete.takeIf { it }) { PopupConfirmDialog(stringResource(R.string.local_delete), stringResource(R.string.fm_delete_count, selection.size), { bulkDelete = false }, busy) { model.fileDeleteMany(mount.id, selection.toList()) { done() } } }
 RetainedPopup(operation?.let { it to selected }) { (shown, file) ->
 when(shown) {
  "mkdir", "new" -> PopupInputDialog(stringResource(if(shown == "mkdir") R.string.local_folder_new else R.string.local_file_new), stringResource(R.string.ui_1), "", { operation = null }, busy) { name -> model.fileCreate(mount.id, path, name, shown == "mkdir") { done() } }
  "rename" -> file?.let { file -> PopupInputDialog(stringResource(R.string.local_rename), stringResource(R.string.ui_1), file.name, { operation = null }, busy) { name -> model.fileRename(mount.id, file.relative, name) { done() } } }
  "delete" -> file?.let { file -> PopupConfirmDialog(stringResource(R.string.local_delete), "${file.name}\n\n${stringResource(R.string.local_delete_body)}", { operation = null }, busy) { model.fileDelete(mount.id, file.relative) { done() } } }
  "edit" -> file?.let { file -> ImagePopup({ if(!busy) operation = null }, { Text(file.name) }, { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 400.dp), enabled = !busy && !loading) }, { BusyButton(stringResource(R.string.local_save), busy, { model.fileWriteText(mount.id, file.relative, text, signature) { done() } }) }, dismissButton = { TextButton({ operation = null }, enabled = !busy && !loading) { Text(stringResource(R.string.cancel)) } }) }
 }
 }
}
