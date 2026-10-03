package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import dev.usbdroid.running
import dev.usbdroid.data.TransferJob
import dev.usbdroid.data.ImageEntry

fun latestChecksum(jobs: List<TransferJob>, image: ImageEntry, algorithm: String): TransferJob? = jobs.filter { it.kind == "CHECKSUM" && it.createdAt >= image.modified && runCatching { val args = org.json.JSONObject(it.args); args.optString("image") == image.id && args.optString("algorithm") == algorithm }.getOrDefault(false) }.maxByOrNull { it.createdAt }

/** A checksum belongs to its image and algorithm, without transfer controls or task history. */
@Composable fun ChecksumResult(job: TransferJob) {
 val clipboard = LocalClipboardManager.current
 Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
  when {
   job.running -> {
    if(job.total > 0) LinearProgressIndicator(progress = { (job.progress.toFloat() / job.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(stringResource(R.string.checksum_calculating), style = MaterialTheme.typography.bodySmall)
   }
   job.state == "DONE" -> {
    val hash = job.result.substringAfter('\n')
    SelectionContainer { Text(hash, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
   }
   job.state == "FAILED" -> Text(job.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  }
 }
}
