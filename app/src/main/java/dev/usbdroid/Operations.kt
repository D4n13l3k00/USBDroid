package dev.usbdroid

import dev.usbdroid.data.TransferJob
import org.json.JSONObject

fun operationKey(kind: String, args: JSONObject): String = when(kind) {
 "CREATE" -> "create"
 "DOWNLOAD" -> "download:${args.optString("url")}"
 "IMPORT" -> "import:${args.optString("uri")}"
 else -> "${kind.lowercase()}:${args.optString("image")}:${if(kind == "CHECKSUM") args.optString("algorithm") else ""}"
}
fun TransferJob.operationKey(): String = runCatching { operationKey(kind, JSONObject(args)) }.getOrDefault("job:$id")
val TransferJob.running: Boolean get() = state in listOf("QUEUED", "RUNNING")
fun AppState.working(key: String): Boolean = key in operations || jobs.any { it.running && it.operationKey() == key }
fun AppState.usbWorking(path: String? = null): Boolean = "eject-all" in operations || operations.any { (it.startsWith("host:") || it.startsWith("eject:")) && (path == null || (if(it.startsWith("host:")) it.substringAfter(':').substringBeforeLast(':') else it.substringAfter(':')) == path) }
fun AppState.imageWorking(id: String): Boolean = "bulk-delete" in operations || operations.any { (it.substringAfter(':').substringBefore(':') == id || it.endsWith(":$id")) } || jobs.any { it.running && runCatching { JSONObject(it.args).optString("image") == id }.getOrDefault(false) }
