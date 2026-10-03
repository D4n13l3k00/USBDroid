package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.R
import dev.usbdroid.data.ImageEntry
import dev.usbdroid.data.isAppFile
import dev.usbdroid.data.displayLocation
import dev.usbdroid.usb.UsbStatus
import java.text.DateFormat
import java.util.Date

@Composable fun ImageDetailsPanel(image: ImageEntry?, usb: UsbStatus, host: () -> Unit, actions: () -> Unit, busy: Boolean = false, eject: () -> Unit = {}) {
 Surface(Modifier.width(340.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
  Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
   Icon(Icons.Rounded.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
   if(image == null) Text(stringResource(R.string.details_select), style = MaterialTheme.typography.titleLarge)
   else {
    Text(image.title, style = MaterialTheme.typography.headlineSmall)
    Text(sizeText(image.size), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.image_on_disk, image.allocatedSize?.let(::sizeText) ?: stringResource(R.string.size_unknown)), style = MaterialTheme.typography.bodySmall)
    Text(DateFormat.getDateTimeInstance().format(Date(image.modified)), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(if(image.isAppFile(androidx.compose.ui.platform.LocalContext.current)) R.string.location_app else R.string.location_external), style = MaterialTheme.typography.labelLarge)
    SelectionContainer { Text(image.displayLocation(), style = MaterialTheme.typography.bodySmall) }
    val connected = usb.luns.any { it.file == image.physicalPath }
    Text(stringResource(if(connected) R.string.details_hosted else R.string.details_detached), color = MaterialTheme.colorScheme.primary)
    BusyButton(stringResource(if(connected) R.string.ui_90 else R.string.ui_114), busy, if(connected) eject else host, Modifier.fillMaxWidth())
    OutlinedButton(onClick = actions, Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_80)) }
   }
  }
 }
}
