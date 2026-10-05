package dev.usbdroid.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.ImageEntry
import dev.usbdroid.files.LocalImage

@Composable fun LocalMountActions(image: ImageEntry, local: LocalImage?, state: AppState, model: AppViewModel, browse: () -> Unit) {
 val context = LocalContext.current
 val busy = state.imageWorking(image.id) || local?.let { state.working("local-unmount:${it.id}") } == true
 Text(stringResource(R.string.local_card_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
 if(local == null) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   listOf(true, false).forEach { readOnly ->
    CardAction(stringResource(if(readOnly) R.string.folder_read_only else R.string.folder_read_write), if(readOnly) Icons.Rounded.Lock else Icons.Rounded.Edit, { model.mountLocal(image, readOnly) }, Modifier.weight(1f), enabled = !busy && !state.rootBusy && image.physicalPath != null && (readOnly || !image.file.extension.equals("iso", true)))
   }
  }
 } else {
  Text(stringResource(R.string.mount_phone_mode, stringResource(if(local.readOnly) R.string.folder_read_only else R.string.folder_read_write)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
  CardAction(stringResource(R.string.local_system_short), Icons.Rounded.FolderOpen, {
   val initial = context.app.imageAccess.documentUri(local.id)
   val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   val systemPackage = listOf("com.google.android.documentsui", "com.android.documentsui").firstOrNull { runCatching { context.packageManager.getApplicationInfo(it, 0) }.isSuccess }
   if(systemPackage != null) intent.setPackage(systemPackage)
   val browseIntent = Intent(Intent.ACTION_VIEW).setDataAndType(DocumentsContract.buildRootUri("${context.packageName}.images", local.id), DocumentsContract.Root.MIME_TYPE_ITEM).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   if(systemPackage != null) browseIntent.setPackage(systemPackage)
   runCatching { context.startActivity(browseIntent) }.onFailure { runCatching { context.startActivity(intent) }.onFailure { model.notifyError(context.getString(R.string.local_manager_unavailable)) } }
  }, Modifier.weight(1f), enabled = !busy)
  CardAction(stringResource(R.string.local_app_short), Icons.Rounded.Folder, browse, Modifier.weight(1f), enabled = !busy)
  }
  CardAction(stringResource(R.string.local_unmount), Icons.Rounded.Eject, { model.unmountLocal(local.id) }, Modifier.fillMaxWidth(), enabled = !busy)
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ImageBrowserScreen(image: ImageEntry?, state: AppState, model: AppViewModel, close: () -> Unit) {
 val local = state.localImages.firstOrNull { it.image == image?.physicalPath }
 BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(image?.title.orEmpty()) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) } }) }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
   if(local != null) ImageFileBrowser(local, state, model)
   else Text(stringResource(R.string.local_empty))
  }
 }
}
