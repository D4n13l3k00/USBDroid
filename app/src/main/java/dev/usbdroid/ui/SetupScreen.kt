package dev.usbdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.usbdroid.working
import dev.usbdroid.usbWorking
import dev.usbdroid.AppState
import dev.usbdroid.AppViewModel
import dev.usbdroid.R
import dev.usbdroid.usb.HostMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SetupScreen(state: AppState, model: AppViewModel, notifications: () -> Unit, directory: () -> Unit, close: () -> Unit = {}, notificationsAllowed: Boolean = false) {
 val p = state.preferences
 val step = p.setupStep.coerceIn(-1, 8)
 if(step == -1) {
  SetupLanguageScreen(p.language, state.working("settings"), state.working("setup"), { model.settings(p.copy(language = it)) }, { model.setup(step = 0) }, close.takeIf { p.welcomeComplete })
  return
 }
 val titles = listOf(R.string.welcome_title, R.string.root_title, R.string.setup_storage_title, R.string.setup_cable_title, R.string.setup_system_title, R.string.setup_test_title, R.string.setup_detect_title, R.string.setup_boot_title, R.string.setup_summary_title)
 val icons = listOf(Icons.Rounded.Usb, Icons.Rounded.AdminPanelSettings, Icons.Rounded.FolderOpen, Icons.Rounded.Cable, Icons.Rounded.SettingsInputComponent, Icons.Rounded.Storage, Icons.Rounded.Computer, Icons.Rounded.PowerSettingsNew, Icons.Rounded.TaskAlt)
 val job = state.jobs.find { it.id == p.setupJob }
 val image = state.images.find { it.id == job?.result }
 val lun = state.usb.luns.find { it.path == p.setupLun } ?: state.usb.luns.firstOrNull { it.file.isBlank() || it.file == image?.physicalPath }
 val working = job?.state in listOf("QUEUED", "RUNNING")
 var mode by rememberSaveable { mutableStateOf(HostMode.READ_ONLY.name) }
 val selectedMode = HostMode.valueOf(mode)
 val active = image?.physicalPath != null && lun?.file == image.physicalPath && lun.readOnly == selectedMode.ro && lun.cdrom == selectedMode.cd
 val finishing = state.working("setup-finish")
 LaunchedEffect(step, state.rootChecked) { if(step > 0 && !state.rootChecked && !state.rootBusy) model.inspect() }
 fun advance() { model.setup(step = step + 1); if(step == 0 && !state.rootChecked || step == 3) model.inspect() }
 BackHandler { model.setup(step = step - 1) }
 if(step > 0 && state.rootChecked && !state.usb.root && !state.rootBusy) { RootRequiredScreen(state, model); return }
 val nextEnabled = !finishing && !state.working("setup") && !state.usbWorking() && when(step) { 0, 1 -> !state.rootBusy && (step == 0 || state.usb.root); 2 -> state.storage.isNotEmpty(); 4 -> state.usb.luns.isNotEmpty(); 5 -> active; 6 -> p.diskResult in listOf("USB", "CDROM"); else -> true }
 Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }, navigationIcon = { IconButton(onClick = { model.setup(step = step - 1) }) { Icon(Icons.Rounded.ArrowBack, stringResource(R.string.create_back)) } }) }, bottomBar = {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
   if(step == 6 || step == 7) TextButton(enabled = !finishing && !state.working("setup"), onClick = { model.setup(step = step + 1, disk = if(step == 6) "NOT_TESTED" else null, boot = if(step == 7) "NOT_TESTED" else null) }) { Text(stringResource(R.string.setup_later)) }
   BusyButton(stringResource(if(step == 0) R.string.request_root else if(step == 8) R.string.start_app else R.string.setup_next), finishing || state.working("setup") || (step <= 1 && state.rootBusy), { if(step == 8) model.finishSetup(close) else advance() }, enabled = nextEnabled, modifier = Modifier.weight(1f))
  } }
 }) { padding ->
  Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
   AnimatedContent(step, transitionSpec = { (fadeIn(tween(160)) + slideInHorizontally(tween(180)) { it / 12 }) togetherWith (fadeOut(tween(100)) + slideOutHorizontally(tween(140)) { -it / 12 }) }, label = "setup-page") { shownStep ->
   Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    if(shownStep > 0) SetupStepProgress(shownStep)
    SetupHero(shownStep, icons[shownStep], busy = (shownStep in 1..4 && state.rootBusy) || (shownStep == 5 && (working || state.usbWorking())) || (shownStep == 8 && finishing), complete = (shownStep == 1 && state.usb.root) || (shownStep == 5 && active) || (shownStep == 6 && p.diskResult in listOf("USB", "CDROM")))
    Text(stringResource(titles[shownStep]), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    when(shownStep) {
     0 -> { Text(stringResource(R.string.setup_welcome), style = MaterialTheme.typography.bodyLarge); Text(stringResource(R.string.setup_steps), color = MaterialTheme.colorScheme.onSurfaceVariant) }
     1 -> { Text(stringResource(R.string.root_body)); if(state.usb.root) StatusLine(stringResource(R.string.root_granted)); BusyButton(stringResource(R.string.retry_root), state.rootBusy, model::inspect) }
     2 -> {
      Text(stringResource(R.string.setup_storage_body))
      state.storage.forEach { storage -> ListItem(headlineContent = { Text(storage.title) }, supportingContent = { Text(storage.location) }, leadingContent = { RadioButton(storage.primary, null) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.clickable(enabled = !state.working("storage-primary")) { model.primaryStorage(storage) }) }
      OutlinedButton(onClick = directory, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.choose_directory)) }
      OutlinedButton(onClick = notifications, Modifier.fillMaxWidth(), enabled = !notificationsAllowed) { Icon(if(notificationsAllowed) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications, null); Spacer(Modifier.width(8.dp)); Text(stringResource(if(notificationsAllowed) R.string.notifications_allowed else R.string.allow_notifications)) }
      Text(stringResource(R.string.permissions_optional), style = MaterialTheme.typography.bodySmall)
     }
     3 -> { Text(stringResource(R.string.setup_cable_body)); Text(state.usb.cable.ifBlank { stringResource(R.string.setup_cable_unknown) }); BusyTextButton(stringResource(R.string.setup_refresh), state.rootBusy, model::inspect) }
     4 -> {
      Text(stringResource(R.string.setup_system_body))
      val configfs = state.usb.luns.any { it.path.contains("/functions/") }
      val legacy = state.usb.luns.any { !it.path.contains("/functions/") }
      val systems = buildList { add("auto" to stringResource(R.string.setup_auto)); if(configfs) add("configfs" to "ConfigFS"); if(legacy) { add("setprop" to "Android setprop"); add("functions" to "Android functions"); add("samsung" to "Samsung UMS") } }
      systems.forEach { (id, title) -> ListItem(headlineContent = { Text(title) }, leadingContent = { RadioButton(p.usbSystem == id, { model.settings(p.copy(usbSystem = id)) }) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) }
      BusyTextButton(stringResource(R.string.setup_refresh), state.rootBusy, model::inspect)
      if(state.usb.luns.isEmpty()) { Text(stringResource(R.string.setup_no_lun), color = MaterialTheme.colorScheme.error); BusyButton(stringResource(R.string.setup_prepare), state.working("prepare"), model::prepare) }
      else { Text(stringResource(R.string.setup_lun_count, state.usb.luns.size)); state.usb.luns.forEach { device -> Text(device.title, style = MaterialTheme.typography.bodySmall) } }
     }
     5 -> {
      Text(stringResource(R.string.setup_test_body))
      if(job == null || job.state in listOf("FAILED", "CANCELLED") || working || state.working("setup-create")) BusyButton(stringResource(R.string.setup_create_test), working || state.working("setup-create"), { model.setupCreate() }, Modifier.fillMaxWidth())
      if(job?.state == "FAILED") Text(job.error, color = MaterialTheme.colorScheme.error)
      if(working) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.setup_creating)) }
      if(image != null) {
       StatusLine(stringResource(R.string.setup_test_ready, sizeText(image.size)))
       state.usb.luns.forEach { device -> ListItem(headlineContent = { Text(device.title) }, supportingContent = { Text(device.file.ifBlank { stringResource(R.string.setup_empty_lun) }) }, leadingContent = { RadioButton(lun?.path == device.path, { model.setup(lun = device.path) }) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) }
       HostMode.entries.forEach { option -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == option.name, { mode = option.name }, enabled = lun != null && (!option.cd || lun.supportsCdrom) && (lun.supportsReadOnly || option.ro == lun.readOnly)); Text(hostModeText(option)) } }
       val appropriate = (mode == HostMode.CDROM.name) == image.file.extension.equals("iso", true)
       if(!appropriate) BusyButton(stringResource(if(mode == HostMode.CDROM.name) R.string.setup_create_cd else R.string.setup_create_test), state.working("setup-create"), { model.setupCreate(mode == HostMode.CDROM.name) }, Modifier.fillMaxWidth())
       if(!active) BusyButton(stringResource(R.string.setup_host), state.usbWorking(lun?.path), { if(lun != null) { model.setup(lun = lun.path); model.host(image, lun, HostMode.valueOf(mode)) } }, enabled = appropriate && lun != null && (lun.file.isBlank() || lun.file == image.physicalPath), modifier = Modifier.fillMaxWidth())
       else StatusLine(stringResource(R.string.setup_kernel_ok))
      }
     }
     6 -> {
      Text(stringResource(R.string.setup_detect_body))
      listOf("USB" to R.string.setup_seen_usb, "CDROM" to R.string.setup_seen_cd, "FAILED" to R.string.setup_not_seen).forEach { (result, label) -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(p.diskResult == result, { model.setup(disk = result) }); Text(stringResource(label)) } }
      if(p.diskResult == "FAILED") { Text(stringResource(R.string.setup_retry_body), color = MaterialTheme.colorScheme.error); OutlinedButton(onClick = { if(active && lun != null) model.eject(lun); model.setup(step = 4) }) { Text(stringResource(R.string.setup_try_system)) } }
     }
     7 -> {
      Text(stringResource(R.string.setup_boot_body)); Text(stringResource(R.string.setup_boot_limits), style = MaterialTheme.typography.bodySmall)
      listOf("PASSED" to R.string.setup_boot_yes, "FAILED" to R.string.setup_boot_no, "NOT_TESTED" to R.string.setup_boot_skip).forEach { (result, label) -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(p.bootResult == result, { model.setup(boot = result) }); Text(stringResource(label)) } }
     }
     8 -> {
      Text(stringResource(R.string.setup_summary_body))
      StatusLine(stringResource(R.string.root_granted))
      Text(stringResource(R.string.setup_disk_result, resultText(p.diskResult)))
      Text(stringResource(R.string.setup_boot_result, resultText(p.bootResult)))
      Text(stringResource(R.string.setup_finish_note), style = MaterialTheme.typography.bodySmall)
      if(finishing) LinearProgressIndicator(Modifier.fillMaxWidth())
     }
    }
    if(state.message != null) { Text(state.message, color = MaterialTheme.colorScheme.error); TextButton(onClick = model::clearMessage) { Text(stringResource(R.string.setup_dismiss_error)) } }
   }
   }
  }
 }
}
@Composable private fun StatusLine(text: String) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Text(text) } }
@Composable fun hostModeText(mode: HostMode): String = stringResource(when(mode) { HostMode.READ_ONLY -> R.string.host_ro; HostMode.WRITABLE -> R.string.host_rw; HostMode.CDROM -> R.string.host_cd })
@Composable private fun resultText(result: String): String = stringResource(when(result) { "USB" -> R.string.setup_seen_usb; "CDROM" -> R.string.setup_seen_cd; "PASSED" -> R.string.setup_passed; "FAILED" -> R.string.setup_failed; else -> R.string.setup_unverified })
@Composable fun StartupScreen(retry: () -> Unit) {
 var timedOut by remember { mutableStateOf(false) }
 LaunchedEffect(Unit) { kotlinx.coroutines.delay(10000); timedOut = true }
 Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.setup_starting)); if(timedOut) TextButton(onClick = retry) { Text(stringResource(R.string.retry_root)) } }
}
