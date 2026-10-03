package dev.usbdroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import dev.usbdroid.ui.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
 override fun onStart() { super.onStart(); app.scope.launch { dev.usbdroid.update.UpdateChecks.check(applicationContext) } }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState); enableEdgeToEdge()
  if(android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
  setContent { val model: AppViewModel = viewModel(); val state by model.state.collectAsStateWithLifecycle(); ThemeRevealHost(state.preferences) { USBDroidTheme(state.preferences) { androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) { App(model, state) } } } }
 }
}
