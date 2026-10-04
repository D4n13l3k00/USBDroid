package dev.usbdroid.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("settings")
class SettingsStore(private val context: Context) {
 val flow = context.settingsDataStore.data.map { p -> Preferences(
  theme = p[stringPreferencesKey("theme")] ?: "system", dynamic = p[booleanPreferencesKey("dynamic")] ?: true,
  amoled = p[booleanPreferencesKey("amoled")] ?: false, keepAwake = p[booleanPreferencesKey("awake")] ?: true,
  autoUsb = p[booleanPreferencesKey("autoUsb")] ?: true, usbCompatibility = p[booleanPreferencesKey("usbCompatibility")] ?: false, usbSystem = p[stringPreferencesKey("usbSystem")] ?: "auto",
  usbMode = p[stringPreferencesKey("usbMode")] ?: "mass_storage", autoHybrid = p[booleanPreferencesKey("autoHybrid")] ?: false,
  language = p[stringPreferencesKey("language")] ?: "system", welcomeComplete = p[booleanPreferencesKey("welcomeComplete")] ?: false,
  setupStep = p[intPreferencesKey("setupStep")] ?: -1, setupJob = p[stringPreferencesKey("setupJob")] ?: "", setupLun = p[stringPreferencesKey("setupLun")] ?: "",
  diskResult = p[stringPreferencesKey("diskResult")] ?: "NOT_TESTED", bootResult = p[stringPreferencesKey("bootResult")] ?: "NOT_TESTED",
  imageSort = p[stringPreferencesKey("imageSort")] ?: "name", imageDescending = p[booleanPreferencesKey("imageDescending")] ?: false,
  downloadSort = p[stringPreferencesKey("downloadSort")] ?: "name", downloadDescending = p[booleanPreferencesKey("downloadDescending")] ?: false,
  jobSort = p[stringPreferencesKey("jobSort")] ?: "date", jobDescending = p[booleanPreferencesKey("jobDescending")] ?: true, favorites = p[stringSetPreferencesKey("favorites")] ?: emptySet(), lastHostMode = p[stringPreferencesKey("lastHostMode")] ?: "READ_ONLY") }
 suspend fun setup(step: Int? = null, job: String? = null, lun: String? = null, disk: String? = null, boot: String? = null, complete: Boolean? = null) { context.settingsDataStore.edit {
  step?.let { value -> it[intPreferencesKey("setupStep")] = value }; job?.let { value -> it[stringPreferencesKey("setupJob")] = value }
  lun?.let { value -> it[stringPreferencesKey("setupLun")] = value }; disk?.let { value -> it[stringPreferencesKey("diskResult")] = value }
  boot?.let { value -> it[stringPreferencesKey("bootResult")] = value }; complete?.let { value -> it[booleanPreferencesKey("welcomeComplete")] = value }
 } }
 suspend fun saveSorting(previous: Preferences, p: Preferences) { context.settingsDataStore.edit {
  if(previous.imageSort != p.imageSort) it[stringPreferencesKey("imageSort")] = p.imageSort
  if(previous.imageDescending != p.imageDescending) it[booleanPreferencesKey("imageDescending")] = p.imageDescending
  if(previous.downloadSort != p.downloadSort) it[stringPreferencesKey("downloadSort")] = p.downloadSort
  if(previous.downloadDescending != p.downloadDescending) it[booleanPreferencesKey("downloadDescending")] = p.downloadDescending
  if(previous.jobSort != p.jobSort) it[stringPreferencesKey("jobSort")] = p.jobSort
  if(previous.jobDescending != p.jobDescending) it[booleanPreferencesKey("jobDescending")] = p.jobDescending
 } }
 suspend fun favorites(ids: Set<String>, enabled: Boolean) { context.settingsDataStore.edit { values ->
  val key = stringSetPreferencesKey("favorites"); val current = values[key] ?: emptySet(); values[key] = if(enabled) current + ids else current - ids
 } }
 suspend fun rememberHost(mode: String) { context.settingsDataStore.edit { it[stringPreferencesKey("lastHostMode")] = mode } }
 suspend fun save(p: Preferences) { context.settingsDataStore.edit {
  it[stringPreferencesKey("theme")] = p.theme; it[booleanPreferencesKey("dynamic")] = p.dynamic; it[booleanPreferencesKey("amoled")] = p.amoled
  it[booleanPreferencesKey("awake")] = p.keepAwake; it[booleanPreferencesKey("autoUsb")] = p.autoUsb; it[booleanPreferencesKey("usbCompatibility")] = p.usbCompatibility; it[stringPreferencesKey("usbSystem")] = p.usbSystem
  it[stringPreferencesKey("usbMode")] = p.usbMode; it[booleanPreferencesKey("autoHybrid")] = p.autoHybrid; it[stringPreferencesKey("language")] = p.language
  it[booleanPreferencesKey("welcomeComplete")] = p.welcomeComplete
  it[stringPreferencesKey("imageSort")] = p.imageSort; it[booleanPreferencesKey("imageDescending")] = p.imageDescending
  it[stringPreferencesKey("downloadSort")] = p.downloadSort; it[booleanPreferencesKey("downloadDescending")] = p.downloadDescending
  it[stringPreferencesKey("jobSort")] = p.jobSort; it[booleanPreferencesKey("jobDescending")] = p.jobDescending
 } }
}
