package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.*
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SelectionActions(images: List<ImageEntry>, state: AppState, favorite: () -> Unit, hide: () -> Unit, eject: () -> Unit, delete: () -> Unit = {}) {
 val connected = images.any { image -> state.usb.luns.any { it.file.isNotBlank() && it.file == image.physicalPath } }
 val busy = images.any { state.imageWorking(it.id) } || state.working("bulk-hide") || state.usbWorking()
 Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
   TextButton(favorite, enabled = images.isNotEmpty() && !state.working("favorite")) { Icon(Icons.Rounded.Star, null); Spacer(Modifier.width(6.dp)); Text(stringResource(if(images.isNotEmpty() && images.all { it.id in state.preferences.favorites }) R.string.favorite_remove else R.string.favorite_add)) }
   BusyTextButton(stringResource(R.string.selection_delete), state.working("bulk-delete"), delete, enabled = images.isNotEmpty() && !connected && !busy)
   BusyTextButton(stringResource(R.string.selection_hide), state.working("bulk-hide"), hide, enabled = images.isNotEmpty() && !connected && !busy)
   BusyTextButton(stringResource(R.string.selection_eject), state.usbWorking(), eject, enabled = connected && !busy)
  }
  if(connected) Text(stringResource(R.string.host_eject_first), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  if(images.isEmpty()) Text(stringResource(R.string.selection_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
 }
}

@Composable fun DownloadReleaseRow(release: Release, state: AppState, model: AppViewModel) {
 val previous = state.jobs.firstOrNull { it.kind == "DOWNLOAD" && runCatching { JSONObject(it.args).optString("url") == release.url }.getOrDefault(false) && it.state != "CANCELLED" }
 val present = state.images.any { it.id == previous?.result || java.io.File(it.location).name.equals(downloadFilename(release), true) }
 val queued = previous?.state in listOf("RUNNING", "QUEUED") || state.working("download:${release.url}")
 ListItem(headlineContent = { Text(release.name) }, supportingContent = { Column {
  Text("${release.version} ${release.arch}".trim() + " · " + if(release.size > 0) sizeText(release.size) else stringResource(R.string.size_unknown))
  if(present) Text(stringResource(R.string.download_present), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
 } }, trailingContent = {
  BusyIconButton(queued, { if(previous?.state in listOf("PAUSED", "FAILED")) model.resume(previous!!.id) else model.download(release) }, enabled = !state.working("job:${previous?.id}")) {
   Icon(if(previous?.state == "PAUSED") Icons.Rounded.PlayArrow else if(previous?.state == "FAILED") Icons.Rounded.Refresh else if(present) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
    stringResource(if(previous?.state == "PAUSED") R.string.download_resume else if(previous?.state == "FAILED") R.string.download_retry else if(present) R.string.download_again else R.string.ui_18))
  }
 })
}

@Composable fun DeleteImagesConfirmation(images: List<ImageEntry>, busy: Boolean, close: () -> Unit, confirm: () -> Unit) {
 val context = androidx.compose.ui.platform.LocalContext.current
 ImagePopup({ if(!busy) close() }, { Text(stringResource(R.string.selection_delete)) }, {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text(stringResource(R.string.delete_files_confirmation, images.size, sizeText(images.sumOf { it.size })))
   if(images.any { !it.isAppFile(context) }) Text(stringResource(R.string.delete_external_warning), color = MaterialTheme.colorScheme.error)
   images.forEach { image -> ImageConfirmationDetails(image) }
  }
 }, { BusyButton(stringResource(R.string.selection_delete), busy, confirm, enabled = images.isNotEmpty()) }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.ui_10)) } })
}

@Composable fun HideImagesConfirmation(images: List<ImageEntry>, busy: Boolean, close: () -> Unit, confirm: () -> Unit) {
 val context = androidx.compose.ui.platform.LocalContext.current
 ImagePopup({ if(!busy) close() }, { Text(stringResource(R.string.selection_hide)) }, {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text(stringResource(if(images.any { it.isAppFile(context) }) R.string.hide_app_confirmation else R.string.hide_external_confirmation))
   images.forEach { ImageConfirmationDetails(it) }
  }
 }, { BusyButton(stringResource(R.string.selection_hide), busy, confirm, enabled = images.isNotEmpty()) }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun ImageConfirmationDetails(image: ImageEntry) {
 val context = androidx.compose.ui.platform.LocalContext.current
 Column {
  Text(image.title, style = MaterialTheme.typography.titleSmall)
  Text("${sizeText(image.size)} · ${stringResource(R.string.image_on_disk, image.allocatedSize?.let(::sizeText) ?: stringResource(R.string.size_unknown))}", style = MaterialTheme.typography.bodySmall)
  Text(stringResource(if(image.isAppFile(context)) R.string.location_app else R.string.location_external), style = MaterialTheme.typography.bodySmall)
  Text(image.displayLocation(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
