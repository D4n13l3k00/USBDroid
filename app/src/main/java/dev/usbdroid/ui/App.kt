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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.*
import dev.usbdroid.usb.*
import java.util.Locale

fun sizeText(bytes: Long) = if(bytes >= 1073741824) String.format(Locale.US, "%.1f GiB", bytes / 1073741824.0) else String.format(Locale.US, "%.0f MiB", bytes / 1048576.0)
@Composable fun Toggle(title: String, detail: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) { ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) }, trailingContent = { Switch(checked, change, enabled = enabled) }, modifier = Modifier.clickable(enabled) { change(!checked) }) }
@Composable fun EmptyView(title: String, detail: String) { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Rounded.Storage, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge); Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@OptIn(ExperimentalFoundationApi::class)
@Composable fun MenuDialog(title: String, items: List<String>, close: () -> Unit, busy: Set<Int> = emptySet(), disabled: Set<Int> = emptySet(), longPick: Map<Int, () -> Unit> = emptyMap(), header: @Composable () -> Unit = {}, detail: @Composable (Int) -> Unit = {}, pick: (Int) -> Unit) {
 val icons = if(items.size == 12) listOf(Icons.Rounded.Usb, Icons.Rounded.Fingerprint, Icons.Rounded.Fingerprint, Icons.Rounded.Fingerprint, Icons.Rounded.VisibilityOff, Icons.Rounded.DeleteOutline, Icons.Rounded.Edit, Icons.Rounded.AspectRatio, Icons.Rounded.DriveFileMove, Icons.Rounded.FileUpload, Icons.Rounded.Transform, Icons.Rounded.ContentCopy) else listOf(Icons.Rounded.AddCircleOutline, Icons.Rounded.FileDownload, Icons.Rounded.Link, Icons.Rounded.FolderOpen)
 ImagePopup(close, { Text(title) }, { Column(Modifier.padding(horizontal = 12.dp)) { header() }; items.forEachIndexed { index, label ->
  if(items.size == 12 && index in setOf(1, 4, 6)) HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
  ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)), headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge, color = if(index in disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) else if(items.size == 12 && index == 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }, leadingContent = { Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { if(index in busy) BusyGlyph() else Icon(icons.getOrElse(index) { Icons.Rounded.ChevronRight }, null, tint = if(items.size == 12 && index == 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }, modifier = Modifier.combinedClickable(enabled = index !in busy && index !in disabled, onClickLabel = if(index in longPick) stringResource(R.string.checksum_copy) else null, onLongClickLabel = if(index in longPick) stringResource(R.string.checksum_recalculate) else null, onLongClick = longPick[index], onClick = { pick(index) }))
  detail(index)
 } }, { TextButton(close) { Text(stringResource(R.string.ui_67)) } })
}
@Composable fun HostImageDialog(image: ImageEntry, state: AppState, close: () -> Unit, host: (Lun, HostMode) -> Unit, eject: () -> Unit) {
 var selected by rememberSaveable { mutableIntStateOf(0) }; var mode by rememberSaveable { mutableStateOf(runCatching { HostMode.valueOf(state.preferences.lastHostMode) }.getOrDefault(HostMode.READ_ONLY)) }
 val lun = state.usb.luns.getOrNull(selected)
 val busy = state.usbWorking(lun?.path) || state.imageWorking(image.id)
 val connected = state.usb.luns.any { it.file == image.physicalPath }
 val valid = !connected && lun != null && lun.file.isBlank() && (!mode.cd || lun.supportsCdrom) && (lun.supportsReadOnly || mode.ro == lun.readOnly)
 ImagePopup(close, { Text(image.title) }, {
  Text(stringResource(if(connected) R.string.details_hosted else R.string.details_detached), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  if(state.usb.luns.isEmpty()) Text(state.usb.error ?: stringResource(R.string.ui_129))
  state.usb.luns.forEachIndexed { i, entry -> ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)), headlineContent = { Text(entry.title) }, supportingContent = { Text(entry.file.ifBlank { stringResource(R.string.ui_89) }) }, leadingContent = { RadioButton(selected == i, { selected = i }, enabled = !busy && !state.rootBusy) }) }
  HorizontalDivider()
  HostMode.entries.forEach { m -> ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)), headlineContent = { Text(hostModeText(m)) }, leadingContent = { RadioButton(mode == m, { mode = m }, enabled = !busy && lun != null && (!m.cd || lun.supportsCdrom) && (lun.supportsReadOnly || m.ro == lun.readOnly)) }) }
  if(connected) BusyTextButton(stringResource(R.string.ui_90), busy, eject, Modifier.fillMaxWidth())
 }, { BusyButton(stringResource(R.string.ui_114), busy || state.rootBusy, { lun?.let { host(it, mode) } }, enabled = valid && !state.rootBusy) })
}
@Composable fun JobRow(job: TransferJob, model: AppViewModel, state: AppState = model.state.value) {
 val original = runCatching { org.json.JSONObject(job.args).optString("_original") }.getOrDefault("")
 val exportOriginal = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri != null) model.exportOriginal(job, uri) }
 val busy = state.working("job:${job.id}")
 val committing = runCatching { org.json.JSONObject(job.args).optString("_phase") == "saving" }.getOrDefault(false)
 val status = if(job.running) jobPhase(job) else stringResource(when(job.state) { "PAUSED" -> R.string.job_paused; "DONE" -> R.string.job_done; "FAILED" -> R.string.job_failed; else -> R.string.job_cancelled })
 Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
   Text(job.title, style = MaterialTheme.typography.titleMedium)
   Text(status, style = MaterialTheme.typography.labelLarge, color = if(job.state == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
   if(job.running) JobProgress(job)
   else if(job.total > 0) Text("${byteText(job.progress)} / ${byteText(job.total)}", style = MaterialTheme.typography.bodySmall)
   if(job.state == "PAUSED") Text(stringResource(R.string.download_paused_hint), style = MaterialTheme.typography.bodySmall)
   if(job.state == "CANCELLED") Text(stringResource(R.string.download_cancelled_hint), style = MaterialTheme.typography.bodySmall)
   if(original.isNotBlank()) TextButton({ exportOriginal.launch(java.io.File(original).name.drop(37).ifBlank { "original.iso" }) }, enabled = !state.working("export::")) { Icon(Icons.Rounded.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.original_export)) }
   if(job.error.isNotBlank()) { if(job.state == "DONE" && job.error.startsWith("isohybrid:")) Text(stringResource(R.string.hybrid_original_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) else ErrorExplanation(job.error, compact = true) }
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { when(job.state) {
    "RUNNING", "QUEUED" -> {
     if(job.kind == "DOWNLOAD") BusyTextButton(stringResource(R.string.ui_130), busy, { model.pause(job.id) }, enabled = !committing)
     BusyTextButton(stringResource(R.string.ui_131), busy, { model.cancel(job.id) }, enabled = !committing)
    }
    "PAUSED", "FAILED" -> {
     BusyTextButton(stringResource(if(job.state == "FAILED") R.string.download_retry else R.string.download_resume), busy, { model.resume(job.id) })
     BusyIconButton(busy, { model.dismissJob(job.id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.ui_132)) }
    }
    else -> BusyIconButton(busy, { model.dismissJob(job.id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.ui_132)) }
   } }
  }
 }
}
