package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.usbdroid.AppState
import dev.usbdroid.AppViewModel
import dev.usbdroid.R
import dev.usbdroid.working

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UsbSettingsScreen(state: AppState, model: AppViewModel, close: () -> Unit) {
 var system by rememberSaveable { mutableStateOf(state.preferences.usbSystem) }
 var mode by rememberSaveable { mutableStateOf(state.preferences.usbMode) }
 var automatic by rememberSaveable { mutableStateOf(state.preferences.autoUsb) }
 var permanent by rememberSaveable { mutableStateOf(false) }
 var apply by remember { mutableStateOf(false) }
 var renderApply by remember { mutableStateOf(false) }
 val saving = state.working("settings")
 val applying = state.working("usb-mode")
 val enabled = !saving && !applying
 val changed = system != state.preferences.usbSystem || mode != state.preferences.usbMode || automatic != state.preferences.autoUsb
 LaunchedEffect(apply) { if(apply) renderApply = true else { kotlinx.coroutines.delay(180); renderApply = false } }
 BackHandler(onBack = close)
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.usb_advanced)) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }, bottomBar = {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
   Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    TextButton(close, Modifier.weight(1f), enabled = enabled) { Text(stringResource(R.string.ui_67)) }
    BusyButton(stringResource(R.string.ui_39), saving, { model.settings(state.preferences.copy(usbSystem = system, usbMode = mode, autoUsb = automatic), close) }, Modifier.weight(1f), enabled = changed && !applying)
   }
  }
 }) { padding ->
  Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
   Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     ListItem(headlineContent = { Text(stringResource(R.string.ui_98)) }, supportingContent = { Text(stringResource(R.string.ui_99)) }, leadingContent = { Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(automatic, { automatic = it }, enabled = enabled) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     Column(Modifier.padding(vertical = 12.dp)) {
      UsbSectionHeading(stringResource(R.string.ui_33), Icons.Rounded.SettingsInputComponent)
      listOf("auto" to stringResource(R.string.setup_auto), "configfs" to "ConfigFS", "setprop" to "Android setprop", "functions" to "Android functions", "samsung" to "Samsung UMS").forEach { (id, title) -> UsbRadioRow(title, system == id, enabled) { system = id } }
     }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     Column(Modifier.padding(vertical = 12.dp)) {
      UsbSectionHeading(stringResource(R.string.ui_35), Icons.Rounded.Usb)
      listOf("mass_storage" to R.string.usb_mode_mass_storage, "none" to R.string.usb_mode_none).forEach { (id, title) -> UsbRadioRow(stringResource(title), mode == id, enabled) { mode = id } }
     }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(stringResource(R.string.usb_manual_apply), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
     ListItem(headlineContent = { Text(stringResource(R.string.ui_36)) }, supportingContent = { Text(stringResource(R.string.ui_37)) }, leadingContent = { Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(permanent, { permanent = it }, enabled = enabled) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
    }
    BusyButton(stringResource(R.string.ui_38), applying, { apply = true }, Modifier.fillMaxWidth(), enabled = !saving)
   }
  }
 }
 if(renderApply) CompositionLocalProvider(LocalPopupVisible provides apply) {
  PopupConfirmDialog(stringResource(R.string.ui_40), stringResource(R.string.ui_41), { apply = false }, busy = applying) { model.mode(mode, permanent) { apply = false } }
 }
}

@Composable private fun UsbSectionHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
 Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
  Text(title, style = MaterialTheme.typography.titleMedium)
 }
}

@Composable private fun UsbRadioRow(title: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
 Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = choose).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  RadioButton(selected, null, enabled = enabled)
  Text(title, style = MaterialTheme.typography.bodyLarge)
 }
}
