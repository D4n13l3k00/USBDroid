package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import dev.usbdroid.data.StorageLocation
import dev.usbdroid.data.ImageFilesystem
import dev.usbdroid.data.minimumWorkingSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun CreateImageScreen(storage: List<StorageLocation>, back: () -> Unit, create: (String, Long, String, Boolean, String?) -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, progress: @Composable () -> Unit = {}) {
 var name by rememberSaveable { mutableStateOf("") }; var quantity by rememberSaveable { mutableStateOf("128") }
 var gib by rememberSaveable { mutableStateOf(false) }; var filesystem by rememberSaveable { mutableStateOf("FAT") }; var allocate by rememberSaveable { mutableStateOf(false) }
 var destination by rememberSaveable { mutableStateOf<String?>(null) }; var menu by remember { mutableStateOf(false) }
 val context = LocalContext.current
 var free by remember { mutableLongStateOf(0L) }
 LaunchedEffect(Unit) { free = withContext(Dispatchers.IO) { File(context.getExternalFilesDir(null) ?: context.filesDir, "images").usableSpace } }
 val mib = quantity.toLongOrNull()?.let { runCatching { Math.multiplyExact(it, if(gib) 1024L else 1L) }.getOrNull() }
 val filename = if(name.trim().endsWith(".img", true)) name.trim() else name.trim() + ".img"
 val validName = name.isNotBlank() && name.none { it == '/' || it == '\\' || it == '\u0000' || it == '\n' }
 val format = ImageFilesystem.valueOf(filesystem)
 val validSize = mib != null && mib in format.minMiB..format.maxMiB
 val requiredSpace = if(allocate) (mib ?: 0) * 1048576 else format.minimumWorkingSpace
 val enoughSpace = free >= requiredSpace
 val selected = storage.find { it.id == destination } ?: storage.firstOrNull { it.primary } ?: storage.firstOrNull()
 RetainedPopup(menu.takeIf { it }) {
  ImagePopup({ menu = false }, { Text(stringResource(R.string.create_destination)) }, {
   storage.forEach { location -> ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), headlineContent = { Text(location.title) }, supportingContent = { Text(location.location) }, leadingContent = { RadioButton(selected?.id == location.id, null, enabled = !busy) }, modifier = Modifier.clickable(enabled = !busy) { destination = location.id; menu = false }) }
  }, { TextButton(onClick = { menu = false }) { Text(stringResource(R.string.ui_67)) } })
 }
 BackHandler(onBack = back)
 Scaffold(modifier.fillMaxSize(), topBar = { TopAppBar(title = { Text(stringResource(R.string.create_title)) }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }, bottomBar = { Surface { BusyButton(stringResource(R.string.create_button), busy, { create(filename, mib!!, filesystem, allocate, selected?.id) }, enabled = validName && validSize && enoughSpace && selected != null, modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) } }) { padding ->
  Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
   Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    progress()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { Icon(Icons.Rounded.Storage, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary); Column { Text(stringResource(R.string.create_heading), style = MaterialTheme.typography.titleLarge); Text(stringResource(R.string.create_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.create_filename)) }, suffix = { if(!name.endsWith(".img", true)) Text(".img") }, singleLine = true, enabled = !busy, isError = name.isNotBlank() && !validName, supportingText = { if(name.isNotBlank() && !validName) Text(stringResource(R.string.create_invalid_name)) })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.create_size)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy, isError = !validSize, supportingText = { Text(stringResource(R.string.filesystem_size_limits, format.minMiB, format.maxMiB)) }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!gib, { gib = false }, label = { Text("MiB") }, enabled = !busy); FilterChip(gib, { gib = true }, label = { Text("GiB") }, enabled = !busy) } }
    Column { Text(stringResource(R.string.create_destination), style = MaterialTheme.typography.titleMedium); OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(8.dp)); Text(selected?.title.orEmpty(), Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null) }; Text(selected?.location.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
     Text(stringResource(R.string.filesystem_label), style = MaterialTheme.typography.titleMedium)
     FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ImageFilesystem.entries.forEach { item -> FilterChip(filesystem == item.name, { filesystem = item.name }, enabled = !busy, label = { Text(if(item == ImageFilesystem.NONE) stringResource(R.string.filesystem_empty) else item.label) }) } }
    }
    Text(stringResource(when(format) { ImageFilesystem.NONE -> R.string.format_none_hint; ImageFilesystem.FAT -> R.string.format_fat_hint; ImageFilesystem.FAT32 -> R.string.format_fat32_hint; ImageFilesystem.EXFAT -> R.string.format_exfat_hint; ImageFilesystem.EXT4 -> R.string.format_ext4_hint; ImageFilesystem.NTFS -> R.string.format_ntfs_hint; ImageFilesystem.BTRFS -> R.string.format_btrfs_hint }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Toggle(stringResource(R.string.create_allocate), stringResource(R.string.create_allocate_detail), allocate, enabled = !busy) { allocate = it }
    Text(stringResource(if(allocate) R.string.format_space_full else R.string.format_metadata_space, sizeText(requiredSpace)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if(!allocate) Text(stringResource(R.string.format_space_sparse), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.create_free, sizeText(free)), style = MaterialTheme.typography.bodyMedium, color = if(enoughSpace) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
   }
  }
 }
}
