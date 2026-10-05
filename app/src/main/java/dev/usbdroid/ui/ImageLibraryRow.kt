package dev.usbdroid.ui

import androidx.compose.foundation.*
import androidx.compose.ui.semantics.*
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.ImageEntry
import dev.usbdroid.data.isAppFile
import dev.usbdroid.data.displayLocation
import dev.usbdroid.usb.*

@OptIn(ExperimentalFoundationApi::class)
@Composable fun ImageLibraryRow(image: ImageEntry, state: AppState, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, host: (Lun, HostMode, () -> Unit) -> Unit, eject: () -> Unit, action: (Int) -> Unit, recalculate: (String) -> Unit, selectionMode: Boolean = false, selected: Boolean = false, select: () -> Unit = {}, favorite: Boolean = false, model: AppViewModel? = null) {
 var copyBackPlan by remember(image.id) { mutableStateOf<dev.usbdroid.files.CopyBackPlan?>(null) }
 var actionsMenu by remember(image.id) { mutableStateOf(false) }
 var selectedPath by remember(image.id) { mutableStateOf("") }
 val local = state.localImages.firstOrNull { it.image == image.physicalPath }
 val connected = state.usb.luns.filter { it.file == image.physicalPath }
 val lun = state.usb.luns.find { it.path == selectedPath } ?: connected.firstOrNull() ?: state.usb.luns.firstOrNull { it.file.isBlank() } ?: state.usb.luns.firstOrNull()
 val busy = state.imageWorking(image.id) || connected.any { state.usbWorking(it.path) } || lun?.let { state.usbWorking(it.path) } == true
 val clipboard = LocalClipboardManager.current
 val context = LocalContext.current
 fun openMount() { if(selectionMode) { select(); return }; if(image.physicalPath == null) action(0) else { actionsMenu = false; onExpandedChange(!expanded) } }
 Surface(modifier.padding(horizontal = 12.dp, vertical = 5.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = if(selected || connected.isNotEmpty() || local != null) BorderStroke(if(selected) 2.dp else 1.dp, MaterialTheme.colorScheme.primary) else null) {
 Column {
  Row(Modifier.fillMaxWidth().combinedClickable(onClick = ::openMount, onLongClick = { actionsMenu = false; onExpandedChange(false); select() }, onLongClickLabel = stringResource(R.string.selection_start)).semantics { if(selectionMode) { this.selected = selected; stateDescription = context.getString(if(selected) R.string.image_selected else R.string.image_not_selected) } }.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
   if(selectionMode) Checkbox(selected, null) else Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f)) {
    Row(verticalAlignment = Alignment.CenterVertically) { if(favorite) { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }; Text(image.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge) }
    Text("${image.file.extension.uppercase()} · ${sizeText(image.size)} · ${stringResource(R.string.image_on_disk, image.allocatedSize?.let(::sizeText) ?: stringResource(R.string.size_unknown))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(if(image.isAppFile(context)) R.string.location_app else R.string.location_external), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if(local != null) stringResource(R.string.mount_phone_mode, stringResource(if(local.readOnly) R.string.folder_read_only else R.string.folder_read_write)) else if(connected.isEmpty()) stringResource(R.string.image_disconnected) else stringResource(R.string.mount_computer_mode, connected.joinToString { if(it.cdrom) "CD-ROM" else context.getString(if(it.readOnly) R.string.folder_read_only else R.string.folder_read_write) }), style = MaterialTheme.typography.bodyMedium, color = if(connected.isEmpty() && local == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
   }
   if(!selectionMode) Box {
    IconButton(onClick = { onExpandedChange(false); actionsMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.ui_80)) }
    DropdownMenu(actionsMenu, { actionsMenu = false }, modifier = Modifier.widthIn(min = 260.dp, max = 340.dp), shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
     Column(Modifier.padding(16.dp)) { Text(stringResource(if(image.isAppFile(context)) R.string.location_app else R.string.location_external), style = MaterialTheme.typography.labelLarge); androidx.compose.foundation.text.selection.SelectionContainer { Text(image.displayLocation(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
     Text(stringResource(R.string.checksum_gestures), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     listOf("SHA-256", "SHA-1", "MD5").forEach { algorithm ->
      val checksum = latestChecksum(state.jobs, image, algorithm)
      val calculating = state.working("checksum:${image.id}:$algorithm")
      Row(Modifier.fillMaxWidth().combinedClickable(enabled = !calculating, onClickLabel = stringResource(R.string.checksum_copy), onLongClickLabel = stringResource(R.string.checksum_recalculate), onClick = {
       if(checksum?.state == "DONE") clipboard.setText(AnnotatedString(checksum.result.substringAfter('\n'))) else android.widget.Toast.makeText(context, R.string.checksum_hold, android.widget.Toast.LENGTH_SHORT).show()
      }, onLongClick = { if(connected.isEmpty() && local == null) recalculate(algorithm) else android.widget.Toast.makeText(context, R.string.checksum_eject, android.widget.Toast.LENGTH_SHORT).show() }).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
       if(calculating) BusyGlyph() else Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
       Column { Text(algorithm); Text(if(checksum?.state == "DONE") checksum.result.substringAfter('\n').take(16) + "…" else stringResource(if(calculating) R.string.checksum_calculating else R.string.checksum_hold), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
     }
     DropdownMenuItem(text = { Text(stringResource(if(favorite) R.string.favorite_remove else R.string.favorite_add)) }, leadingIcon = { Icon(if(favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) }, onClick = { actionsMenu = false; action(12) })
     HorizontalDivider()
     val labels = listOf(R.string.ui_115, R.string.ui_122, R.string.ui_117, R.string.ui_2, R.string.ui_118, R.string.ui_119, R.string.ui_120, R.string.ui_121)
     val icons = listOf(Icons.Rounded.VisibilityOff, Icons.Rounded.DeleteOutline, Icons.Rounded.Edit, Icons.Rounded.AspectRatio, Icons.Rounded.FolderOpen, Icons.Rounded.FileUpload, Icons.Rounded.Transform, Icons.Rounded.ContentCopy)
     labels.forEachIndexed { index, label ->
      val number = index + 4
      val allowed = !busy && (local == null || number !in setOf(4, 5, 7, 8, 9, 10, 11)) && (connected.isEmpty() || number !in setOf(4, 5, 7, 8, 10)) && (connected.none { !it.readOnly } || number !in setOf(9, 11))
      DropdownMenuItem(text = { Column { Text(stringResource(label), color = if(number == 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface); if(number == 4) Text(stringResource(R.string.hide_files_remain), style = MaterialTheme.typography.bodySmall) } }, leadingIcon = { Icon(icons[index], null) }, enabled = allowed, onClick = { actionsMenu = false; action(number) })
     }
    }
   }
  }
  AnimatedVisibility(expanded && !selectionMode, enter = cardExpand, exit = cardCollapse) { Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
   if(image.id == state.temporaryImageId && model != null) BusyButton(stringResource(R.string.folder_copyback), state.working("folder-preview:${image.id}") || state.working("folder-copyback:${image.id}"), { model.previewFolderCopyBack(image) { copyBackPlan = it } }, Modifier.fillMaxWidth(), enabled = local == null && connected.isEmpty() && !busy)
   if(local != null) {
    if(model != null) LocalMountActions(image, local, state, model) { action(13) }
   } else if(connected.isEmpty()) {
   if(model != null) LocalMountActions(image, null, state, model) { action(13) }
   if(state.usb.luns.isNotEmpty()) {
    Text(stringResource(R.string.usb_host_selection), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if(state.usb.luns.size == 1) Text(state.usb.luns.single().title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else state.usb.luns.forEach { item -> DropdownMenuItem(text = { Column { Text(item.title); if(item.file.isNotBlank()) Text(state.images.find { it.physicalPath == item.file }?.title ?: java.io.File(item.file).name, style = MaterialTheme.typography.bodySmall) } }, leadingIcon = { RadioButton(lun?.path == item.path, null) }, enabled = !busy && !state.rootBusy && item.file.isBlank(), onClick = { selectedPath = item.path }) }
   }
   if(lun == null || lun.file.isNotBlank()) Text(stringResource(if(lun == null) R.string.host_missing_reason else R.string.host_busy_reason), Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
   fun available(mode: HostMode) = state.folderShare == null && lun != null && lun.file.isBlank() && (!mode.cd || lun.supportsCdrom) && (lun.supportsReadOnly || mode.ro == lun.readOnly)
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(HostMode.READ_ONLY, HostMode.WRITABLE).forEach { mode ->
     CardAction(stringResource(if(mode.ro) R.string.folder_read_only else R.string.folder_read_write), if(mode.ro) Icons.Rounded.Lock else Icons.Rounded.Edit, { lun?.let { host(it, mode) { onExpandedChange(false) } } }, Modifier.weight(1f), enabled = available(mode) && !busy && !state.rootBusy, accented = mode.name == state.preferences.lastHostMode)
    }
   }
   CardAction(hostModeText(HostMode.CDROM), Icons.Rounded.Album, { lun?.let { host(it, HostMode.CDROM) { onExpandedChange(false) } } }, Modifier.fillMaxWidth(), enabled = available(HostMode.CDROM) && !busy && !state.rootBusy, accented = HostMode.CDROM.name == state.preferences.lastHostMode)
   if(!state.rootBusy && lun != null && lun.file.isBlank()) {
    val reason = when { state.folderShare != null -> R.string.folder_mtp_active; !lun.supportsCdrom -> R.string.host_cd_reason; !lun.supportsReadOnly -> R.string.host_ro_reason; else -> null }
    if(reason != null) Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
   } else {
    connected.forEach { item -> Text("${item.title} · ${hostModeText(if(item.cdrom) HostMode.CDROM else if(item.readOnly) HostMode.READ_ONLY else HostMode.WRITABLE)}", style = MaterialTheme.typography.bodyMedium) }
    CardAction(stringResource(R.string.ui_90), Icons.Rounded.Eject, { onExpandedChange(false); eject() }, Modifier.fillMaxWidth(), enabled = !busy)
   }
  } }
 }
 }
 RetainedPopup(copyBackPlan) { shown -> FolderCopyBackDialog(shown, state.working("folder-copyback:${image.id}"), { copyBackPlan = null }) { replace -> model?.applyFolderCopyBack(shown, replace) { copyBackPlan = null } } }

}
