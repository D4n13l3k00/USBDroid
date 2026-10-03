package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import dev.usbdroid.data.TransferJob
import org.json.JSONObject
import java.util.Locale

fun byteText(bytes: Long): String = when {
 bytes >= 1073741824 -> String.format(Locale.US, "%.1f GiB", bytes / 1073741824.0)
 bytes >= 1048576 -> String.format(Locale.US, "%.1f MiB", bytes / 1048576.0)
 bytes >= 1024 -> String.format(Locale.US, "%.0f KiB", bytes / 1024.0)
 else -> "$bytes B"
}
@Composable fun jobPhase(job: TransferJob): String {
 val phase = runCatching { JSONObject(job.args).optString("_phase") }.getOrDefault("")
 return stringResource(when {
  phase == "hashing" || job.kind == "CHECKSUM" -> R.string.checksum_calculating
  phase == "formatting" -> R.string.filesystem_formatting
  phase == "saving" -> R.string.phase_saving
  phase == "hybrid" -> R.string.phase_hybrid
  job.kind == "DOWNLOAD" -> R.string.phase_download
  job.kind == "CREATE" && runCatching { JSONObject(job.args).optBoolean("allocate") }.getOrDefault(false) -> R.string.phase_allocate
  job.kind == "CREATE" -> R.string.operation_create
  job.kind == "RESIZE" -> R.string.operation_resize
  job.kind == "HYBRID" -> R.string.operation_hybrid
  job.kind == "EXPORT" -> R.string.operation_export
  else -> R.string.operation_copy
 })
}
@Composable fun JobProgress(job: TransferJob, modifier: Modifier = Modifier) {
 val args = runCatching { JSONObject(job.args) }.getOrNull()
 val speed = args?.optLong("_speed", 0) ?: 0
 val eta = args?.optLong("_eta", -1) ?: -1
 Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
  if(job.total > 0) LinearProgressIndicator(progress = { (job.progress.toFloat() / job.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
  if(job.total > 0) { Text("${(job.progress * 100 / job.total).coerceIn(0, 100)}%", style = MaterialTheme.typography.labelMedium); Text("${byteText(job.progress)} / ${byteText(job.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
  if(speed > 0 && job.total > 0 && job.progress < job.total) {
   Text(stringResource(R.string.speed_value, byteText(speed)) + if(eta >= 0) " · " + when { eta >= 3600 -> stringResource(R.string.eta_hours, (eta + 3599) / 3600); eta >= 60 -> stringResource(R.string.eta_minutes, (eta + 59) / 60); else -> stringResource(R.string.eta_seconds, eta) } else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
