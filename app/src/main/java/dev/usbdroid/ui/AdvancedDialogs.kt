package dev.usbdroid.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.data.*
import java.util.UUID

@Composable fun AdvancedDialogs(action: String?, image: ImageEntry?, state: AppState, model: AppViewModel, close: () -> Unit, choose: (String) -> Unit, selectTree: () -> Unit) {
 when(action) {
  "rename" -> if(image != null) PopupInputDialog(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_0), androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_1), image.title, close, busy = state.working("rename:${image.id}")) { model.rename(image, it, close) }
  "resize" -> if(image != null) {
   var value by rememberSaveable { mutableStateOf(((image.size + 1048575) / 1048576).toString()) }
   var unit by rememberSaveable { mutableIntStateOf(1) }; var approved by remember { mutableStateOf(false) }
   val mib = value.toLongOrNull()?.let { runCatching { Math.multiplyExact(it, if(unit == 1) 1 else 1024) }.getOrNull() }
   val shrink = mib != null && mib * 1048576 < image.size
   ImagePopup(onDismissRequest = close, title = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_2)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_3)) }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(unit == 1, { unit = 1 }, label = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_4)) }); FilterChip(unit == 2, { unit = 2 }, label = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_5)) }) }
    Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_6))
    if(shrink) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_7), color = MaterialTheme.colorScheme.error); Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(approved, { approved = it }); Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_8)) } }
   } }, confirmButton = { BusyButton(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_9), state.imageWorking(image.id), { model.resize(image, mib!!, close) }, enabled = mib != null && mib in 4..1048576 && (!shrink || approved)) }, dismissButton = { TextButton(onClick = close) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_10)) } }, progress = {})
  }
  "move" -> if(image != null) MenuDialog(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_11), state.storage.map { it.title }, close, busy = if(state.imageWorking(image.id)) state.storage.indices.toSet() else emptySet(), header = { OperationProgress(state) }) { model.move(image, state.storage[it].id, close) }
  "hybrid" -> if(image != null) PopupConfirmDialog(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_12), androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_13), close, busy = state.imageWorking(image.id)) { model.convert(image, close) }
  "url" -> {
   var url by rememberSaveable { mutableStateOf("") }; var http by rememberSaveable { mutableStateOf(false) }
   val busy = state.working("download:${url.trim()}")
   ScreenPanel(onDismissRequest = close, title = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_14)) }, text = { Column { OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_15)) }, enabled = !busy); Toggle(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_16), androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_17), http, enabled = !busy) { http = it }; state.jobs.filter { it.operationKey() == "download:${url.trim()}" }.forEach { JobRow(it, model, state) } } }, confirmButton = { BusyButton(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_18), busy, { model.download(Release("ISO / IMG", "", "", url.trim(), 0, http), close) }, enabled = url.startsWith("https://") || http && url.startsWith("http://")) }, dismissButton = { TextButton(onClick = close) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_10)) } })
  }
  "path" -> InputDialog(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_19), androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_20), "", close, busy = state.working("add-path")) { model.addPath(it.trim(), close) }
  "directories" -> StorageScreen(state, model, close, selectTree)
  "repositories" -> {
   var editing by remember { mutableStateOf<CatalogRepository?>(null) }; var new by remember { mutableStateOf(false) }
   val scroll = rememberScrollState()
   androidx.compose.animation.Crossfade(new, label = "repository-editor") { editor ->
    if(editor) { key(editing?.id ?: "new") { RepositoryEditor(editing, state, { new = false }) { model.repository(it) { new = false } } } }
    else RepositoriesScreen(state, model, close, scroll) { repo -> editing = repo; new = true }
   }
  }
  "usbsettings" -> UsbSettingsScreen(state, model, close)
  "diagnostics" -> DiagnosticsScreen(state, model, close)
  "about" -> AboutScreen(close)
 }
}
@Composable fun InputDialog(title: String, label: String, initial: String, close: () -> Unit, busy: Boolean = false, confirm: (String) -> Unit) { var value by rememberSaveable { mutableStateOf(initial) }; ScreenPanel(onDismissRequest = close, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, enabled = !busy) }, confirmButton = { BusyButton(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_39), busy, { confirm(value) }, enabled = value.isNotBlank()) }, dismissButton = { TextButton(onClick = close) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_10)) } }) }
@Composable fun ConfirmDialog(title: String, text: String, close: () -> Unit, busy: Boolean = false, confirm: () -> Unit) { ScreenPanel(onDismissRequest = close, title = { Text(title) }, text = { Text(text) }, confirmButton = { BusyButton(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_45), busy, confirm) }, dismissButton = { TextButton(onClick = close) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_10)) } }) }
@Composable fun InfoDialog(title: String, text: String, close: () -> Unit) { ScreenPanel(onDismissRequest = close, title = { Text(title) }, text = { Text(text, Modifier) }, confirmButton = { TextButton(onClick = close) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_46)) } }) }
@Composable private fun RepositoryEditor(original: CatalogRepository?, state: AppState, close: () -> Unit, save: (CatalogRepository) -> Unit) {
 val id = rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
 val busy = state.working("repository:$id")
 var title by rememberSaveable { mutableStateOf(original?.title.orEmpty()) }; var url by rememberSaveable { mutableStateOf(original?.url.orEmpty()) }; var http by rememberSaveable { mutableStateOf(original?.allowHttp ?: false) }
 ScreenPanel(onDismissRequest = close, title = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_47)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_1)) }, enabled = !busy); OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_48)) }, enabled = !busy); Toggle(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_16), androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_17), http, enabled = !busy) { http = it } } }, confirmButton = { BusyButton(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_39), busy, { save(CatalogRepository(id, title.trim(), url.trim(), original?.enabled ?: true, http)) }, enabled = title.isNotBlank() && (url.startsWith("https://") || http && url.startsWith("http://"))) }, dismissButton = { TextButton(onClick = close) { Text(androidx.compose.ui.res.stringResource(dev.usbdroid.R.string.ui_10)) } })
}
