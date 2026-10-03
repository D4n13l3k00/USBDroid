package dev.usbdroid.ui

import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.usbdroid.*
import dev.usbdroid.R
import dev.usbdroid.data.*
import dev.usbdroid.usb.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(model: AppViewModel, state: AppState) {
 var page by rememberSaveable { mutableIntStateOf(0) }
 val snackbar = remember { SnackbarHostState() }
 var deleteSelection by remember { mutableStateOf<List<ImageEntry>?>(null) }
 var hideSelection by remember { mutableStateOf<List<ImageEntry>?>(null) }
 var selectionMode by rememberSaveable { mutableStateOf(false) }
 var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
 var favoritesOnly by rememberSaveable { mutableStateOf(false) }
 var imageQuery by rememberSaveable { mutableStateOf("") }
 var downloadQuery by rememberSaveable { mutableStateOf("") }
 val imageScroll = rememberLazyListState()
 val downloadScroll = rememberLazyListState()
 val settingsScroll = rememberLazyListState()
 val scrollScope = rememberCoroutineScope()
 val showTop by remember(page) { derivedStateOf { val scroll = if(page == 0) imageScroll else downloadScroll; page != 2 && (scroll.firstVisibleItemIndex > 0 || scroll.firstVisibleItemScrollOffset > 200) } }
 var route by rememberSaveable { mutableStateOf<String?>(null) }
 var renderedRoute by rememberSaveable { mutableStateOf<String?>(null) }
 LaunchedEffect(route) { if(route != null) renderedRoute = route }
 var imageId by rememberSaveable { mutableStateOf<String?>(null) }
 var expandedImageId by rememberSaveable { mutableStateOf<String?>(null) }
 var directAdd by rememberSaveable { mutableStateOf(false) }
 val context = LocalContext.current
 val image = state.images.find { it.id == imageId }
 val visibleImages = state.images.filter { it.title.contains(imageQuery, true) && (!favoritesOnly || it.id in state.preferences.favorites) }
 val selectedImages = state.images.filter { it.id in selectedIds }
 fun selectImage(id: String) { selectionMode = true; expandedImageId = null; selectedIds = if(id in selectedIds) selectedIds - id else selectedIds + id }
 fun finishSelection() { selectionMode = false; selectedIds = emptyList() }
 LaunchedEffect(page) { if(page != 0) finishSelection() }
 LaunchedEffect(state.images) { selectedIds = selectedIds.filter { id -> state.images.any { it.id == id } } }
 LaunchedEffect(state.undoRemoval?.token) {
  state.undoRemoval?.let { undo ->
   val outcome = snackbar.showSnackbar(context.getString(R.string.records_removed, undo.ids.size), actionLabel = context.getString(R.string.undo_action), duration = SnackbarDuration.Long)
   if(outcome == SnackbarResult.ActionPerformed) model.undoRemoval(undo.token) else model.clearUndo(undo.token)
  }
 }
 LaunchedEffect(state.notice?.token) { state.notice?.let { kotlinx.coroutines.delay(5000); model.clearNotice(it.token) } }
 androidx.activity.compose.BackHandler(selectionMode && route == null) { finishSelection() }
 val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; if(directAdd) model.add(uri) else model.import(uri) } }
 val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; model.addStorage(uri) } }
 val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if(uri != null && image != null) model.export(image, uri) }
 var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
 val lifecycle = LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle, context) {
  val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }
  lifecycle.addObserver(observer)
  onDispose { lifecycle.removeObserver(observer) }
 }
 val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }
 val permissions = {
  val history = context.getSharedPreferences("permissions", android.content.Context.MODE_PRIVATE)
  val activity = context as? android.app.Activity
  if(Build.VERSION.SDK_INT >= 33 && (history.getBoolean("notifications-requested", false).not() || activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))) {
   history.edit().putBoolean("notifications-requested", true).apply()
   notify.launch(Manifest.permission.POST_NOTIFICATIONS)
  } else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
 }
 LaunchedEffect(state.preferences.language) { val language = state.preferences.language.takeUnless { it == "system" }.orEmpty(); if(AppCompatDelegate.getApplicationLocales().toLanguageTags() != language) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
 if(!state.settingsLoaded) { StartupScreen(model::refresh); return }
 if(!state.preferences.welcomeComplete) { SetupScreen(state, model, permissions, { tree.launch(null) }, notificationsAllowed = notificationsAllowed);
 Messages(state, model); return }
 if(!state.rootChecked) { StartupScreen(model::inspect); return }
 if(!state.usb.root) { RootRequiredScreen(state, model); Messages(state, model); return }
 val currentRoute = route
 val close: () -> Unit = { if(route == currentRoute) route = null }
 val popup = currentRoute in setOf("add", "actions", "host", "rename", "resize", "hybrid", "delete", "hide", "delete-selected", "hide-selected")
 var renderedPopup by remember { mutableStateOf<String?>(null) }
 var popupImage by remember { mutableStateOf<ImageEntry?>(null) }
 LaunchedEffect(currentRoute) {
  val target = currentRoute.takeIf { popup }
  if(renderedPopup != null && renderedPopup != target) kotlinx.coroutines.delay(180)
  renderedPopup = target
  if(target != null && image != null) popupImage = image
 }
 val labels = listOf(stringResource(R.string.ui_69), stringResource(R.string.ui_70), stringResource(R.string.ui_71))
 val icons = listOf(Icons.Rounded.Storage, Icons.Rounded.Download, Icons.Rounded.Settings)
 val refreshKey = if(page == 1) "catalog" else "refresh"
 BoxWithConstraints(Modifier.fillMaxSize()) {
  val wide = maxWidth >= 700.dp
  Row {
   if(wide) NavigationRail(Modifier.fillMaxHeight()) { labels.forEachIndexed { i, label -> NavigationRailItem(page == i, { page = i }, { Icon(icons[i], label) }, label = { Text(label) }) } }
   Scaffold(Modifier.weight(1f), topBar = { TopAppBar(title = { Text(if(page == 0 && selectionMode) stringResource(R.string.selection_count, selectedImages.size) else if(page == 0) "USBDroid" else labels[page]) }, actions = {
    if(page == 0 && selectionMode) {
     IconButton({ selectedIds = visibleImages.map { it.id } }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.selection_all)) }
     IconButton({ finishSelection() }) { Icon(Icons.Rounded.Close, stringResource(R.string.selection_clear)) }
    } else {
    if(page == 0) IconButton({ expandedImageId = null; selectionMode = true }) { Icon(Icons.Rounded.Checklist, stringResource(R.string.selection_start)) }
    if(page != 2) SortMenu(state.preferences, page == 1, false, onOpen = { expandedImageId = null }) { model.sorting(it) }
    if(page != 2) BusyIconButton(state.working(refreshKey), { if(page == 1) model.catalog() else model.refresh() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.ui_72)) }
    }
   }) }, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = { if(!wide) FloatingNavigationBar(page, labels, icons) { page = it } }, floatingActionButton = {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
     AnimatedVisibility(showTop, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) { SmallFloatingActionButton(onClick = { val scroll = if(page == 0) imageScroll else downloadScroll; scrollScope.launch { scroll.animateScrollToItem(0) } }) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.scroll_to_top)) } }
     if(page == 0 && !selectionMode) { val label = stringResource(R.string.ui_73); ExtendedFloatingActionButton(onClick = { expandedImageId = null; route = "add" }, modifier = Modifier.semantics { contentDescription = label }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(label) }) }
    }
   }) { padding ->
    Column(Modifier.padding(padding)) {
     androidx.compose.animation.Crossfade(targetState = page, label = "main-section") { section -> when(section) {
      0 -> Column(Modifier.fillMaxSize()) {
       StatusBanner(state, openUpdate = { route = "about" }, cancel = model::cancel)
       Row(Modifier.fillMaxSize()) {
       LazyColumn(Modifier.weight(1f), state = imageScroll, contentPadding = PaddingValues(bottom = 100.dp)) {
        item { SearchField(imageQuery, { imageQuery = it }, stringResource(R.string.ui_76)) }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
         FilterChip(favoritesOnly, { expandedImageId = null; favoritesOnly = !favoritesOnly }, label = { Text(stringResource(R.string.favorites_only)) }, leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) })
         Text("${sortLabel(state.preferences.imageSort)} · ${stringResource(if(state.preferences.imageDescending) R.string.sort_descending else R.string.sort_ascending)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if(selectionMode) item {
         SelectionActions(selectedImages, state, { model.favorite(selectedIds.toSet(), !selectedImages.all { it.id in state.preferences.favorites }) }, { hideSelection = selectedImages.toList(); route = "hide-selected" }, { model.ejectImages(selectedImages) { finishSelection() } }, { deleteSelection = selectedImages.toList(); route = "delete-selected" })
        }
        if(state.images.isEmpty()) item { EmptyView(stringResource(R.string.ui_78), stringResource(R.string.ui_79)) }
        else if(visibleImages.isEmpty()) item { EmptyView(stringResource(if(favoritesOnly && imageQuery.isBlank()) R.string.favorites_empty else R.string.search_empty), stringResource(if(favoritesOnly && imageQuery.isBlank()) R.string.favorites_hint else R.string.search_empty_detail)) }
        items(sortedImages(visibleImages, state.preferences.imageSort, state.preferences.imageDescending, state.usb.luns.mapNotNull { it.file.takeIf(String::isNotBlank) }.toSet()), key = { it.id }) { entry ->
         ImageLibraryRow(entry, state, expandedImageId == entry.id, { expanded -> imageId = entry.id; expandedImageId = if(expanded) entry.id else null }, Modifier.animateItem(), { lun, mode, done -> model.host(entry, lun, mode) { if(expandedImageId == entry.id) done() } }, { model.ejectImage(entry) }, { action ->
          expandedImageId = null
          imageId = entry.id
          when(action) { 12 -> model.favorite(setOf(entry.id), entry.id !in state.preferences.favorites); 0 -> route = "host"; 4 -> route = "hide"; 5 -> route = "delete"; 6 -> route = "rename"; 7 -> route = "resize"; 8 -> route = "move"; 9 -> exporter.launch(entry.title + ".${entry.file.extension.ifBlank { "img" }}"); 10 -> route = "hybrid"; 11 -> model.copyForHost(entry) }
         }, { algorithm -> model.checksum(entry, algorithm) }, selectionMode = selectionMode, selected = entry.id in selectedIds, select = { selectImage(entry.id) }, favorite = entry.id in state.preferences.favorites)
        }
       }
       if(wide) ImageDetailsPanel(image, state.usb, { route = "host" }, { route = "actions" }, image?.let { state.imageWorking(it.id) } == true, { image?.let { model.ejectImage(it) } })
       }
      }
      1 -> LazyColumn(state = downloadScroll, contentPadding = PaddingValues(bottom = 100.dp)) {
       item { FilledTonalButton(onClick = { route = "url" }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) { Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ui_14)) } }
       item { SortSummary(state.preferences.downloadSort, state.preferences.downloadDescending) }
       item { Text(stringResource(R.string.ui_82), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
       items(sortedJobs(state.jobs.filter { it.kind == "DOWNLOAD" }, state.preferences.jobSort, state.preferences.jobDescending), key = { "job:${it.id}" }) { JobRow(it, model, state) }
       item { SearchField(downloadQuery, { downloadQuery = it }, stringResource(R.string.ui_83)) }
       if(state.catalog.isEmpty() && !state.working("catalog")) item { EmptyView(stringResource(R.string.catalog_empty), stringResource(R.string.catalog_empty_detail)) }
       else if(state.catalog.isNotEmpty() && state.catalog.none { "${it.name} ${it.version} ${it.arch}".contains(downloadQuery, true) }) item { EmptyView(stringResource(R.string.search_empty), stringResource(R.string.search_empty_detail)) }
       items(sortedReleases(state.catalog.distinctBy { listOf(it.url, it.name, it.version, it.arch) }.filter { "${it.name} ${it.version} ${it.arch}".contains(downloadQuery, true) }, state.preferences.downloadSort, state.preferences.downloadDescending), key = { "${it.url}:${it.name}:${it.version}:${it.arch}" }) { release -> DownloadReleaseRow(release, state, model) }
      }
      2 -> SettingsList(state, model, settingsScroll) { route = it }
     }
     }
    }
   }
  }
 }
 AnimatedVisibility(visible = currentRoute != null && !popup, enter = fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 12 }, exit = fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 12 }) {
  when(val shown = renderedRoute) {
   "create" -> CreateImageScreen(state.storage, close, { name, size, fat, allocated, storage -> model.createFormattedAt(name, size, fat, allocated, storage, close) }, busy = state.working("create"))
   "wizard" -> SetupScreen(state, model, permissions, { tree.launch(null) }, close, notificationsAllowed)
   null -> Unit
   else -> ActionScreens(shown, image, state, model, close, { route = it }, { copy -> directAdd = !copy; route = null; importer.launch(arrayOf("*/*")) }, { tree.launch(null) }, { if(image != null) exporter.launch(image.title + ".${image.file.extension.ifBlank { "img" }}") }, { route = null; page = 1; model.catalog() })
  }
 }
 renderedPopup?.let { shown -> CompositionLocalProvider(LocalPopupVisible provides (currentRoute == shown)) {
  when(shown) {
   "delete-selected" -> deleteSelection?.let { entries -> DeleteImagesConfirmation(entries, state.working("bulk-delete"), close) { model.deleteImages(entries) { close(); finishSelection() } } }
   "hide-selected" -> hideSelection?.let { entries -> HideImagesConfirmation(entries, state.working("bulk-hide"), close) { model.hideImages(entries) { close(); finishSelection() } } }
   else -> ActionScreens(shown, popupImage ?: image, state, model, close, { route = it }, { copy -> directAdd = !copy; route = null; importer.launch(arrayOf("*/*")) }, { tree.launch(null) }, { if(image != null) exporter.launch(image.title + ".${image.file.extension.ifBlank { "img" }}") }, { route = null; page = 1; model.catalog() })
  }
 } }
 Messages(state, model) { kind -> model.clearMessage(); when(kind) { ErrorKind.SPACE, ErrorKind.PERMISSION -> route = "directories"; else -> model.inspect() } }
}

@Composable private fun SearchField(value: String, update: (String) -> Unit, hint: String) { OutlinedTextField(value, update, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true) }
@Composable private fun SortSummary(sort: String, descending: Boolean) { Text("${sortLabel(sort)} · ${stringResource(if(descending) R.string.sort_descending else R.string.sort_ascending)}", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
private fun hostModeTextValue(lun: Lun) = if(lun.cdrom) "CD-ROM" else if(lun.readOnly) "USB RO" else "USB RW"
@Composable fun Messages(state: AppState, model: AppViewModel, recover: ((ErrorKind) -> Unit)? = null) {
 state.duplicateDownload?.let { release -> ImagePopup(model::clearDuplicate, { Text(stringResource(R.string.download_duplicate_title)) }, { Text(stringResource(R.string.download_duplicate_body)) }, { Button({ model.clearDuplicate(); model.download(release, allowDuplicate = true) }) { Text(stringResource(R.string.download_again)) } }, dismissButton = { TextButton(model::clearDuplicate) { Text(stringResource(R.string.ui_10)) } }) }
 state.message?.let { message -> TintedAlertDialog(model::clearMessage, title = { Text(if(state.messageIsError) stringResource(R.string.error_title) else "USBDroid") }, text = {
  if(state.messageIsError) ErrorExplanation(message, recover = recover ?: { model.clearMessage(); model.inspect() }) else androidx.compose.foundation.text.selection.SelectionContainer { Text(message, Modifier.verticalScroll(rememberScrollState())) }
 }, confirmButton = { TextButton(model::clearMessage) { Text(stringResource(R.string.ui_67)) } }) }
}

@Composable private fun SettingsList(state: AppState, model: AppViewModel, scroll: LazyListState, open: (String) -> Unit) {
 val busy = state.working("settings")
 val reveal = LocalThemeReveal.current
 fun appearance(p: Preferences) { reveal?.capture(p); model.settings(p) }
 LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
  item { SettingsSection(stringResource(R.string.ui_91), first = true) }
  item { SettingsChoice(stringResource(R.string.settings_theme), Icons.Rounded.Palette, state.preferences.theme, listOf("system" to stringResource(R.string.ui_92), "light" to stringResource(R.string.ui_93), "dark" to stringResource(R.string.ui_94)), !busy, segmented = true) { appearance(state.preferences.copy(theme = it)) } }
  item { Toggle(stringResource(R.string.ui_95), stringResource(R.string.ui_96), state.preferences.dynamic, enabled = !busy) { appearance(state.preferences.copy(dynamic = it)) } }
  item { Toggle("AMOLED", stringResource(R.string.ui_97), state.preferences.amoled, enabled = !busy) { appearance(state.preferences.copy(amoled = it)) } }
  item { SettingsSection(stringResource(R.string.settings_application)) }
  item { SettingsChoice(stringResource(R.string.settings_language), Icons.Rounded.Translate, state.preferences.language, languageChoices(), !busy) { model.settings(state.preferences.copy(language = it)) } }
  item { SettingsSection(stringResource(R.string.settings_images_downloads)) }
  item { Toggle(stringResource(R.string.ui_102), stringResource(R.string.ui_103), state.preferences.autoHybrid, enabled = !busy) { model.settings(state.preferences.copy(autoHybrid = it)) } }
  item { SettingsLink(stringResource(R.string.ui_21), Icons.Rounded.FolderOpen) { open("directories") } }
  item { SettingsLink(stringResource(R.string.ui_28), Icons.Rounded.CloudDownload) { open("repositories") } }
  item { SettingsSection(stringResource(R.string.usb_settings_section)) }
  item { Toggle(stringResource(R.string.ui_100), stringResource(R.string.ui_101), state.preferences.keepAwake, enabled = !busy) { model.settings(state.preferences.copy(keepAwake = it)) } }
  item { ListItem(headlineContent = { Text(stringResource(R.string.usb_connection_title)) }, supportingContent = { Text(usbConnectionText(state.usb.cable)) }, leadingContent = { Icon(Icons.Rounded.Cable, null) }, trailingContent = { BusyIconButton(state.rootBusy, model::inspect) { Icon(Icons.Rounded.Refresh, stringResource(R.string.ui_86)) } }) }

  if(state.usb.luns.isEmpty()) item { BusyTextButton(stringResource(R.string.setup_prepare), state.working("prepare"), model::prepare, Modifier.padding(horizontal = 20.dp)) }
  state.usb.error?.let { error -> item { ErrorExplanation(error, Modifier.padding(horizontal = 20.dp), compact = true, recover = { model.inspect() }) } }
  item { SettingsLink(stringResource(R.string.usb_advanced), Icons.Rounded.SettingsInputComponent) { open("usbsettings") } }
  item { SettingsLink(stringResource(R.string.ui_87), Icons.Rounded.AutoFixHigh) { model.setup(step = -1); open("wizard") } }
  item { SettingsSection(stringResource(R.string.settings_support)) }
  item { SettingsLink(stringResource(R.string.ui_106), Icons.Rounded.Info) { open("about") } }
  item { SettingsLink(stringResource(R.string.ui_107), Icons.Rounded.BugReport) { open("diagnostics") } }
 }
}
@Composable private fun SettingsSection(title: String, first: Boolean = false) { Column {
 if(!first) HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
 Text(title, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
} }
@Composable private fun usbConnectionText(raw: String): String {
 val status = raw.lowercase(java.util.Locale.ROOT)
 return stringResource(when {
  "configured" in status || "suspended" in status -> R.string.usb_connection_configured
  "disconnected" in status || "not attached" in status || "not-attached" in status -> R.string.usb_connection_disconnected
  "connected" in status || "powered" in status || "addressed" in status || "attached" in status -> R.string.usb_connection_attached
  else -> R.string.usb_connection_unknown
 })
}
@Composable private fun SettingsLink(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) { ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable(onClick = click)) }

@Composable private fun ActionScreens(route: String, image: ImageEntry?, state: AppState, model: AppViewModel, close: () -> Unit, choose: (String) -> Unit, import: (Boolean) -> Unit, tree: () -> Unit, export: () -> Unit, downloads: () -> Unit) {
 val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
 val context = LocalContext.current
 when(route) {
  "add" -> MenuDialog(stringResource(R.string.ui_73), listOf(stringResource(R.string.ui_108), stringResource(R.string.ui_109), stringResource(R.string.ui_110), stringResource(R.string.ui_111), stringResource(R.string.ui_112)), close, busy = listOfNotNull(1.takeIf { state.working("create") }).toSet()) { index -> when(index) { 0 -> import(true); 1 -> choose("create"); 2 -> downloads(); 3 -> import(false); 4 -> choose("path") } }
  "host" -> if(image != null) { if(image.physicalPath == null) ConfirmDialog(stringResource(R.string.ui_113), stringResource(R.string.copy_space, sizeText(image.size)), close, busy = state.imageWorking(image.id)) { model.copyForHost(image) } else HostImageDialog(image, state, close, { lun, mode -> model.host(image, lun, mode, close) }, { model.ejectImage(image) }) }
  "actions" -> if(image != null) {
   val connected = state.usb.luns.any { it.file == image.physicalPath }
   val busy = buildSet { if(state.working("eject-image:${image.id}")) add(0); if(state.working("remove:${image.id}")) add(4); if(state.working("copy_host:${image.id}:")) add(11); listOf("SHA-256", "SHA-1", "MD5").forEachIndexed { i, algo -> if(state.working("checksum:${image.id}:$algo")) add(i + 1) } }
   val blocked = buildSet {
    if(state.imageWorking(image.id)) addAll(setOf(0, 4, 5, 6, 7, 8, 10, 11))
    if(connected) addAll(setOf(4, 5, 7, 8, 10))
    if(state.usb.luns.any { it.file == image.physicalPath && !it.readOnly }) addAll(setOf(1, 2, 3, 9, 11))
   }
   MenuDialog(image.title, listOf(stringResource(if(connected) R.string.ui_90 else R.string.ui_114), "SHA-256", "SHA-1", "MD5", stringResource(R.string.ui_115), stringResource(R.string.ui_116), stringResource(R.string.ui_117), stringResource(R.string.ui_2), stringResource(R.string.ui_118), stringResource(R.string.ui_119), stringResource(R.string.ui_120), stringResource(R.string.ui_121)), close, busy = busy, disabled = blocked, longPick = (1..3).associateWith { index -> { model.checksum(image, listOf("SHA-256", "SHA-1", "MD5")[index - 1]) } }, header = {
    Text(stringResource(if(connected) R.string.details_hosted else R.string.details_detached), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.checksum_gestures), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }, detail = { index -> if(index in 1..3) {
    val algorithm = listOf("SHA-256", "SHA-1", "MD5")[index - 1]
    val job = latestChecksum(state.jobs, image, algorithm)
    job?.let { ChecksumResult(it) }
   } }) { index -> when(index) { 0 -> if(connected) model.ejectImage(image) else { choose("host") }; 1, 2, 3 -> { val value = latestChecksum(state.jobs, image, listOf("SHA-256", "SHA-1", "MD5")[index - 1]); if(value?.state == "DONE") clipboard.setText(androidx.compose.ui.text.AnnotatedString(value.result.substringAfter('\n'))) else android.widget.Toast.makeText(context, R.string.checksum_hold, android.widget.Toast.LENGTH_SHORT).show() }; 4 -> choose("hide"); 5 -> choose("delete"); 6 -> choose("rename"); 7 -> choose("resize"); 8 -> choose("move"); 9 -> export(); 10 -> choose("hybrid"); 11 -> model.copyForHost(image) } }
  }
  "delete" -> if(image != null) DeleteImagesConfirmation(listOf(image), state.working("remove:${image.id}"), close) { model.remove(image, true, close) }
  "hide" -> if(image != null) HideImagesConfirmation(listOf(image), state.working("remove:${image.id}"), close) { model.remove(image, false, close) }
  else -> AdvancedDialogs(route, image, state, model, close, choose, tree)
 }
}
