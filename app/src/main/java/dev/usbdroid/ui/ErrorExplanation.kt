package dev.usbdroid.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import java.util.Locale

enum class ErrorKind { ROOT, SPACE, NETWORK, HOST, DETACH, PERMISSION, IMAGE, CONFLICT, OTHER }
fun errorKind(details: String): ErrorKind {
 val text = details.lowercase(Locale.ROOT)
 return when {
  listOf("changed since", "changed during", "changed while", "original file changed").any(text::contains) -> ErrorKind.CONFLICT
  listOf("root", "su:", "superuser", "root-доступ").any(text::contains) -> ErrorKind.ROOT
  listOf("not enough", "no space", "enospc", "недостаточно", "свободного места").any(text::contains) -> ErrorKind.SPACE
  listOf("eject", "already connected", "сначала извлек", "подключён", "уже подключ").any(text::contains) -> ErrorKind.DETACH
  listOf("lun", "udc", "configfs", "usb", "режим недоступ", "host", "хост").any(text::contains) -> ErrorKind.HOST
  listOf("http", "timeout", "timed out", "resolve host", "connection", "etag", "content-range", "remote", "incomplete response", "загрузка уже").any(text::contains) -> ErrorKind.NETWORK
  listOf("permission", "eacces", "denied", "securityexception", "доступ к").any(text::contains) -> ErrorKind.PERMISSION
  listOf("mount", "filesystem", "image", "loop", "образ", "файловая система").any(text::contains) -> ErrorKind.IMAGE
  else -> ErrorKind.OTHER
 }
}
fun errorTitleResource(kind: ErrorKind): Int = when(kind) { ErrorKind.ROOT -> R.string.error_root; ErrorKind.SPACE -> R.string.error_space; ErrorKind.NETWORK -> R.string.error_network; ErrorKind.HOST -> R.string.error_host; ErrorKind.DETACH -> R.string.error_detach; ErrorKind.PERMISSION -> R.string.error_permission; ErrorKind.IMAGE -> R.string.error_image; ErrorKind.CONFLICT -> R.string.error_changed; else -> R.string.error_title }
@Composable fun ErrorExplanation(details: String, modifier: Modifier = Modifier, compact: Boolean = false, showTitle: Boolean = true, recover: ((ErrorKind) -> Unit)? = null) {
 var expanded by remember(details) { mutableStateOf(false) }
 val kind = errorKind(details)
 val title = errorTitleResource(kind)
 val hint = when(kind) { ErrorKind.ROOT -> R.string.error_root_hint; ErrorKind.SPACE -> R.string.error_space_hint; ErrorKind.NETWORK -> R.string.error_network_hint; ErrorKind.HOST -> R.string.error_host_hint; ErrorKind.DETACH -> R.string.error_detach_hint; ErrorKind.PERMISSION -> R.string.error_permission_hint; ErrorKind.IMAGE -> R.string.error_image_hint; ErrorKind.CONFLICT -> R.string.error_changed_hint; else -> R.string.error_generic_hint }
 val clipboard = LocalClipboardManager.current
 Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
  if(showTitle) Text(stringResource(title), style = if(compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
  Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  if(recover != null && kind in setOf(ErrorKind.ROOT, ErrorKind.HOST, ErrorKind.SPACE, ErrorKind.PERMISSION, ErrorKind.CONFLICT)) TextButton({ recover(kind) }) { Text(stringResource(when(kind) { ErrorKind.ROOT -> R.string.error_check_root; ErrorKind.HOST -> R.string.error_check_usb; ErrorKind.CONFLICT -> R.string.error_check_again; else -> R.string.error_storage })) }
  Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp)) { Text(stringResource(R.string.error_details), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Icon(if(expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) }
  AnimatedVisibility(expanded, enter = expandVertically(tween(160, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(100)), exit = shrinkVertically(tween(140, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(90))) {
   Column { SelectionContainer { Text(details, style = MaterialTheme.typography.bodySmall) }; TextButton({ clipboard.setText(AnnotatedString(details)) }) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.error_copy)) } }
  }
 }
}
