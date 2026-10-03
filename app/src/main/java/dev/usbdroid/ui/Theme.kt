package dev.usbdroid.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.usbdroid.data.Preferences

@Composable fun USBDroidTheme(p: Preferences, content: @Composable () -> Unit) {
 val dark = when(p.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
 val scheme = if(p.dynamic && Build.VERSION.SDK_INT >= 31) { if(dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current) }
 else if(dark) darkColorScheme(primary = Color(0xff80d5b5), secondary = Color(0xffb3ccbf), tertiary = Color(0xffa5cddd))
 else lightColorScheme(primary = Color(0xff006c50), secondary = Color(0xff4c6358), tertiary = Color(0xff3e6372))
 val colors = if(dark && p.amoled) scheme.copy(background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xff080808), surfaceContainer = Color(0xff101010), surfaceContainerHigh = Color(0xff181818), surfaceContainerHighest = Color(0xff202020)) else scheme
 val activity = LocalActivity.current; val view = LocalView.current
 SideEffect { if(activity != null && !view.isInEditMode) WindowCompat.getInsetsController(activity.window, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } }
 MaterialTheme(colorScheme = colors, content = content)
}
