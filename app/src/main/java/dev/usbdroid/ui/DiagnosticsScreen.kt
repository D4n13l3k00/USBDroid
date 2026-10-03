package dev.usbdroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DiagnosticsScreen(state: AppState, model: AppViewModel, close: () -> Unit) {
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 val snackbar = remember { SnackbarHostState() }
 var exporting by remember { mutableStateOf(false) }
 val busy = state.working("report")
 val report = state.diagnostics
 LaunchedEffect(Unit) { if(report.isBlank()) model.report() }
 BackHandler(onBack = close)
 val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
  if(uri != null) scope.launch {
   exporting = true
   try {
    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(report) } ?: error("Cannot open destination") }
    snackbar.showSnackbar(context.getString(R.string.diagnostics_exported))
   } catch(e: CancellationException) { throw e }
   catch(_: Exception) { snackbar.showSnackbar(context.getString(R.string.diagnostics_export_failed)) }
   finally { exporting = false }
  }
 }
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.ui_107)) }, navigationIcon = { IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.create_back)) } }, actions = { BusyIconButton(busy, model::report) { Icon(Icons.Rounded.Refresh, stringResource(R.string.ui_72)) } }) }, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
   Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     OutlinedButton(onClick = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("USBDroid diagnostics", report)); scope.launch { snackbar.showSnackbar(context.getString(R.string.diagnostics_copied)) } }, modifier = Modifier.weight(1f), enabled = report.isNotBlank() && !busy) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.diagnostics_copy)) }
     BusyButton(stringResource(R.string.diagnostics_export), exporting, { export.launch("USBDroid-diagnostics.txt") }, Modifier.weight(1f), enabled = report.isNotBlank() && !busy)
    }
    TextButton(close, Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_67)) }
   }
  }
 }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.diagnostics_loading)) }
   SelectionContainer { Text(report, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
  }
 }
}
