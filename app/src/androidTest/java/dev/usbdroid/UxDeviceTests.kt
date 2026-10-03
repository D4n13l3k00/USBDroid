package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.usbdroid.data.*
import dev.usbdroid.ui.*
import dev.usbdroid.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UxDeviceTests {
 @get:Rule val compose = createComposeRule()
 private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.app
 private fun fixture(id: String) = ImageEntry(id, id, "/$id.img", "/$id.img", 4194304, 0, "default")
 private fun state(images: List<ImageEntry>, favorites: Set<String> = emptySet()) = AppState(images = images, settingsLoaded = true, rootChecked = true, preferences = Preferences(welcomeComplete = true, favorites = favorites), usb = UsbStatus(root = true))
 @Test fun locationClassificationUsesPathBoundaries() {
  val own = fixture("own").copy(location = File(app.cacheDir, "own.img").path, physicalPath = File(app.cacheDir, "own.img").path)
  assertTrue(own.isAppFile(app))
  assertFalse(own.copy(physicalPath = app.cacheDir.path + "-external/image.img").isAppFile(app))
  assertFalse(own.copy(physicalPath = null, location = "content://documents/image").isAppFile(app))
 }
 @Test fun mountedSelectionCannotDeleteFiles() {
  val image = fixture("Mounted")
  val connected = AppState(usb = UsbStatus(root = true, luns = listOf(Lun("fixture", image.physicalPath!!, true, false, true, true))))
  compose.setContent { USBDroidTheme(Preferences()) { SelectionActions(listOf(image), connected, {}, {}, {}, { fail("Must not delete") }) } }
  compose.onNodeWithText(app.getString(R.string.selection_delete)).assertIsNotEnabled()
 }
 @Test fun bulkDeletionRemovesOnlySelectedFixtureFiles() = runBlocking {
  val dao = app.database.dao()
  val files = (1..3).map { File(app.cacheDir, "delete-test-${UUID.randomUUID()}.img").apply { writeBytes(byteArrayOf(1,2,3)) } }
  val images = files.map { fixture(UUID.randomUUID().toString()).copy(location = it.path, physicalPath = it.path, size = 3) }
  try {
   images.forEach { dao.putImage(it) }
   val model = AppViewModel(app)
   compose.waitUntil(10000) { model.state.value.images.any { it.id == images[0].id } }
   compose.runOnIdle { model.deleteImages(images.take(2)) }
   compose.waitUntil(30000) { model.state.value.notice != null || model.state.value.message != null }
   assertNull(model.state.value.message)
   assertFalse(files[0].exists()); assertFalse(files[1].exists()); assertTrue(files[2].exists())
   assertNull(dao.image(images[0].id)); assertNull(dao.image(images[1].id)); assertNotNull(dao.image(images[2].id))
  } finally { images.forEach { dao.deleteImage(it.id) }; files.forEach { it.delete() } }
 }
 @Test fun bulkDeleteOpensConfirmationAndCanBeCancelled() {
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), state(listOf(fixture("External")))) } }
  compose.onNodeWithText("External").performTouchInput { longClick() }
  compose.onNodeWithText(app.getString(R.string.selection_delete)).performClick()
  compose.onNodeWithText(app.getString(R.string.delete_external_warning)).assertIsDisplayed()
  compose.onNodeWithText("/External.img").assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.ui_10)).performClick()
  compose.onNodeWithText(app.getString(R.string.delete_external_warning)).assertDoesNotExist()
  compose.onNodeWithText(app.getString(R.string.selection_count, 1)).assertIsDisplayed()
 }
 @Test fun holdingACardSelectsImagesWithoutOpeningMountControls() {
  val images = listOf(fixture("Alpha"), fixture("Beta"))
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), state(images)) } }
  compose.onNodeWithText("Alpha").performTouchInput { longClick() }
  compose.onNodeWithText(app.getString(R.string.selection_count, 1)).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.usb_host_selection)).assertDoesNotExist()
  compose.onNodeWithText("Beta").performClick()
  compose.onNodeWithText(app.getString(R.string.selection_count, 2)).assertIsDisplayed()
  compose.onNodeWithContentDescription(app.getString(R.string.selection_clear)).performClick()
  compose.onNodeWithText("USBDroid").assertIsDisplayed()
 }
 @Test fun favoritesFilterShowsOnlySavedImages() {
  compose.setContent { USBDroidTheme(Preferences()) { App(AppViewModel(app), state(listOf(fixture("Favorite"), fixture("Other")), setOf("Favorite"))) } }
  compose.onNodeWithText(app.getString(R.string.favorites_only)).performClick()
  compose.onNodeWithText("Favorite").assertIsDisplayed(); compose.onNodeWithText("Other").assertDoesNotExist()
  compose.onNodeWithText(app.getString(R.string.favorites_only)).performClick(); compose.onNodeWithText("Other").assertIsDisplayed()
 }
 @Test fun mountedSelectionCannotRemoveLibraryEntries() {
  val image = fixture("Mounted")
  val connected = AppState(usb = UsbStatus(root = true, luns = listOf(Lun("fixture", image.physicalPath!!, true, false, true, true))))
  compose.setContent { USBDroidTheme(Preferences()) { SelectionActions(listOf(image), connected, {}, { fail("Must not hide mounted image") }, {}) } }
  compose.onNodeWithText(app.getString(R.string.selection_hide)).assertIsNotEnabled()
  compose.onNodeWithText(app.getString(R.string.selection_eject)).assertIsEnabled()
 }
 @Test fun unsupportedCdModeHasSpecificExplanation() {
  val image = fixture("Image")
  compose.setContent { USBDroidTheme(Preferences()) { ImageLibraryRow(image, AppState(usb = UsbStatus(root = true, luns = listOf(Lun("fixture", "", readOnly = true, cdrom = false, supportsReadOnly = true, supportsCdrom = false)))), true, {}, host = { _, _, _ -> }, eject = {}, action = {}, recalculate = {}) } }
  compose.onNodeWithText(app.getString(R.string.host_cd)).assertIsNotEnabled()
  compose.onNodeWithText(app.getString(R.string.host_cd_reason)).assertIsDisplayed()
 }
 @Test fun progressShowsSpeedEtaAndCancellation() {
  var cancelled = ""
  val job = TransferJob("fixture", "DOWNLOAD", "Example", JSONObject().put("_speed", 2097152).put("_eta", 75).put("_phase", "downloading").toString(), "RUNNING", 1048576, 4194304)
  compose.setContent { USBDroidTheme(Preferences()) { StatusBanner(AppState(jobs = listOf(job))) { cancelled = it } } }
  compose.onNodeWithText(app.getString(R.string.speed_value, "2.0 MiB") + " · " + app.getString(R.string.eta_minutes, 2L)).assertIsDisplayed()
  compose.onNodeWithContentDescription(app.getString(R.string.ui_131)).performClick()
  compose.runOnIdle { assertEquals("fixture", cancelled) }
 }
 @Test fun errorsHideTechnicalTextUntilRequested() {
  val details = "HTTP 404 fixture details"
  compose.setContent { USBDroidTheme(Preferences()) { ErrorExplanation(details) } }
  compose.onNodeWithText(app.getString(R.string.error_network)).assertIsDisplayed()
  compose.onNodeWithText(details).assertDoesNotExist()
  compose.onNodeWithText(app.getString(R.string.error_details)).performClick()
  compose.onNodeWithText(details).assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.error_copy)).assertIsDisplayed()
 }
 @Test fun choosingFilesystemShowsItsLimitsAndCompatibility() {
  compose.setContent { USBDroidTheme(Preferences()) { CreateImageScreen(listOf(StorageLocation("default", "Images", "/tmp", "FILE", true)), {}, { _, _, _, _, _ -> }) } }
  compose.onNodeWithText("exFAT").performScrollTo().performClick()
  compose.onNodeWithText(app.getString(R.string.format_exfat_hint)).performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("FAT32").performScrollTo().performClick()
  compose.onNodeWithText(app.getString(R.string.format_fat32_hint)).performScrollTo().assertIsDisplayed()
  compose.onNodeWithText(app.getString(R.string.create_size)).performScrollTo().performTextClearance()
  compose.onNodeWithText(app.getString(R.string.create_size)).performTextInput("32")
  compose.onNodeWithText(app.getString(R.string.create_filename)).performScrollTo().performTextInput("fixture")
  compose.onNodeWithText(app.getString(R.string.create_button)).assertIsNotEnabled()
 }
 @Test fun preferencesPreserveFavoritesAndLastHostMode() = runBlocking {
  val previous = app.settings.flow.first()
  val id = "ux-favorite-${UUID.randomUUID()}"
  try {
   app.settings.favorites(setOf(id), true); app.settings.rememberHost("CDROM")
   app.settings.save(previous.copy(theme = previous.theme))
   val stored = app.settings.flow.first(); assertTrue(id in stored.favorites); assertEquals("CDROM", stored.lastHostMode)
  } finally { app.settings.favorites(setOf(id), false); app.settings.rememberHost(previous.lastHostMode) }
 }
 @Test fun removingEntriesCanBeUndoneWithoutChangingFiles() = runBlocking {
  val files = (1..2).map { File(app.cacheDir, "ux-hide-${UUID.randomUUID()}.img").apply { writeText("Keep these bytes") } }
  val images = files.map { ImageEntry(UUID.randomUUID().toString(), it.name, it.path, it.canonicalPath, it.length(), it.lastModified(), "default") }
  val model = AppViewModel(app)
  try {
   images.forEach { app.database.dao().putImage(it) }
   compose.waitUntil(10000) { model.state.value.images.any { it.id == images[0].id } }
   compose.runOnIdle { model.hideImages(images) }
   compose.waitUntil(20000) { model.state.value.undoRemoval != null }
   images.forEach { assertTrue(app.database.dao().image(it.id)!!.hidden) }
   val token = model.state.value.undoRemoval!!.token
   compose.runOnIdle { model.undoRemoval(token) }
   compose.waitUntil(10000) { model.state.value.undoRemoval == null }
   images.forEach { assertFalse(app.database.dao().image(it.id)!!.hidden) }
   files.forEach { assertEquals("Keep these bytes", it.readText()) }
  } finally { images.forEach { app.database.dao().deleteImage(it.id) }; files.forEach { it.delete() } }
  Unit
 }
 @Test fun downloadCopiesAvoidExistingAndQueuedFilenames() = runBlocking {
  app.library.initialize()
  val id = UUID.randomUUID().toString(); val source = File(app.library.directory, "copy-$id.iso").apply { writeText("Preserve original") }
  val second = File(app.library.directory, "copy-$id (2).iso").apply { writeText("Preserve second") }
  try {
   app.database.dao().putJob(TransferJob(id, "DOWNLOAD", "Queued fixture", JSONObject().put("name", "copy-$id (3).iso").toString()))
   assertEquals("copy-$id (4).iso", app.library.copyFilename(source.name, "default"))
   assertEquals("Preserve original", source.readText()); assertEquals("Preserve second", second.readText())
  } finally { app.database.dao().deleteJob(id); source.delete(); second.delete() }
  Unit
 }
 @Test fun latePauseAndCancelDoNotChangeCompletedJobs() = runBlocking {
  val id = UUID.randomUUID().toString(); val model = AppViewModel(app)
  try {
   app.database.dao().putJob(TransferJob(id, "DOWNLOAD", "Completed fixture", "{}", state = "DONE"))
   compose.runOnIdle { model.pause(id) }; compose.waitUntil(10000) { !model.state.value.working("job:$id") }
   assertEquals("DONE", app.database.dao().job(id)!!.state)
   compose.runOnIdle { model.cancel(id) }; compose.waitUntil(10000) { !model.state.value.working("job:$id") }
   assertEquals("DONE", app.database.dao().job(id)!!.state)
  } finally { app.database.dao().deleteJob(id) }
  Unit
 }
 @Test fun completedDownloadRequestsConfirmationBeforeMakingAnotherJob() = runBlocking {
  val id = UUID.randomUUID().toString(); val file = File(app.cacheDir, "ux-duplicate-$id.iso").apply { writeText("Fixture") }
  val image = ImageEntry(id, file.name, file.path, file.canonicalPath, file.length(), 0, "default")
  val release = Release("Fixture", "1", "arm64", "https://example.invalid/${file.name}", file.length())
  val job = TransferJob(id, "DOWNLOAD", "Fixture", JSONObject().put("url", release.url).toString(), state = "DONE", result = id)
  val model = AppViewModel(app)
  try {
   app.database.dao().putImage(image); app.database.dao().putJob(job)
   compose.waitUntil(10000) { model.state.value.images.any { it.id == id } && model.state.value.jobs.any { it.id == id } }
   val before = app.database.dao().jobs().first().size
   compose.runOnIdle { model.download(release) }
   assertEquals(release, model.state.value.duplicateDownload)
   assertEquals(before, app.database.dao().jobs().first().size)
   model.clearDuplicate()
  } finally { app.database.dao().deleteImage(id); app.database.dao().deleteJob(id); file.delete() }
  Unit
 }
}
