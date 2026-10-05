package dev.usbdroid.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.R

@Composable internal fun SetupHero(step: Int, icon: ImageVector, busy: Boolean = false, complete: Boolean = false, modifier: Modifier = Modifier) {
 val entrance = remember(step) { Animatable(0f) }
 LaunchedEffect(step) { entrance.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
 val colors = MaterialTheme.colorScheme
 Box(modifier.fillMaxWidth().height(148.dp), contentAlignment = Alignment.Center) {
  if(step in 3..6) {
   Canvas(Modifier.width(232.dp).height(96.dp)) {
    val y = size.height / 2
    drawLine(colors.primary.copy(alpha = .35f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * entrance.value, y), strokeWidth = 2.dp.toPx())
   }
   Row(Modifier.width(280.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Surface(shape = CircleShape, color = colors.surfaceContainerHigh) { Icon(Icons.Rounded.Usb, null, Modifier.padding(14.dp).size(24.dp), tint = colors.primary) }
    Surface(shape = CircleShape, color = colors.surfaceContainerHigh) { Icon(Icons.Rounded.Computer, null, Modifier.padding(14.dp).size(24.dp), tint = colors.primary) }
   }
  }
  Surface(Modifier.size(96.dp).graphicsLayer { alpha = entrance.value; scaleX = .85f + .15f * entrance.value; scaleY = scaleX; rotationZ = -8f * (1f - entrance.value) }, shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
   Box(contentAlignment = Alignment.Center) {
    AnimatedContent(if(step == 8 || complete) Icons.Rounded.TaskAlt else icon, transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(100)) }, label = "setup-icon") { shown -> Icon(shown, null, Modifier.size(48.dp)) }
   }
  }
  if(busy) CircularProgressIndicator(Modifier.size(108.dp), strokeWidth = 2.dp, color = colors.primary)
 }
}

@Composable internal fun SetupStepProgress(step: Int, modifier: Modifier = Modifier) {
 Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Text(stringResource(R.string.setup_step, step, 8), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  Row(Modifier.widthIn(max = 280.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
   repeat(8) { index ->
    val color by animateColorAsState(if(index < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, tween(120), label = "setup-step-$index")
    Box(Modifier.weight(1f).height(4.dp).background(color, RoundedCornerShape(50)))
   }
  }
 }
}
