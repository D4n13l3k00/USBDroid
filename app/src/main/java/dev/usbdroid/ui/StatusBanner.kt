package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import org.json.JSONObject

@Composable fun StatusBanner(state: AppState, openUpdate: () -> Unit = {}, cancel: (String) -> Unit = {}) {
 val hosted = state.usb.luns.filter { it.file.isNotBlank() }
 val active = state.jobs.filter { it.running }
 val immediate = state.operations.filter { key -> key.substringBefore(':') in setOf("inspect", "prepare", "host", "eject", "eject-all", "eject-image", "create", "setup-create", "remove", "bulk-hide", "bulk-delete", "refresh", "restore-hidden", "usb-mode", "local-mount", "local-unmount", "image-file", "folder-mtp", "folder-mtp-stop", "folder-discard", "folder-estimate", "folder-preview", "recovery") }.filterNot { key -> active.any { it.operationKey() == key } }
 Surface(Modifier.fillMaxWidth().animateContentSize(tween(220)).padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color.Transparent) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Icon(Icons.Rounded.Usb, null, tint = if(hosted.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
    Column {
     Text(when { hosted.isNotEmpty() -> stringResource(R.string.usb_hosted_count, hosted.size); state.folderShare != null -> stringResource(R.string.folder_mtp_active); state.localImages.isNotEmpty() -> stringResource(R.string.local_mounted); else -> stringResource(R.string.usb_idle) }, style = MaterialTheme.typography.titleSmall)
     hosted.forEach { lun -> Text("${state.images.find { it.physicalPath == lun.file }?.title ?: java.io.File(lun.file).name} · ${hostModeText(if(lun.cdrom) dev.usbdroid.usb.HostMode.CDROM else if(lun.readOnly) dev.usbdroid.usb.HostMode.READ_ONLY else dev.usbdroid.usb.HostMode.WRITABLE)}", style = MaterialTheme.typography.bodySmall) }
    }
   }
   state.folderShare?.let { share -> Text(java.io.File(share.folder).name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
   state.localImages.forEach { image -> Text("${image.title} · ${stringResource(R.string.mount_phone_mode, stringResource(if(image.readOnly) R.string.folder_read_only else R.string.folder_read_write))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
   immediate.forEach { key -> LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(operationLabel(key)), style = MaterialTheme.typography.bodySmall) }
   active.forEach { job ->
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
     Text("${jobPhase(job)} · ${job.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
     val committing = runCatching { JSONObject(job.args).optString("_phase") == "saving" }.getOrDefault(false)
     BusyIconButton(state.working("job:${job.id}"), { cancel(job.id) }, enabled = !committing) { Icon(Icons.Rounded.Close, stringResource(R.string.ui_131)) }
    }
    JobProgress(job)
   }
   state.notice?.let { notice -> Text(stringResource(R.string.operation_complete, notice.title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
   UpdateBannerRow(openUpdate)

  }
 }
}
private fun jobLabel(kind: String) = when(kind) {
 "CREATE" -> R.string.operation_create
 "DOWNLOAD" -> R.string.operation_download
 "RESIZE" -> R.string.operation_resize
 "HYBRID" -> R.string.operation_hybrid
 "EXPORT" -> R.string.operation_export
 else -> R.string.operation_copy
}
