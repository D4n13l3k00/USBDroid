package dev.usbdroid.ui

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.core.view.drawToBitmap
import dev.usbdroid.data.Preferences
import kotlinx.coroutines.delay
import kotlin.math.hypot

private fun Preferences.appearanceKey() = Triple(theme, dynamic, amoled)
internal data class ThemeSnapshot(val image: ImageBitmap, val center: Offset, val target: Triple<String, Boolean, Boolean>)
internal class ThemeRevealController(private val root: View) {
 var snapshot by mutableStateOf<ThemeSnapshot?>(null)
 private var touch: Offset? = null
 fun recordScreen(position: Offset) {
  val rootLocation = IntArray(2); root.getLocationOnScreen(rootLocation)
  touch = position - Offset(rootLocation[0].toFloat(), rootLocation[1].toFloat())
 }
 fun record(view: View, position: Offset) {
  val location = IntArray(2); view.getLocationOnScreen(location)
  recordScreen(position + Offset(location[0].toFloat(), location[1].toFloat()))
 }
 fun capture(target: Preferences) {
  val bitmap = runCatching { root.drawToBitmap().asImageBitmap() }.getOrNull() ?: return
  snapshot = ThemeSnapshot(bitmap, touch ?: Offset(root.width / 2f, root.height / 2f), target.appearanceKey())
 }
}
internal val LocalThemeReveal = staticCompositionLocalOf<ThemeRevealController?> { null }
internal fun Modifier.recordThemeTouch(): Modifier = composed {
 val controller = LocalThemeReveal.current
 var origin by remember { mutableStateOf(Offset.Zero) }
 onGloballyPositioned { origin = it.positionOnScreen() }.pointerInput(controller) { awaitEachGesture { val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); controller?.recordScreen(origin + down.position) } }
}

@Composable fun ThemeRevealHost(preferences: Preferences, content: @Composable () -> Unit) {
 val view = LocalView.current
 val controller = remember(view) { ThemeRevealController(view) }
 val snapshot = controller.snapshot
 val radius = remember(snapshot) { Animatable(0f) }
 val key = preferences.appearanceKey()
 LaunchedEffect(snapshot, key) {
  if(snapshot != null && snapshot.target == key) {
   radius.snapTo(0f)
   val x = maxOf(snapshot.center.x, snapshot.image.width - snapshot.center.x)
   val y = maxOf(snapshot.center.y, snapshot.image.height - snapshot.center.y)
   radius.animateTo(hypot(x, y), tween(520, easing = FastOutSlowInEasing))
   if(controller.snapshot === snapshot) controller.snapshot = null
  }
 }
 LaunchedEffect(snapshot) { if(snapshot != null) { delay(3000); if(controller.snapshot === snapshot) controller.snapshot = null } }
 CompositionLocalProvider(LocalThemeReveal provides controller) {
  Box(Modifier.fillMaxSize().recordThemeTouch()) {
   content()
   if(snapshot != null) Canvas(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
    drawImage(snapshot.image)
    if(snapshot.target == key) drawCircle(Color.Transparent, radius.value, snapshot.center, blendMode = BlendMode.Clear)
   }
  }
 }
}
