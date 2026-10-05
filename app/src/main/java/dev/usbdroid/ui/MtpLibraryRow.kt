package dev.usbdroid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.ImageEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MtpLibraryRow(entry: ImageEntry, state: AppState, model: AppViewModel, expanded: Boolean, expand: (Boolean) -> Unit, modifier: Modifier, rename: () -> Unit) {
 var menu by remember(entry.id) { mutableStateOf(false) }
 var remove by remember(entry.id) { mutableStateOf(false) }
 val connected = state.folderShare?.folder == entry.physicalPath
 val busy = state.working("folder-mtp") || state.working("folder-mtp-stop") || state.working("remove:${entry.id}")
 Surface(modifier.padding(horizontal = 12.dp, vertical = 5.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = if(connected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
  Column {
   Row(Modifier.fillMaxWidth().clickable { expand(!expanded) }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
     Row(verticalAlignment = Alignment.CenterVertically) {
      if(entry.id in state.preferences.favorites) { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
      Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
     }
     Text(if(connected) "MTP · ${stringResource(if(entry.mtpReadOnly) R.string.folder_read_only else R.string.folder_read_write)}" else "MTP", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
     Text(stringResource(if(connected) R.string.folder_mtp_active else R.string.folder_not_connected), style = MaterialTheme.typography.bodyMedium, color = if(connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box {
     IconButton({ expand(false); menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
     DropdownMenu(menu, { menu = false }, modifier = Modifier.widthIn(min = 260.dp, max = 340.dp), shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
      DropdownMenuItem({ Text(stringResource(R.string.folder_rename)) }, { menu = false; rename() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
      DropdownMenuItem({ Text(stringResource(if(entry.id in state.preferences.favorites) R.string.favorite_remove else R.string.favorite_add)) }, { menu = false; model.favorite(setOf(entry.id), entry.id !in state.preferences.favorites) }, leadingIcon = { Icon(if(entry.id in state.preferences.favorites) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) })
      DropdownMenuItem({ Text(stringResource(R.string.folder_remove_mtp), color = MaterialTheme.colorScheme.error) }, { menu = false; remove = true }, enabled = !connected && !busy, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
     }
    }
   }
   AnimatedVisibility(expanded, enter = cardExpand, exit = cardCollapse) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
     Text(entry.physicalPath.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     if(connected) CardAction(stringResource(R.string.folder_stop), Icons.Rounded.Eject, model::stopFolderMtp, Modifier.fillMaxWidth(), enabled = !busy && !state.rootBusy)
     else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(true, false).forEach { readOnly ->
       CardAction(stringResource(if(readOnly) R.string.folder_read_only else R.string.folder_read_write), if(readOnly) Icons.Rounded.Lock else Icons.Rounded.Edit, { model.startFolderMtp(entry.physicalPath!!, readOnly, entry) }, Modifier.weight(1f), enabled = !busy && !state.rootBusy && state.folderShare == null && !state.usb.luns.any { it.file.isNotBlank() })
      }
     }
    }
   }
  }
 }
 RetainedPopup(remove.takeIf { it }) { PopupConfirmDialog(stringResource(R.string.folder_remove_mtp), stringResource(R.string.folder_remove_mtp_body), { remove = false }, busy = busy) { model.removeMtpFolder(entry) { remove = false } } }
}
