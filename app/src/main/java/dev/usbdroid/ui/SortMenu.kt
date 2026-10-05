package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import dev.usbdroid.data.Preferences

@Composable fun SortMenu(p: Preferences, downloads: Boolean, busy: Boolean, onOpen: () -> Unit = {}, save: (Preferences) -> Unit) {
 var expanded by remember { mutableStateOf(false) }
 var jobs by rememberSaveable(downloads) { mutableStateOf(false) }
 val selected = if(!downloads) p.imageSort else if(jobs) p.jobSort else p.downloadSort
 val descending = if(!downloads) p.imageDescending else if(jobs) p.jobDescending else p.downloadDescending
 val choices = if(!downloads) listOf("name", "size", "date") else if(jobs) listOf("date", "name", "size", "progress", "state") else listOf("name", "version", "arch", "size")
 fun pick(value: String) { save(if(!downloads) p.copy(imageSort = value) else if(jobs) p.copy(jobSort = value) else p.copy(downloadSort = value)) }
 fun order(value: Boolean) { save(if(!downloads) p.copy(imageDescending = value) else if(jobs) p.copy(jobDescending = value) else p.copy(downloadDescending = value)) }
 Box {
  IconButton(onClick = { onOpen(); expanded = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort_title)) }
  DropdownMenu(expanded, { expanded = false }, modifier = Modifier.widthIn(min = 240.dp, max = 320.dp), shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
   if(downloads) Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(!jobs, { jobs = false }, label = { Text(stringResource(R.string.sort_catalog)) })
    FilterChip(jobs, { jobs = true }, label = { Text(stringResource(R.string.ui_70)) })
   }
   choices.forEach { criterion -> DropdownMenuItem(text = { Text(sortLabel(criterion)) }, leadingIcon = { RadioButton(selected == criterion, null) }, onClick = { pick(criterion) }) }
   HorizontalDivider()
   DropdownMenuItem(text = { Text(stringResource(R.string.sort_ascending)) }, leadingIcon = { RadioButton(!descending, null) }, onClick = { order(false) })
   DropdownMenuItem(text = { Text(stringResource(R.string.sort_descending)) }, leadingIcon = { RadioButton(descending, null) }, onClick = { order(true) })
  }
 }
}
@Composable fun sortLabel(value: String): String = stringResource(when(value) { "size" -> R.string.ui_3; "date" -> R.string.ui_77; "version" -> R.string.sort_version; "arch" -> R.string.sort_arch; "progress" -> R.string.sort_progress; "state" -> R.string.sort_state; else -> R.string.ui_1 })
