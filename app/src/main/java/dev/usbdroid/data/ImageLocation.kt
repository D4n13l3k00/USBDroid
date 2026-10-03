package dev.usbdroid.data

import android.content.Context
import android.net.Uri
import java.io.File

/** Classify the real location, not the library collection it belongs to. */
fun ImageEntry.isAppFile(context: Context): Boolean {
 val path = physicalPath ?: if(location.startsWith("file:")) Uri.parse(location).path else location.takeIf { it.startsWith("/") }
 if(path == null) return false
 val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
 val roots = listOf(context.filesDir, context.cacheDir) + context.getExternalFilesDirs(null).filterNotNull() + context.externalCacheDirs.filterNotNull()
 return roots.any { root -> val base = root.canonicalFile; file == base || file.path.startsWith(base.path + File.separator) }
}
fun ImageEntry.displayLocation(): String = physicalPath ?: location
