package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R

@Composable fun BusyGlyph() {
 val description = stringResource(R.string.operation_in_progress)
 CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = description }, strokeWidth = 2.dp)
}
@Composable fun BusyButton(label: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
 Button(onClick, modifier, enabled = enabled && !busy) { if(busy) { BusyGlyph(); Spacer(Modifier.width(10.dp)) }; Text(label) }
}
@Composable fun BusyTextButton(label: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
 TextButton(onClick, modifier, enabled = enabled && !busy) { if(busy) { BusyGlyph(); Spacer(Modifier.width(10.dp)) }; Text(label) }
}
@Composable fun BusyIconButton(busy: Boolean, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
 IconButton(onClick, enabled = enabled && !busy) { if(busy) BusyGlyph() else content() }
}
@Composable fun OperationProgress(state: AppState) {
 val keys = state.operations.filterNot { key -> state.jobs.any { it.running && it.operationKey() == key } }
 AnimatedVisibility(keys.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) { Surface(color = androidx.compose.ui.graphics.Color.Transparent) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   LinearProgressIndicator(Modifier.fillMaxWidth())
   keys.distinctBy { it.substringBefore(':') }.forEach { key -> Text(stringResource(operationLabel(key)), style = MaterialTheme.typography.bodySmall) }
  }
 }
 }
}
internal fun operationLabel(key: String): Int = when(key.substringBefore(':')) {
 "inspect" -> R.string.operation_inspect
 "prepare" -> R.string.operation_prepare
 "host" -> R.string.operation_host
 "eject", "eject-all", "eject-image" -> R.string.operation_eject
 "catalog" -> R.string.operation_catalog
 "refresh", "restore-hidden" -> R.string.operation_refresh
 "create", "setup-create" -> R.string.operation_create
 "settings", "setup", "repository", "storage", "storage-primary", "add-storage" -> R.string.operation_save
 "setup-finish", "usb-mode" -> R.string.operation_usb
 "report" -> R.string.operation_report
 "remove", "bulk-hide", "bulk-delete" -> R.string.operation_remove
 else -> R.string.operation_in_progress
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ScreenPanel(onDismissRequest: () -> Unit, title: @Composable () -> Unit, text: @Composable () -> Unit, confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit = {}, progress: @Composable () -> Unit = {}) {
 var shown by remember { mutableStateOf(false) }
 LaunchedEffect(Unit) { shown = true }
 val entrance by animateFloatAsState(if(shown) 1f else 0f, tween(180), label = "screen-entrance")
 BackHandler(onBack = onDismissRequest)
 Scaffold(Modifier.fillMaxSize(), topBar = { TopAppBar(title = title, navigationIcon = { IconButton(onDismissRequest) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }, bottomBar = {
  Surface(color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)) { Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { dismissButton(); Spacer(Modifier.width(12.dp)); confirmButton() } }
 }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().graphicsLayer { alpha = entrance; translationY = (1f - entrance) * 24.dp.toPx() }) {
   progress()
   Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) { text() } }
  }
 }
}
