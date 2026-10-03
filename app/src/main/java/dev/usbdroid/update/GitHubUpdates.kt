package dev.usbdroid.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppRelease(val version: String, val notes: String, val url: String, val size: Long, val digest: String)

object GitHubUpdates {
 const val repository = "D4n13l3k00/USBDroid"
 val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()

 fun newer(candidate: String, current: String): Boolean {
  fun parts(value: String): List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }
  val a = parts(candidate) ?: return false
  val b = parts(current) ?: return false
  return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
 }

 fun parse(json: String, current: String): AppRelease? {
  val release = JSONObject(json)
  if(release.optBoolean("draft") || release.optBoolean("prerelease")) return null
  val version = release.getString("tag_name")
  if(!newer(version, current)) return null
  val assets = release.getJSONArray("assets")
  val expected = "USBDroid-${version.removePrefix("v")}.apk"
  val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == expected && it.optString("state") == "uploaded" } ?: error("asset")
  val url = asset.getString("browser_download_url")
  require(url.startsWith("https://github.com/$repository/releases/download/")) { "asset" }
  val size = asset.getLong("size")
  require(size in 1..536870912L) { "asset" }
  return AppRelease(version, release.optString("body"), url, size, asset.optString("digest"))
 }

 fun check(current: String): AppRelease? {
  val request = Request.Builder().url("https://api.github.com/repos/$repository/releases/latest").header("Accept", "application/vnd.github+json").header("User-Agent", "USBDroid/$current").build()
  return client.newCall(request).execute().use {
   if(it.code == 404) error("unavailable")
   if(it.code == 403 || it.code == 429) error("rate_limit")
   check(it.isSuccessful) { "network" }
   parse(it.body?.string() ?: error("network"), current)
  }
 }

 @Suppress("DEPRECATION")
 fun validate(context: Context, file: File) {
  val flags = if(Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val pm = context.packageManager
  val incoming = pm.getPackageArchiveInfo(file.path, flags) ?: error("invalid_apk")
  val installed = pm.getPackageInfo(context.packageName, flags)
  require(incoming.packageName == context.packageName) { "invalid_apk" }
  fun code(info: android.content.pm.PackageInfo) = if(Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
  require(code(incoming) > code(installed)) { "invalid_apk" }
  fun signers(info: android.content.pm.PackageInfo) = (if(Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) } }?.toSet()
  val trusted = signers(installed)
  require(!trusted.isNullOrEmpty() && trusted == signers(incoming)) { "signature" }
 }
}
