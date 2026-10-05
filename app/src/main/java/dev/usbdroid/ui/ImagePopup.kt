package dev.usbdroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Compact image controls retain the library underneath and inherit the active palette. */
internal val LocalPopupVisible = staticCompositionLocalOf { true }
@Composable fun ImagePopup(onDismissRequest: () -> Unit, title: @Composable () -> Unit, text: @Composable () -> Unit, confirmButton: @Composable () -> Unit, progress: @Composable () -> Unit = {}, dismissButton: @Composable () -> Unit = {}) {
 var shown by remember { mutableStateOf(false) }
 val visible = LocalPopupVisible.current
 LaunchedEffect(visible) { shown = visible }
 val entrance by animateFloatAsState(if(shown) 1f else 0f, tween(180), label = "popup-entrance")
 Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
  Surface(Modifier.padding(20.dp).widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 680.dp).graphicsLayer { alpha = entrance; scaleX = .94f + .06f * entrance; scaleY = scaleX }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
   Column {
    Column(Modifier.fillMaxWidth().padding(24.dp)) { ProvideTextStyle(MaterialTheme.typography.titleLarge, title) }
    progress()
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() } }
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) { dismissButton(); Spacer(Modifier.width(8.dp)); confirmButton() }
   }
  }
 }
}
