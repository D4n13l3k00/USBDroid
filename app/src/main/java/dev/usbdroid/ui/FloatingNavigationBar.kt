package dev.usbdroid.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable fun FloatingNavigationBar(selected: Int, labels: List<String>, icons: List<ImageVector>, select: (Int) -> Unit) {
 val centers = remember { mutableStateMapOf<Int, Offset>() }
 var origin by remember { mutableStateOf(Offset.Zero) }
 val density = LocalDensity.current
 val halfWidth = with(density) { 32.dp.toPx() }; val halfHeight = with(density) { 16.dp.toPx() }
 Surface(color = NavigationBarDefaults.containerColor) {
  Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
   centers[selected]?.let { center ->
    val target = IntOffset((center.x - origin.x - halfWidth).roundToInt(), (center.y - origin.y - halfHeight).roundToInt())
    val offset by animateIntOffsetAsState(target, spring(dampingRatio = .82f, stiffness = 380f), label = "navigation-pill")
    Box(Modifier.offset { offset }.size(64.dp, 32.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)))
   }
   NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
    labels.forEachIndexed { index, label ->
     NavigationBarItem(selected == index, { select(index) }, icon = {
      Icon(icons[index], null, Modifier.onGloballyPositioned { centers[index] = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) })
     }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent))
    }
   }
  }
 }
}
