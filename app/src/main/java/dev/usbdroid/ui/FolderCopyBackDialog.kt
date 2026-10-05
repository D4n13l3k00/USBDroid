package dev.usbdroid.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import dev.usbdroid.files.CopyBackPlan

@Composable fun FolderCopyBackDialog(plan: CopyBackPlan, busy: Boolean, close: () -> Unit, apply: (Boolean) -> Unit) {
 var replace by remember(plan) { mutableStateOf(false) }
 val files = plan.items.filter { !it.directory || it.blocked || it.targetHash == null }
 ImagePopup({ if(!busy) close() }, { Text(stringResource(R.string.folder_copyback)) }, {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Text(stringResource(R.string.folder_copyback_hint))
   if(files.isEmpty()) Text(stringResource(R.string.folder_unchanged))
   LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(files, key = { it.relative }) { item ->
    Column {
    Text(item.relative, style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(when { item.blocked -> R.string.folder_conflict_type; item.targetHash != null -> R.string.folder_conflict_replace; item.directory -> R.string.folder_copyback_new_directory; else -> R.string.folder_copyback_new }), style = MaterialTheme.typography.bodySmall, color = if(item.targetHash != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
   }
   } }
   if(files.any { !it.blocked && it.targetHash != null }) Row {
    Checkbox(replace, { replace = it }, enabled = !busy)
    Text(stringResource(R.string.folder_replace_confirm), Modifier.padding(top = 12.dp))
   }
  }
 }, { BusyButton(stringResource(R.string.folder_copyback_apply), busy, { apply(replace) }, enabled = files.any { !it.blocked && (it.targetHash == null || replace) }) }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}
