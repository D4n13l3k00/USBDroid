package dev.usbdroid.ui

import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import dev.usbdroid.R

@Composable fun PopupInputDialog(title: String, label: String, initial: String, close: () -> Unit, busy: Boolean = false, confirm: (String) -> Unit) {
 var value by rememberSaveable { mutableStateOf(initial) }
 ImagePopup({ if(!busy) close() }, { Text(title) }, { OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, enabled = !busy) }, { BusyButton(stringResource(R.string.ui_39), busy, { confirm(value) }, enabled = value.isNotBlank()) }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.ui_10)) } })
}
@Composable fun PopupConfirmDialog(title: String, text: String, close: () -> Unit, busy: Boolean = false, confirm: () -> Unit) {
 ImagePopup({ if(!busy) close() }, { Text(title) }, { Text(text) }, { BusyButton(stringResource(R.string.ui_45), busy, confirm) }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.ui_10)) } })
}
