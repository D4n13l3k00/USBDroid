package dev.usbdroid.ui

import android.app.Activity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.*
import dev.usbdroid.R

@Composable fun RootRequiredScreen(state: AppState, model: AppViewModel) {
 val activity = LocalActivity.current
 Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
  Spacer(Modifier.height(72.dp)); Icon(Icons.Rounded.GppBad, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
  Text(stringResource(R.string.root_required_title), style = MaterialTheme.typography.headlineMedium)
  Text(stringResource(R.string.root_required_body), style = MaterialTheme.typography.bodyLarge)
   BusyButton(stringResource(R.string.retry_root), state.rootBusy, model::inspect, Modifier.fillMaxWidth())
   OutlinedButton(onClick = {
    val manager = listOf("com.rifsxd.ksunext", "me.weishu.kernelsu", "com.topjohnwu.magisk").firstNotNullOfOrNull { activity?.packageManager?.getLaunchIntentForPackage(it) }
    if(manager != null) activity?.startActivity(manager)
   }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_root_manager)) }
   OutlinedButton(onClick = { activity?.finishAffinity() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.close_app)) }
 } }
}
