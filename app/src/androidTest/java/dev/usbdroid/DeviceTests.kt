package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.work.*
import dev.usbdroid.data.*
import dev.usbdroid.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceTests {
 @get:Rule val compose = createComposeRule()
 private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.app
 @Test fun themeRevealUsesOffsetButtonPositionInsteadOfItsLocalCoordinates() {
  val preferences = androidx.compose.runtime.mutableStateOf(Preferences(theme = "dark"))
  var reveal: ThemeRevealController? = null
  compose.setContent { ThemeRevealHost(preferences.value) {
   reveal = LocalThemeReveal.current
   Box(Modifier.fillMaxSize()) { androidx.compose.material3.Button(modifier = Modifier.offset(90.dp, 260.dp).recordThemeTouch(), onClick = { val next = preferences.value.copy(theme = "light"); reveal!!.capture(next); preferences.value = next }) { androidx.compose.material3.Text("Change theme") } }
  } }
  val button = compose.onNodeWithText("Change theme").fetchSemanticsNode().boundsInRoot
  compose.mainClock.autoAdvance = false
  compose.onNodeWithText("Change theme").performTouchInput { click() }
  compose.mainClock.advanceTimeBy(16)
  compose.runOnIdle { val center = reveal!!.snapshot!!.center; assertTrue(kotlin.math.abs(center.x - button.center.x) < 5f); assertTrue(kotlin.math.abs(center.y - button.center.y) < 5f) }
  compose.mainClock.advanceTimeBy(600)
  compose.mainClock.autoAdvance = true
 }
 @Test fun onlyThemeUsesThreeButtonsAndLanguageExpandsInline() {
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true))) } }
  compose.onNodeWithText(app.getString(R.string.ui_71)).performClick()
  listOf(R.string.ui_92, R.string.ui_93, R.string.ui_94).forEach { compose.onNodeWithText(app.getString(it)).assertIsDisplayed() }
  compose.onNodeWithText("English").assertDoesNotExist()
  compose.onNodeWithText(app.getString(R.string.settings_language)).performClick()
  compose.onNodeWithText("English").assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.settings_language)).performClick()
  compose.waitForIdle(); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
  compose.onNodeWithText("English").assertDoesNotExist()
 }
 @Test fun themeRevealsNewColorsFromTouchAndClearsSnapshot() {
  val preferences = androidx.compose.runtime.mutableStateOf(Preferences(theme = "dark"))
  var reveal: ThemeRevealController? = null
  var view: android.view.View? = null
  compose.setContent { ThemeRevealHost(preferences.value) { reveal = LocalThemeReveal.current; view = androidx.compose.ui.platform.LocalView.current; Box(Modifier.fillMaxSize().background(if(preferences.value.theme == "dark") androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White)) } }
  compose.waitForIdle()
  compose.mainClock.autoAdvance = false
  compose.runOnIdle { val next = preferences.value.copy(theme = "light"); reveal!!.record(view!!, androidx.compose.ui.geometry.Offset(120f, 160f)); reveal!!.capture(next); preferences.value = next }
  compose.mainClock.advanceTimeBy(128)
  val pixels = compose.onRoot().captureToImage().toPixelMap()
  assertTrue(pixels[120, 160].red > .9f)
  assertTrue(pixels[pixels.width - 100, pixels.height - 100].red < .1f)
  compose.mainClock.advanceTimeBy(600)
  compose.runOnIdle { assertNull(reveal!!.snapshot) }
  compose.mainClock.autoAdvance = true
 }
 @Test fun languageSelectionComesBeforeRootOnFirstLaunch() {
  compose.setContent { USBDroidTheme(Preferences()) { SetupScreen(AppState(settingsLoaded = true), AppViewModel(app), {}, {}) } }
  compose.onNodeWithText(app.getString(R.string.setup_language_title)).assertIsDisplayed()
  compose.onNodeWithText("Русский").assertIsDisplayed()
  compose.onNodeWithText("English").assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.request_root)).assertDoesNotExist()
 }
 @Test fun mountedImageOffersEjectWithoutMountButtons() {
  val image = ImageEntry("mounted", "Mounted fixture", "/fixture.img", "/fixture.img", 4194304, 0, "default")
  val lun = dev.usbdroid.usb.Lun("fixture-lun", image.physicalPath!!, false, false, true, true)
  var ejects = 0
  compose.setContent { USBDroidTheme(Preferences()) { Box(Modifier.safeDrawingPadding()) { ImageLibraryRow(image, AppState(usb = dev.usbdroid.usb.UsbStatus(root = true, luns = listOf(lun))), expanded = true, onExpandedChange = {}, host = { _, _, _ -> fail("Already mounted") }, eject = { ejects++ }, action = {}, recalculate = {}) } } }
  listOf(R.string.host_ro, R.string.host_rw, R.string.host_cd).forEach { compose.onNodeWithText(app.getString(it)).assertDoesNotExist() }
  compose.onNodeWithText(app.getString(R.string.ui_90)).performClick()
  compose.runOnIdle { assertEquals(1, ejects) }
 }
 @Test fun connectedImagesStayFirstAndReturnToSelectedOrderWhenEjected() {
  val images = listOf("A", "B", "C", "D").mapIndexed { index, name -> ImageEntry(name, name, "/$name.img", "/$name.img", index.toLong(), index.toLong(), "default") }
  val hosted = setOf("/B.img", "/D.img")
  assertEquals(listOf("B", "D", "A", "C"), sortedImages(images, "name", false, hosted).map { it.id })
  assertEquals(listOf("D", "B", "C", "A"), sortedImages(images, "size", true, hosted).map { it.id })
  assertEquals(listOf("D", "C", "B", "A"), sortedImages(images, "size", true).map { it.id })
 }
 @Test fun onlyOneImageMountChoiceStaysExpanded() {
  val images = listOf("First", "Second").map { ImageEntry(it, it, "/$it.img", "/$it.img", 4194304, 0, "default") }
  val lun = dev.usbdroid.usb.Lun("fixture-lun", "", true, false, true, true)
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(images = images, settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true, luns = listOf(lun)))) } }
  compose.onNodeWithText("First").performClick()
  compose.onAllNodesWithText(app.getString(R.string.usb_host_selection)).assertCountEquals(1)
  compose.onNodeWithText("Second").performClick()
  compose.waitForIdle(); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
  compose.onAllNodesWithText(app.getString(R.string.usb_host_selection)).assertCountEquals(1)
  compose.onNodeWithContentDescription(app.getString(R.string.sort_title)).performClick()
  compose.waitForIdle(); compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
  compose.onAllNodesWithText(app.getString(R.string.usb_host_selection)).assertCountEquals(0)
 }
 @Test fun scrollPositionsSurviveTabsAndSettingsSubmenus() {
  val images = (1..80).map { ImageEntry("image-$it", "Image ${it.toString().padStart(2, '0')}", "/fixture-$it.img", "/fixture-$it.img", 4194304, 0, "default") }
  val downloads = (1..60).map { TransferJob("download-$it", "DOWNLOAD", "Download $it", "{}", state = "PAUSED", createdAt = it.toLong()) }
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(images = images, jobs = downloads, settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true))) } }
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Image 40"))
  compose.onNodeWithText(app.getString(R.string.ui_70)).performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Download 30"))
  compose.onNodeWithText(app.getString(R.string.ui_69)).performClick()
  compose.onNodeWithText("Image 40").assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.ui_71)).performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.getString(R.string.ui_106)))
  compose.onNodeWithText(app.getString(R.string.ui_106)).performClick()
  compose.onNodeWithContentDescription(app.getString(R.string.create_back)).performClick()
  compose.onNodeWithText(app.getString(R.string.ui_106)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.ui_70)).performClick()
  compose.onNodeWithText("Download 30").assertIsDisplayed()
 }
 @Test fun eachTabRetainsItsOwnSearch() {
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true))) } }
  compose.onNode(hasSetTextAction()).performTextInput("memtest")
  compose.onNodeWithText(app.getString(R.string.ui_70)).performClick()
  compose.onNode(hasSetTextAction()).performTextInput("linux")
  compose.onNodeWithText(app.getString(R.string.ui_69)).performClick()
  compose.onNode(hasSetTextAction()).assertTextContains("memtest")
  compose.onNodeWithText(app.getString(R.string.ui_70)).performClick()
  compose.onNode(hasSetTextAction()).assertTextContains("linux")
 }
 @Test fun checksumIsAnImageResultRatherThanATransferCard() {
  val hash = "a".repeat(64)
  compose.setContent { USBDroidTheme(Preferences()) { ChecksumResult(TransferJob("hash", "CHECKSUM", "Task title", "{}", state = "DONE", result = "SHA-256\n$hash")) } }
  compose.onNodeWithText(hash).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.checksum_copy)).assertDoesNotExist()
  compose.onNodeWithText("Task title").assertDoesNotExist()
  compose.onNodeWithText("100%").assertDoesNotExist()
 }
 @Test fun downloadsShowOnlyDownloadJobs() {
  val jobs = listOf(TransferJob("hash", "CHECKSUM", "Hash operation", "{}", state = "DONE"), TransferJob("download", "DOWNLOAD", "ISO download", "{}", state = "PAUSED"))
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true), jobs = jobs)) } }
  compose.onNodeWithText(app.getString(R.string.ui_70)).performClick()
  compose.onNodeWithText("ISO download").assertIsDisplayed()
  compose.onNodeWithText("Hash operation").assertDoesNotExist()
  compose.onNodeWithText(app.getString(R.string.usb_idle)).assertDoesNotExist()
  compose.onNodeWithText(app.getString(R.string.ui_71)).performClick()
  compose.onNodeWithText(app.getString(R.string.usb_idle)).assertDoesNotExist()
 }
 @Test fun imageTapSelectsMountModeAndMoreMenuCopiesOrRecalculates() {
  val image = ImageEntry("ui-fixture", "Fixture", "/fixture.img", "/fixture.img", 4194304, 0, "default")
  val hash = "b".repeat(64)
  val checksum = TransferJob("checksum", "CHECKSUM", "SHA-256", JSONObject().put("image", image.id).put("algorithm", "SHA-256").toString(), state = "DONE", result = "SHA-256\n$hash")
  val lun = dev.usbdroid.usb.Lun("fixture-lun", "", true, false, true, true)
  val otherLun = lun.copy(path = "second-fixture-lun")
  var selected: dev.usbdroid.usb.HostMode? = null; var selectedHost: String? = null; var recalculations = 0
  val expanded = androidx.compose.runtime.mutableStateOf(false)
  compose.setContent { USBDroidTheme(Preferences()) { ImageLibraryRow(image, AppState(jobs = listOf(checksum), usb = dev.usbdroid.usb.UsbStatus(root = true, luns = listOf(lun, otherLun))), expanded = expanded.value, onExpandedChange = { expanded.value = it }, host = { host, mode, done -> selected = mode; selectedHost = host.path; done() }, eject = {}, action = {}, recalculate = { recalculations++ }) } }
  compose.onNodeWithText("Fixture").performClick()
  compose.onNodeWithText(app.getString(R.string.usb_host_selection)).assertIsDisplayed()
  compose.onNodeWithText(lun.title).assertIsDisplayed()
  compose.onNodeWithText(otherLun.title).performClick()
  compose.onNodeWithText(app.getString(R.string.host_cd)).performClick()
  compose.runOnIdle { assertEquals(dev.usbdroid.usb.HostMode.CDROM, selected); assertEquals(otherLun.path, selectedHost) }
  compose.onNodeWithContentDescription(app.getString(R.string.ui_80)).performClick()
  compose.onNodeWithText("SHA-256").performClick()
  compose.runOnIdle { assertEquals(0, recalculations) }
  compose.onNodeWithText("SHA-256").performTouchInput { longClick() }
  compose.runOnIdle { assertEquals(1, recalculations) }
 }
 @Test fun bannerShowsKernelHostingStateAndOperationProgress() {
  val image = ImageEntry("fixture", "Fixture", "/fixture.img", "/fixture.img", 100, 0, "default")
  val state = androidx.compose.runtime.mutableStateOf(AppState(images = listOf(image), operations = setOf("settings", "storage", "repository")))
  compose.setContent { USBDroidTheme(Preferences()) { Box(Modifier.safeDrawingPadding()) { StatusBanner(state.value) } } }
  compose.onNodeWithText(app.getString(R.string.usb_idle)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.operation_save)).assertDoesNotExist()
  compose.runOnIdle { state.value = state.value.copy(usb = dev.usbdroid.usb.UsbStatus(root = true, luns = listOf(dev.usbdroid.usb.Lun("fixture-lun", image.physicalPath!!, true, false, true, true))), jobs = listOf(TransferJob("task", "CREATE", "Fixture", "{}", state = "RUNNING", progress = 25, total = 100))) }
  compose.waitForIdle()
  compose.mainClock.advanceTimeBy(500)
  compose.waitForIdle()
  compose.onNodeWithText(app.getString(R.string.usb_hosted_count, 1)).assertIsDisplayed()
  compose.onNodeWithText("Fixture · ${app.getString(R.string.host_ro)}").assertIsDisplayed()
  compose.onNodeWithText("25%").assertIsDisplayed()
 }
 @Test fun welcomeExplainsRootBeforeAskingForPermissions() {
  val title = app.getString(R.string.welcome_title)
  compose.setContent { USBDroidTheme(Preferences()) { SetupScreen(AppState(settingsLoaded = true, preferences = Preferences(setupStep = 0)), AppViewModel(app), {}, {}) } }
  compose.onNodeWithText(title).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.request_root)).assertIsDisplayed()
 }
 @Test fun noRootBlocksMainInterface() {
  compose.setContent { USBDroidTheme(Preferences()) { RootRequiredScreen(AppState(rootChecked = true), AppViewModel(app)) } }
  compose.onNodeWithText(app.getString(R.string.root_required_title)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.retry_root)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.close_app)).assertIsDisplayed()
 }
 @Test fun themesAndAmoledUseCorrectSurface() {
  var background = 1L
  compose.setContent { USBDroidTheme(Preferences(theme = "dark", amoled = true)) { background = androidx.compose.material3.MaterialTheme.colorScheme.background.value.toLong() } }
  compose.waitForIdle(); assertEquals(androidx.compose.ui.graphics.Color.Black.value.toLong(), background)
 }
 @Test fun creationUsesFullScreenAndValidatesBeforeStarting() {
  compose.setContent { USBDroidTheme(Preferences()) { CreateImageScreen(listOf(StorageLocation("default", "Images", "/tmp", "FILE", true)), {}, { _, _, _, _, _ -> }) } }
  compose.onNodeWithText(app.getString(R.string.create_title)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.create_button)).assertIsNotEnabled()
  compose.onNodeWithText(app.getString(R.string.create_filename)).performTextInput("test-volume")
  compose.onNodeWithText(app.getString(R.string.create_button)).assertIsEnabled()
 }
 @Test fun repeatedRootDiscoveryDoesNotReplaceTheMainInterfaceWithASpinner() {
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(settingsLoaded = true, rootChecked = true, rootBusy = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true))) } }
  compose.onNodeWithText(app.getString(R.string.ui_71)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.ui_71)).performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(app.getString(R.string.ui_87)))
  compose.onNodeWithText(app.getString(R.string.ui_87)).performClick()
  compose.onNodeWithText(app.getString(R.string.setup_title)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.setup_language_title)).assertIsDisplayed()
 }
 @Test fun busyButtonBlocksRepeatClickAndReenablesWhenFinished() {
  val busy = androidx.compose.runtime.mutableStateOf(false); var clicks = 0
  compose.setContent { USBDroidTheme(Preferences()) { BusyButton("Run operation", busy.value, { clicks++; busy.value = true }) } }
  compose.onNodeWithText("Run operation").performClick().assertIsNotEnabled()
  compose.onNodeWithContentDescription(app.getString(R.string.operation_in_progress)).assertIsDisplayed()
  compose.onNodeWithText("Run operation").performTouchInput { click() }; compose.runOnIdle { assertEquals(1, clicks); busy.value = false }
  compose.onNodeWithText("Run operation").assertIsEnabled().performClick()
  compose.runOnIdle { assertEquals(2, clicks) }
 }
 @Test fun imageMenusAndSortingStayAboveTheList() {
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), AppState(settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true), usb = dev.usbdroid.usb.UsbStatus(root = true))) } }
  compose.onNodeWithText("USB").assertDoesNotExist()
  compose.onNodeWithContentDescription(app.getString(R.string.sort_title)).performClick()
  compose.onNodeWithText(app.getString(R.string.sort_descending)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.ui_71)).assertExists()
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
  compose.waitForIdle()
  compose.onNodeWithContentDescription(app.getString(R.string.ui_73)).performClick()
  compose.onNodeWithText(app.getString(R.string.ui_108)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.ui_71)).assertExists()
 }
 @Test fun duplicateCreationStartsOneWorkerAndClearsBusyAfterCompletion() = runBlocking {
  app.library.initialize()
  val model = AppViewModel(app); val name = "duplicate-${UUID.randomUUID()}.img"
  val completed = java.util.concurrent.atomic.AtomicInteger()
  compose.runOnIdle {
   model.createAt(name, 4, true, false, "default") { completed.incrementAndGet() }
   assertTrue(model.state.value.working("create"))
   model.createAt(name, 4, true, false, "default") { completed.incrementAndGet() }
  }
  compose.waitUntil(60000) { completed.get() > 0 && !model.state.value.working("create") }
  val matching = app.database.dao().jobs().first().filter { it.title == name }
  assertEquals(1, matching.size); assertEquals(1, completed.get()); assertEquals("DONE", matching.single().state)
  val image = app.database.dao().image(matching.single().result)!!
  File(image.physicalPath!!).delete(); app.database.dao().deleteImage(image.id); deleteOperation(matching.single().id)
 }
 @Test fun downloadSortingChangesDoNotChangeLibrarySorting() {
  var saved: Preferences? = null
  val preferences = androidx.compose.runtime.mutableStateOf(Preferences(imageSort = "date", imageDescending = true))
  compose.setContent { USBDroidTheme(Preferences()) { SortMenu(preferences.value, true, true) { saved = it; preferences.value = it } } }
  compose.onNodeWithContentDescription(app.getString(R.string.sort_title)).performClick()
  compose.onNodeWithText(app.getString(R.string.sort_version)).performClick()
  compose.onNodeWithText(app.getString(R.string.sort_descending)).performClick()
  compose.runOnIdle { assertEquals("version", saved!!.downloadSort); assertTrue(saved!!.downloadDescending); assertEquals("date", saved!!.imageSort); assertTrue(saved!!.imageDescending) }
 }
 @Test fun formattedCreationCommitsEveryFormatAndReadyHashes() = runBlocking {
  app.library.initialize()
  for(format in ImageFilesystem.entries.filter { it != ImageFilesystem.NONE }) {
   val job = runJob("CREATE", JSONObject().put("name", "format-test-${format.name}-${UUID.randomUUID()}.img").put("mib", format.minMiB).put("filesystem", format.name).put("storage", "default"))
   try {
    assertEquals(job.error, "DONE", job.state)
    val image = app.database.dao().image(job.result)!!
    try { verifyReadyHashes(image) } finally { File(image.physicalPath!!).delete(); app.database.dao().deleteImage(image.id) }
   } finally { deleteOperation(job.id) }
  }
  Unit
 }
 private suspend fun runJob(kind: String, args: JSONObject): TransferJob {
  val id = UUID.randomUUID().toString(); app.database.dao().putJob(TransferJob(id, kind, "Device verification", args.toString()))
  val request = OneTimeWorkRequestBuilder<ImageWorker>().setInputData(workDataOf("id" to id)).build()
  WorkManager.getInstance(app).enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, request).result.get()
  repeat(150) { val job = app.database.dao().job(id)!!; if(job.state in listOf("DONE", "FAILED")) return job; delay(200) }
  error("Worker timeout")
 }
 private suspend fun deleteOperation(id: String) { app.database.dao().jobs().first().filter { it.id == id || it.id.startsWith("$id:hash:") }.forEach { app.database.dao().deleteJob(it.id) } }
 private suspend fun verifyReadyHashes(image: ImageEntry) {
  val bytes = File(image.physicalPath!!).readBytes()
  listOf("SHA-256", "SHA-1", "MD5").forEach { algorithm ->
   val saved = latestChecksum(app.database.dao().jobs().first(), image, algorithm)!!
   val expected = MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }
   assertEquals("DONE", saved.state); assertEquals("$algorithm\n$expected", saved.result)
  }
 }
 @Test fun completedDownloadHasAllThreeReadyHashes() = runBlocking {
  app.library.initialize()
  val payload = ByteArray(65536) { (it % 251).toByte() }
  app.database.dao().jobs().first().filter { it.title == "Device verification" && it.kind == "DOWNLOAD" && it.state == "FAILED" && runCatching { JSONObject(it.args).getString("url").startsWith("http://127.0.0.1:") && JSONObject(it.args).getString("name").startsWith("download-") }.getOrDefault(false) }.forEach { deleteOperation(it.id) }
  val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
  val responder = Thread { runCatching { server.accept().use { socket ->
   val reader = socket.getInputStream().bufferedReader(); while(!reader.readLine().isNullOrEmpty()) {}
   socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nETag: fixture-v1\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
  } } }.apply { isDaemon = true; start() }
  try {
   val job = runJob("DOWNLOAD", JSONObject().put("url", "http://127.0.0.1:${server.localPort}/fixture.iso").put("name", "download-${UUID.randomUUID()}.iso").put("allowHttp", true).put("storage", "default"))
   assertEquals(job.error, "DONE", job.state)
   val image = app.database.dao().image(job.result)!!; assertArrayEquals(payload, File(image.physicalPath!!).readBytes()); verifyReadyHashes(image)
   File(image.physicalPath).delete(); app.database.dao().deleteImage(image.id); deleteOperation(job.id)
  } finally { server.close(); responder.join(1000) }
 }
 @Test fun automaticHybridKeepsExportableOriginalOutsideLibrary() = runBlocking {
  app.library.initialize()
  val payload = ByteArray(65536) { (it % 251).toByte() }
  val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
  val responder = Thread { runCatching { server.accept().use { socket ->
   val reader = socket.getInputStream().bufferedReader(); while(!reader.readLine().isNullOrEmpty()) {}
   socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(payload); flush() }
  } } }.apply { isDaemon = true; start() }
  var image: ImageEntry? = null; var backup: File? = null; var job: TransferJob? = null; var exportJob: TransferJob? = null
  val output = File(app.cacheDir, "original-export-${UUID.randomUUID()}.iso")
  try {
   job = runJob("DOWNLOAD", JSONObject().put("url", "http://127.0.0.1:${server.localPort}/fixture.iso").put("name", "hybrid-original-${UUID.randomUUID()}.iso").put("allowHttp", true).put("hybrid", true).put("storage", "default"))
   assertEquals(job.error, "DONE", job.state); assertTrue(job.error.startsWith("isohybrid:"))
   image = app.database.dao().image(job.result)!!; backup = File(JSONObject(job.args).getString("_original"))
   assertArrayEquals(payload, backup.readBytes()); assertArrayEquals(payload, File(image.physicalPath!!).readBytes())
   assertFalse(app.database.dao().allImages().any { it.physicalPath == backup.path })
   exportJob = runJob("EXPORT", JSONObject().put("uriSource", backup.path).put("uri", output.path))
   assertEquals(exportJob.error, "DONE", exportJob.state); assertArrayEquals(payload, output.readBytes())
  } finally {
   server.close(); responder.join(1000); output.delete(); backup?.delete()
   image?.let { File(it.physicalPath!!).delete(); app.database.dao().deleteImage(it.id) }
   job?.let { deleteOperation(it.id) }; exportJob?.let { deleteOperation(it.id) }
  }
  Unit
 }
 @Test fun createsRealFatVolumeThroughBackgroundWorker() = runBlocking {
  app.library.initialize()
  val name = "device-test-${UUID.randomUUID()}.img"
  val job = runJob("CREATE", JSONObject().put("name", name).put("mib", 4).put("fat", true).put("allocate", false).put("storage", "default").put("testBoot", true))
  assertEquals(job.error, "DONE", job.state)
  val image = app.database.dao().image(job.result)!!; assertEquals(4194304L, image.size)
  val bytes = File(image.physicalPath!!).inputStream().use { input -> ByteArray(512).also { input.read(it) } }
  assertEquals(0x55, bytes[510].toInt() and 255); assertEquals(0xaa, bytes[511].toInt() and 255)
  assertTrue(String(bytes, Charsets.ISO_8859_1).contains("USBDroid boot test successful"))
  verifyReadyHashes(image)
  File(image.physicalPath).copyTo(File(app.cacheDir, "verified-test-boot.img"), overwrite = true)
  File(image.physicalPath).delete(); app.database.dao().deleteImage(image.id); deleteOperation(job.id)
 }
 @Test fun importAndExportPreserveBytes() = runBlocking {
  app.library.initialize()
  val source = File(app.cacheDir, "roundtrip-${UUID.randomUUID()}.img").apply { writeBytes(ByteArray(8192) { (it % 251).toByte() }) }
  val job = runJob("IMPORT", JSONObject().put("name", source.name).put("uri", source.path).put("size", source.length()).put("storage", "default"))
  assertEquals(job.error, "DONE", job.state); val image = app.database.dao().image(job.result)!!
  val output = File(app.cacheDir, "export-${UUID.randomUUID()}.img")
  val exported = runJob("EXPORT", JSONObject().put("image", image.id).put("uri", output.path))
  assertEquals(exported.error, "DONE", exported.state); assertArrayEquals(source.readBytes(), output.readBytes())
  source.delete(); output.delete(); File(image.physicalPath!!).delete(); app.database.dao().deleteImage(image.id); deleteOperation(job.id); app.database.dao().deleteJob(exported.id)
 }
}
