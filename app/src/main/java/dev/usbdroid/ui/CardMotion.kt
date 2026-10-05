package dev.usbdroid.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment

internal val cardExpand = expandVertically(animationSpec = spring(dampingRatio = 1f, stiffness = 1400f), expandFrom = Alignment.Top) + fadeIn(tween(90))
internal val cardCollapse = shrinkVertically(animationSpec = spring(dampingRatio = 1f, stiffness = 1600f), shrinkTowards = Alignment.Top) + fadeOut(tween(70))
