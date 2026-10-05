package dev.usbdroid

import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.usbdroid.data.ImageFilesystem
import dev.usbdroid.files.FolderImage
import dev.usbdroid.files.ImageAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FolderImageDeviceTest {
 @Test fun copyMoveAndConflictPreview() = runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("folderFeatures") == "true")
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  val metadata = File(app.filesDir, "folder-image.json")
  assumeTrue("Keep the user's temporary image", !metadata.exists())
  val source = File(app.cacheDir, "copyback-test-${UUID.randomUUID()}").apply { mkdir() }
  File(source, "existing.txt").writeText("original")
  var image: dev.usbdroid.data.ImageEntry? = null
  var mountId: String? = null
  try {
   val created = FolderImage(app).create(source.path, "Copyback test", ImageFilesystem.FAT32, 16) {}
   image = created
   val access = app.imageAccess; val mount = access.mount(created.physicalPath!!, created.title, false); mountId = mount.id
   val before = access.readText(mount.id, "existing.txt")
   access.writeText(mount.id, "existing.txt", "changed", before.second)
   access.create(mount.id, "", "copies", true)
   access.transfer(mount.id, listOf("existing.txt"), "copies", false)
   assertEquals("changed", access.readText(mount.id, "copies/existing.txt").first)
   access.create(mount.id, "", "moved", true)
   access.transfer(mount.id, listOf("copies/existing.txt"), "moved", true)
   assertTrue(access.children(mount.id, "copies").isEmpty())
   access.unmount(mount.id); mountId = null
   val copier = dev.usbdroid.files.FolderCopyBack(app)
   val plan = copier.preview(created)
   assertTrue(plan.items.any { it.relative == "existing.txt" && it.targetHash != null })
   copier.apply(plan, false)
   assertEquals("original", File(source, "existing.txt").readText())
   assertEquals("changed", File(source, "moved/existing.txt").readText())
   assertTrue(File(source, "copies").isDirectory)
   val changed = copier.preview(created)
   File(source, "existing.txt").writeText("external change")
   assertThrows(IllegalArgumentException::class.java) { runBlocking { copier.apply(changed, true) } }
   assertEquals("external change", File(source, "existing.txt").readText())
   copier.apply(copier.preview(created), true)
   assertEquals("changed", File(source, "existing.txt").readText())
   assertFalse(File(app.filesDir, "folder-copyback.json").exists())
  } finally {
   mountId?.let { app.imageAccess.unmount(it) }
   image?.let { app.library.delete(it, true) }
   if(image != null && metadata.exists() && org.json.JSONObject(metadata.readText()).optString("image") == image?.id) metadata.delete()
   source.deleteRecursively()
  }
 }
 @Test fun folderCopyLocalEditingSafAndRecovery() = runBlocking {
  assumeTrue(InstrumentationRegistry.getArguments().getString("folderFeatures") == "true")
  val app = InstrumentationRegistry.getInstrumentation().targetContext.app
  val metadata = File(app.filesDir, "folder-image.json")
  assumeTrue("Keep the user's temporary image", !metadata.exists())
  val source = File(app.cacheDir, "folder-test-${UUID.randomUUID()}").apply { mkdir() }
  File(source, "original.txt").writeText("original")
  var image: dev.usbdroid.data.ImageEntry? = null
  var mountId: String? = null
  try {
   val created = FolderImage(app).create(source.path, "Folder test", ImageFilesystem.FAT32, 16) {}
   image = created
   val access = app.imageAccess
   val mounted = access.mount(created.physicalPath!!, created.title, false)
   mountId = mounted.id
   assertEquals("original", access.readText(mounted.id, "original.txt").first)
   access.create(mounted.id, "", "created.txt", false)
   val before = access.readText(mounted.id, "created.txt")
   access.writeText(mounted.id, "created.txt", "edited", before.second)
   assertEquals("edited", access.readText(mounted.id, "created.txt").first)
   assertThrows(IllegalArgumentException::class.java) { access.children(mounted.id, "../") }
   assertThrows(IllegalArgumentException::class.java) { runBlocking { app.usb.assertLocalDetached(created.physicalPath!!) } }
   app.contentResolver.query(DocumentsContract.buildRootsUri("${app.packageName}.images"), arrayOf(DocumentsContract.Root.COLUMN_TITLE), null, null, null)!!.use { cursor ->
    assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.columnCount)
   }
   ParcelFileDescriptor.AutoCloseOutputStream(access.open(mounted.id, "created.txt", "wt")).use { it.write("SAF edit".toByteArray()) }
   repeat(20) { if(access.readText(mounted.id, "created.txt").first != "SAF edit") delay(100) }
   assertEquals("SAF edit", access.readText(mounted.id, "created.txt").first)
   val editCache = File.createTempFile("image-document", ".cache", app.cacheDir).apply { writeText("recovered edit") }
   val editJournal = File(app.filesDir, "document-edit-${UUID.randomUUID()}.json")
   val signature = dev.usbdroid.usb.RootShell.inMountNamespace("stat -c '%s:%y:%i' ${dev.usbdroid.usb.RootShell.quote("${mounted.directory}/created.txt")}")
   editJournal.writeText(org.json.JSONObject().put("image", mounted.image).put("relative", "created.txt").put("cache", editCache.path).put("signature", signature).toString())
   access.restoreDocument(editJournal.name)
   assertEquals("recovered edit", access.readText(mounted.id, "created.txt").first)
   assertFalse(editJournal.exists()); assertFalse(editCache.exists())

   val recovered = ImageAccess(app, app.usb); recovered.refresh()
   assertTrue(recovered.mounts.value.any { it.id == mounted.id })
   access.unmount(mounted.id); mountId = null
   val readOnly = access.mount(created.physicalPath!!, created.title, true); mountId = readOnly.id
   assertThrows(IllegalArgumentException::class.java) { access.create(readOnly.id, "", "forbidden.txt", false) }
   access.unmount(readOnly.id); mountId = null
   assertEquals("original", File(source, "original.txt").readText())
  } finally {
   mountId?.let { app.imageAccess.unmount(it) }
   image?.let { app.library.delete(it, true) }
   if(image != null && metadata.exists() && org.json.JSONObject(metadata.readText()).optString("image") == image?.id) metadata.delete()
   File(source, "original.txt").delete(); source.delete()
  }
 }
}
