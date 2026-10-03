package dev.usbdroid.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver

@Composable fun TintedAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null) {
 val colors = MaterialTheme.colorScheme
 AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, title = title, text = text,
  containerColor = colors.primary.copy(alpha = .08f).compositeOver(colors.surfaceContainerHigh), titleContentColor = colors.onSurface, textContentColor = colors.onSurfaceVariant)
}
