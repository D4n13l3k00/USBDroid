package dev.usbdroid.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import dev.usbdroid.BuildConfig
import dev.usbdroid.R
import dev.usbdroid.update.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class UpdateViewModel(application: Application): AndroidViewModel(application) {
 val release = UpdateChecks.release
 val checking = UpdateChecks.checking
 val status = UpdateChecks.status
 val installError = MutableStateFlow(0)
 val work = WorkManager.getInstance(application).getWorkInfosForUniqueWorkFlow("app-update")
 init {
  viewModelScope.launch {
   withContext(Dispatchers.IO) {
    val file = File(application.filesDir, "updates/update.apk")
    if(file.exists()) try { GitHubUpdates.validate(application, file) } catch(_: Exception) { file.delete() }
   }
  }
 }
 fun check() {
  viewModelScope.launch { UpdateChecks.check(getApplication(), force = true) }
 }
 fun download() {
  val item = release.value ?: return
  WorkManager.getInstance(getApplication()).enqueueUniqueWork("app-update", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("url" to item.url, "size" to item.size, "digest" to item.digest, "version" to item.version)).build())
 }
 fun cancel() { WorkManager.getInstance(getApplication()).cancelUniqueWork("app-update") }
}

@Composable private fun rememberUpdateInstaller(model: UpdateViewModel): () -> Unit {
 val context = LocalContext.current
 fun install() {
  try {
   val file = File(context.filesDir, "updates/update.apk")
   GitHubUpdates.validate(context, file)
   val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
   context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
  } catch(e: Exception) { model.installError.value = updateError(e.message) }
 }
 val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
  if(context.packageManager.canRequestPackageInstalls()) install() else model.installError.value = R.string.update_permission
 }
 return {
  model.installError.value = 0
  if(context.packageManager.canRequestPackageInstalls()) install()
  else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
 }
}

@Composable fun UpdateCard(modifier: Modifier = Modifier, model: UpdateViewModel = viewModel()) {
 val context = LocalContext.current
 val release by model.release.collectAsStateWithLifecycle()
 val checking by model.checking.collectAsStateWithLifecycle()
 val status by model.status.collectAsStateWithLifecycle()
 val works by model.work.collectAsStateWithLifecycle(emptyList())
 val work = works.firstOrNull()
 val active = work != null && !work.state.isFinished
 val ready = work?.state == WorkInfo.State.SUCCEEDED && (release == null || work.outputData.getString("version") == release?.version) && File(context.filesDir, "updates/update.apk").exists()
 val installError by model.installError.collectAsStateWithLifecycle()
 val install = rememberUpdateInstaller(model)
 Surface(modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
  Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
   Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
   if(release != null) {
    Text(stringResource(R.string.update_available, release!!.version), style = MaterialTheme.typography.bodyLarge)
    if(release!!.notes.isNotBlank()) Text(release!!.notes, style = MaterialTheme.typography.bodyMedium)
   }
   if(status != 0) Text(stringResource(status), color = MaterialTheme.colorScheme.onSurfaceVariant)
   if(work?.state == WorkInfo.State.FAILED) Text(stringResource(updateError(work.outputData.getString("error"))), color = MaterialTheme.colorScheme.error)
   if(installError != 0) Text(stringResource(installError), color = MaterialTheme.colorScheme.error)
   when {
    active -> {
     Text(stringResource(if(work?.state == WorkInfo.State.RUNNING) R.string.update_downloading else R.string.update_waiting))
     LinearProgressIndicator(progress = { (work?.progress?.getInt("progress", 0) ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
     TextButton(model::cancel) { Text(stringResource(R.string.cancel)) }
    }
    ready -> Button(onClick = install) { Text(stringResource(R.string.update_install)) }
    release != null -> Button(model::download) { Text(stringResource(R.string.update_download)) }
   }
   OutlinedButton(onClick = model::check, enabled = !checking && !active) {
    if(checking) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
    Text(stringResource(if(checking) R.string.update_checking else R.string.update_check))
   }
  }
 }
}

@Composable fun UpdateBannerRow(open: () -> Unit, model: UpdateViewModel = viewModel()) {
 val context = LocalContext.current
 val release by model.release.collectAsStateWithLifecycle()
 val works by model.work.collectAsStateWithLifecycle(emptyList())
 val work = works.firstOrNull()
 val active = work != null && !work.state.isFinished
 val ready = work?.state == WorkInfo.State.SUCCEEDED && (release == null || work.outputData.getString("version") == release?.version) && File(context.filesDir, "updates/update.apk").exists()
 val install = rememberUpdateInstaller(model)
 AnimatedVisibility(release != null || active || ready) {
  Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = open)) {
   Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
     Icon(Icons.Rounded.SystemUpdate, null)
     Column(Modifier.weight(1f)) {
      Text(stringResource(when { ready -> R.string.update_ready; active -> if(work?.state == WorkInfo.State.RUNNING) R.string.update_downloading else R.string.update_waiting; else -> R.string.update_available }, release?.version.orEmpty()), style = MaterialTheme.typography.labelLarge)
      if(work?.state == WorkInfo.State.FAILED) Text(stringResource(R.string.update_retry), style = MaterialTheme.typography.bodySmall)
     }
     if(ready) TextButton(install) { Text(stringResource(R.string.update_install)) }
    }
    if(active) LinearProgressIndicator(progress = { (work?.progress?.getInt("progress", 0) ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
    val installError by model.installError.collectAsStateWithLifecycle()
    if(installError != 0) Text(stringResource(installError), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
   }
  }
 }
}
