package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StorageScreen(state: AppState, model: AppViewModel, close: () -> Unit, selectTree: () -> Unit) {
 var rootExpanded by rememberSaveable { mutableStateOf(false) }
 var rootPath by rememberSaveable { mutableStateOf("") }
 var removing by remember { mutableStateOf<StorageLocation?>(null) }
 BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.ui_21)) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   state.storage.forEach { storage ->
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
       Icon(if(storage.kind == "ROOT") Icons.Rounded.AdminPanelSettings else Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
       Column(Modifier.weight(1f)) {
        Text(if(storage.id == "default") stringResource(R.string.storage_app) else storage.title, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(when { storage.primary -> R.string.storage_primary; storage.kind == "ROOT" -> R.string.storage_root; else -> R.string.storage_saf }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
       }
       if(!storage.primary && storage.id != "default") IconButton({ removing = storage }) { Icon(Icons.Rounded.Close, stringResource(R.string.ui_22)) }
      }
      androidx.compose.foundation.text.selection.SelectionContainer { Text(storage.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      if(!storage.primary) BusyTextButton(stringResource(R.string.storage_make_primary), state.working("storage-primary"), { model.primaryStorage(storage) })
     }
    }
   }
   Spacer(Modifier.height(4.dp))
   Text(stringResource(R.string.storage_add_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
   FilledTonalButton(selectTree, Modifier.fillMaxWidth(), enabled = !state.working("add-storage")) { Icon(Icons.Rounded.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_23)) }
   OutlinedButton({ rootExpanded = !rootExpanded }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.storage_root_expand)); Spacer(Modifier.weight(1f)); Icon(if(rootExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) }
   AnimatedVisibility(rootExpanded, enter = cardExpand, exit = cardCollapse) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
     OutlinedTextField(rootPath, { rootPath = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ui_24)) }, singleLine = true, enabled = !state.working("add-storage"))
     BusyButton(stringResource(R.string.ui_25), state.working("add-storage"), { model.addRootStorage(rootPath.trim()) }, enabled = rootPath.startsWith("/"), modifier = Modifier.fillMaxWidth())
    }
   }
   HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
   BusyTextButton(stringResource(R.string.ui_26), state.working("restore-hidden"), model::restoreHidden)
  }
 }
 var renderedRemoval by remember { mutableStateOf<StorageLocation?>(null) }
 LaunchedEffect(removing) { if(removing != null) renderedRemoval = removing else { kotlinx.coroutines.delay(180); renderedRemoval = null } }
 renderedRemoval?.let { storage -> CompositionLocalProvider(LocalPopupVisible provides (removing != null)) {
  PopupConfirmDialog(stringResource(R.string.storage_remove_title), stringResource(R.string.storage_remove_body), { removing = null }, busy = state.working("storage:${storage.id}")) { model.removeStorage(storage) { removing = null } }
 } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RepositoriesScreen(state: AppState, model: AppViewModel, close: () -> Unit, scroll: androidx.compose.foundation.ScrollState = rememberScrollState(), edit: (CatalogRepository?) -> Unit) {
 var removing by remember { mutableStateOf<CatalogRepository?>(null) }
 BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.ui_28)) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   FilledTonalButton({ edit(null) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_31)) }
   if(state.repositories.isEmpty()) Text(stringResource(R.string.repository_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
   state.repositories.forEach { repo ->
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
       Icon(Icons.Rounded.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
       Text(repo.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
       Switch(repo.enabled, { model.repository(repo.copy(enabled = it)) }, enabled = !state.working("repository:${repo.id}"))
      }
      Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       TextButton({ edit(repo) }, enabled = !state.working("repository:${repo.id}")) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_29)) }
       TextButton({ removing = repo }, enabled = !state.working("repository:${repo.id}")) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ui_30)) }
      }
     }
    }
   }
  }
 }
 var renderedRemoval by remember { mutableStateOf<CatalogRepository?>(null) }
 LaunchedEffect(removing) { if(removing != null) renderedRemoval = removing else { kotlinx.coroutines.delay(180); renderedRemoval = null } }
 renderedRemoval?.let { repo -> CompositionLocalProvider(LocalPopupVisible provides (removing != null)) {
  PopupConfirmDialog(stringResource(R.string.repository_remove_title), stringResource(R.string.repository_remove_body, repo.title), { removing = null }, busy = state.working("repository:${repo.id}")) { model.deleteRepository(repo.id) { removing = null } }
 } }
}
