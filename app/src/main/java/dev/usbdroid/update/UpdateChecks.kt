package dev.usbdroid.update

import android.content.Context
import dev.usbdroid.BuildConfig
import dev.usbdroid.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

object UpdateChecks {
 const val interval = 6 * 60 * 60 * 1000L
 val release = MutableStateFlow<AppRelease?>(null)
 val checking = MutableStateFlow(false)
 val status = MutableStateFlow(0)
 private val lock = Mutex()

 fun due(now: Long, previous: Long) = previous == 0L || now < previous || now - previous >= interval

 suspend fun check(context: Context, force: Boolean = false) {
  if(!lock.tryLock()) return
  try {
   val prefs = context.getSharedPreferences("app-updates", Context.MODE_PRIVATE)
   if(release.value == null) runCatching {
    val cached = JSONObject(prefs.getString("release", "")!!)
    val item = AppRelease(cached.getString("version"), cached.getString("notes"), cached.getString("url"), cached.getLong("size"), cached.getString("digest"))
    if(GitHubUpdates.newer(item.version, BuildConfig.VERSION_NAME) && item.url.startsWith("https://github.com/${GitHubUpdates.repository}/releases/download/") && item.size in 1..536870912L) release.value = item
   }
   val now = System.currentTimeMillis()
   if(!force && !due(now, prefs.getLong("last-attempt", 0))) return
   checking.value = true
   // Persist attempts too: offline starts must not repeatedly hit the network.
   withContext(Dispatchers.IO) { prefs.edit().putLong("last-attempt", now).commit() }
   try {
    val latest = withContext(Dispatchers.IO) { GitHubUpdates.check(BuildConfig.VERSION_NAME) }
    release.value = latest
    status.value = if(latest == null) R.string.update_current else 0
    withContext(Dispatchers.IO) {
     val cached = latest?.let { JSONObject().put("version", it.version).put("notes", it.notes).put("url", it.url).put("size", it.size).put("digest", it.digest).toString() }
     prefs.edit().putString("release", cached).commit()
    }
   } catch(e: CancellationException) { throw e }
   catch(e: Exception) { status.value = updateError(e.message) }
  } finally { checking.value = false; lock.unlock() }
 }
}

internal fun updateError(code: String?) = when(code) {
 "unavailable" -> R.string.update_unavailable
 "rate_limit" -> R.string.update_rate_limit
 "signature" -> R.string.update_signature
 "invalid_apk", "asset" -> R.string.update_invalid
 "space" -> R.string.update_space
 else -> R.string.update_network
}
