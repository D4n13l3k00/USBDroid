package dev.usbdroid.files

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import dev.usbdroid.R
import dev.usbdroid.app
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

class ImageDocumentsProvider : DocumentsProvider() {
 private val access get() = requireNotNull(context).app.imageAccess
 private val documentColumns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
 private fun split(id: String): Pair<String, String> {
  val index = id.indexOf(':'); require(index > 0); return id.substring(0, index) to AccessPolicy.relative(id.substring(index + 1))
 }
 private fun add(cursor: MatrixCursor, values: Map<String, Any?>) { cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray()) }
 override fun onCreate() = true
 override fun queryRoots(projection: Array<out String>?): Cursor {
  val cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_ICON))
  runCatching { runBlocking { access.refresh() }; access.mounts.value.forEach { entry -> add(cursor, mapOf(Root.COLUMN_ROOT_ID to entry.id, Root.COLUMN_DOCUMENT_ID to "${entry.id}:", Root.COLUMN_TITLE to "USBDroid · ${entry.title}", Root.COLUMN_FLAGS to (Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD or if(entry.readOnly) 0 else Root.FLAG_SUPPORTS_CREATE), Root.COLUMN_ICON to R.drawable.ic_launcher)) } }
  return cursor
 }
 private fun row(cursor: MatrixCursor, id: String, relative: String, entry: LocalImage, file: ImageFile) {
  val flags = if(entry.readOnly) 0 else (if(relative.isBlank()) 0 else Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME) or (if(file.directory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE)
  add(cursor, mapOf(Document.COLUMN_DOCUMENT_ID to "$id:$relative", Document.COLUMN_DISPLAY_NAME to if(relative.isBlank()) entry.title else file.name, Document.COLUMN_MIME_TYPE to if(file.directory) Document.MIME_TYPE_DIR else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.name.substringAfterLast('.').lowercase()) ?: "application/octet-stream", Document.COLUMN_FLAGS to flags, Document.COLUMN_SIZE to file.size, Document.COLUMN_LAST_MODIFIED to file.modified))
 }
 override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(projection ?: documentColumns).also { cursor -> val (id, relative) = split(documentId); access.access(id) { entry -> row(cursor, id, relative, entry, access.info(entry, relative)) } }
 override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = MatrixCursor(projection ?: documentColumns).also { cursor ->
  val (id, relative) = split(parentDocumentId); val children = access.children(id, relative)
  access.access(id) { entry -> children.forEach { row(cursor, id, it.relative, entry, it) } }
 }
 override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
  signal?.throwIfCanceled(); val (id, relative) = split(documentId)
  return try { access.open(id, relative, mode) } catch(e: Exception) { throw FileNotFoundException(e.message).also { it.initCause(e) } }
 }
 override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String { val (id, relative) = split(parentDocumentId); return "$id:${access.create(id, relative, displayName, mimeType == Document.MIME_TYPE_DIR)}" }
 override fun deleteDocument(documentId: String) { val (id, relative) = split(documentId); access.delete(id, relative) }
 override fun renameDocument(documentId: String, displayName: String): String { val (id, relative) = split(documentId); return "$id:${access.rename(id, relative, displayName)}" }
 override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
  val (parentId, parentPath) = split(parentDocumentId); val (id, path) = split(documentId)
  return id == parentId && path != parentPath && (parentPath.isBlank() || path.startsWith("$parentPath/"))
 }
}
