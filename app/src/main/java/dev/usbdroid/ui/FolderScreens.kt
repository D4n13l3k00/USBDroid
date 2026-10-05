package dev.usbdroid.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import dev.usbdroid.data.*
import dev.usbdroid.files.LocalImage
import dev.usbdroid.files.ImageFile
import dev.usbdroid.usb.HostMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun FolderShareScreen(state: AppState, model: AppViewModel, close: () -> Unit) {
 val context = LocalContext.current
 var source by rememberSaveable { mutableStateOf("") }
 var physical by rememberSaveable { mutableStateOf<String?>(null) }
 var title by rememberSaveable { mutableStateOf("Folder") }
 var mode by rememberSaveable { mutableIntStateOf(if(state.temporaryImageId != null) 1 else 0) }
 var readOnly by rememberSaveable { mutableStateOf(false) }
 var filesystem by rememberSaveable { mutableStateOf("FAT32") }
 var extra by rememberSaveable { mutableStateOf("128") }
 var estimate by remember { mutableStateOf<dev.usbdroid.files.FolderEstimate?>(null) }
 var plan by remember { mutableStateOf<dev.usbdroid.files.CopyBackPlan?>(null) }
 LaunchedEffect(source, filesystem, extra) { estimate = null }
 var host by remember { mutableStateOf(false) }
 var discard by remember { mutableStateOf(false) }
 val temporary = state.images.firstOrNull { it.id == state.temporaryImageId }
 val connected = temporary?.let { image -> state.usb.luns.any { it.file == image.physicalPath } } == true
 val busy = state.working("folder-create") || state.working("folder-mtp") || state.working("folder-mtp-stop") || state.working("folder-image") || state.working("folder-discard")
 val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri != null) {
  runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
  source = uri.toString()
  title = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: "Folder"
  physical = runCatching { DocumentsContract.getTreeDocumentId(uri).takeIf { uri.authority == "com.android.externalstorage.documents" && it.startsWith("primary:") }?.let { "/storage/emulated/0/${it.removePrefix("primary:")}" } }.getOrNull()
 } }
 LaunchedEffect(Unit) { model.refreshAccess() }
 BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.folder_share)) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } }) }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
   SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    listOf(R.string.folder_mtp, R.string.folder_image).forEachIndexed { index, label -> SegmentedButton(mode == index, { mode = index }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) } }
   }
   Text(stringResource(if(mode == 0) R.string.folder_mtp_hint else R.string.folder_image_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
   OutlinedTextField(if(source.startsWith("content:")) physical ?: source else source, { source = it; physical = it.takeIf { p -> p.startsWith('/') } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.folder_path)) }, enabled = !busy, singleLine = true)
   OutlinedButton({ tree.launch(null) }, Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.folder_choose)) }
   OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.folder_entry_title)) }, enabled = !busy, singleLine = true)
   if(mode == 0) {
    if(source.startsWith("content:") && physical == null) Text(stringResource(R.string.folder_physical_required), color = MaterialTheme.colorScheme.onSurfaceVariant)
    BusyButton(stringResource(R.string.folder_create_mtp), busy, { model.addMtpFolder(physical ?: source, title, readOnly, close) }, Modifier.fillMaxWidth(), enabled = (physical ?: source).startsWith('/') && title.isNotBlank())
   } else {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     ImageFilesystem.entries.filter { it != ImageFilesystem.NONE }.forEach { fs -> FilterChip(filesystem == fs.name, { filesystem = fs.name }, label = { Text(fs.label) }, enabled = !busy) }
    }
    OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.folder_extra)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy, singleLine = true)
    BusyButton(stringResource(R.string.folder_calculate), state.working("folder-estimate"), { val request = Triple(source, filesystem, extra); model.estimateFolder(source, filesystem, extra.toLong()) { if(request == Triple(source, filesystem, extra)) estimate = it } }, Modifier.fillMaxWidth(), enabled = !busy && source.isNotBlank() && extra.toLongOrNull() in 16L..1048576L)
    estimate?.let { value ->
     Text(stringResource(R.string.folder_estimate, value.files, byteText(value.fileBytes), byteText(value.reserveBytes), byteText(value.imageBytes), byteText(value.availableBytes)), style = MaterialTheme.typography.bodyMedium)
    }
    BusyButton(stringResource(R.string.folder_make_image), busy, { model.folderImage(source, title, filesystem, extra.toLong()) }, Modifier.fillMaxWidth(), enabled = estimate?.let { it.availableBytes >= it.imageBytes } == true && !state.working("folder-estimate") && source.isNotBlank() && title.isNotBlank() && extra.toLongOrNull() in 16L..1048576L && temporary == null)
    state.jobs.filter { it.kind == "FOLDER_IMAGE" && it.running }.forEach { job -> JobProgress(job); Text(runCatching { org.json.JSONObject(job.args).optString("_file") }.getOrDefault(""), style = MaterialTheme.typography.bodySmall) }
    if(temporary != null) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(temporary.title, style = MaterialTheme.typography.bodyLarge)
      Text(sizeText(temporary.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if(connected) BusyButton(stringResource(R.string.folder_stop), state.imageWorking(temporary.id) || state.usbWorking(), { model.ejectImage(temporary) }, Modifier.fillMaxWidth())
      else Button({ host = true }, Modifier.fillMaxWidth(), enabled = !busy && state.folderShare == null && state.localImages.none { it.image == temporary.physicalPath }) { Text(stringResource(R.string.ui_114)) }
      BusyButton(stringResource(R.string.folder_copyback), state.working("folder-preview:${temporary.id}") || state.working("folder-copyback:${temporary.id}"), { model.previewFolderCopyBack(temporary) { plan = it } }, Modifier.fillMaxWidth(), enabled = !busy && !connected && state.localImages.none { it.image == temporary.physicalPath })
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       TextButton(model::keepFolderImage, enabled = !busy) { Text(stringResource(R.string.folder_keep)) }
       TextButton({ discard = true }, enabled = !busy && !connected && state.localImages.none { it.image == temporary.physicalPath }) { Text(stringResource(R.string.folder_discard)) }
      }
     }
    }
   }
  }
 }
 RetainedPopup(plan) { shown -> FolderCopyBackDialog(shown, state.working("folder-copyback:${shown.image.id}"), { plan = null }) { replace -> model.applyFolderCopyBack(shown, replace) { plan = null } } }
 if(host && temporary != null) HostImageDialog(temporary, state, { host = false }, { lun, selected -> model.host(temporary, lun, selected) { host = false } }, { model.ejectImage(temporary) })
 if(discard && temporary != null) PopupConfirmDialog(stringResource(R.string.folder_discard), stringResource(R.string.folder_discard_body), { discard = false }, busy = state.working("folder-discard")) { model.discardFolderImage(temporary) { discard = false } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LocalImageScreen(image: ImageEntry?, state: AppState, model: AppViewModel, close: () -> Unit) {
 val context = LocalContext.current
 var readOnly by rememberSaveable { mutableStateOf(true) }
 var selected by rememberSaveable { mutableStateOf<String?>(null) }
 val mounts = if(image == null) state.localImages else state.localImages.filter { it.image == image.physicalPath || it.image == image.location }
 val mounted = mounts.firstOrNull { it.id == selected } ?: mounts.firstOrNull()
 LaunchedEffect(Unit) { model.refreshAccess() }
 BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(image?.title ?: stringResource(R.string.local_images)) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } }) }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
   if(mounts.isEmpty()) {
    if(image == null) EmptyView(stringResource(R.string.local_empty), stringResource(R.string.local_manager_hint))
    else {
     Text(stringResource(R.string.local_partition_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
     if(image.physicalPath == null) Text(stringResource(R.string.local_need_copy))
     else {
      Toggle(stringResource(R.string.folder_read_only), "", readOnly, !state.imageWorking(image.id)) { readOnly = it }
      BusyButton(stringResource(R.string.local_mount), state.imageWorking(image.id), { model.mountLocal(image, readOnly) }, Modifier.fillMaxWidth(), enabled = state.usb.luns.none { it.file == image.physicalPath })
     }
    }
   } else {
    mounts.forEach { entry -> FilterChip(mounted?.id == entry.id, { selected = entry.id }, label = { Text(entry.title) }) }
    if(mounted != null) {
     Text(stringResource(if(mounted.readOnly) R.string.local_readonly_hint else R.string.local_mounted), style = MaterialTheme.typography.titleMedium)
     if(mounted.offset > 0) Text(stringResource(R.string.local_first_partition, byteText(mounted.offset)), style = MaterialTheme.typography.bodySmall)
     Text(stringResource(R.string.local_manager_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
     androidx.compose.foundation.text.selection.SelectionContainer { Text("${stringResource(R.string.local_path)}\n${mounted.directory}", style = MaterialTheme.typography.bodySmall) }
     Button({
      val root = DocumentsContract.buildRootUri("${context.packageName}.images", mounted.id)
      val intent = Intent(Intent.ACTION_VIEW).setDataAndType(root, DocumentsContract.Root.MIME_TYPE_ITEM).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(DocumentsContract.EXTRA_INITIAL_URI, context.app.imageAccess.documentUri(mounted.id))) }.onFailure { model.notifyError(it.message ?: "File manager unavailable") } }
     }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.local_open_manager)) }
     ImageFileBrowser(mounted, state, model)
     BusyButton(stringResource(R.string.local_unmount), state.working("local-unmount:${mounted.id}"), { model.unmountLocal(mounted.id) }, Modifier.fillMaxWidth())
    }
   }
  }
 }
}
