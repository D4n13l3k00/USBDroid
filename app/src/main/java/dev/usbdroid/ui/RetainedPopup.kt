package dev.usbdroid.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** Retains the closing dialog until its exit transition finishes. */
@Composable internal fun <T : Any> RetainedPopup(value: T?, content: @Composable (T) -> Unit) {
 var rendered by remember { mutableStateOf(value) }
 LaunchedEffect(value) { if(value != null) rendered = value else { delay(180); rendered = null } }
 rendered?.let { CompositionLocalProvider(LocalPopupVisible provides (value != null)) { content(it) } }
}
