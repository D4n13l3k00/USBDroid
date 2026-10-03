package dev.usbdroid.update

import org.junit.Assert.*
import org.junit.Test

class GitHubUpdatesTest {
 @Test fun automaticChecksRespectSixHours() {
  assertTrue(UpdateChecks.due(1000, 0))
  assertFalse(UpdateChecks.due(1000 + UpdateChecks.interval - 1, 1000))
  assertTrue(UpdateChecks.due(1000 + UpdateChecks.interval, 1000))
  assertTrue(UpdateChecks.due(999, 1000))
 }
 private fun release(version: String = "v1.1.0", asset: String = "USBDroid-1.1.0.apk", url: String = "https://github.com/D4n13l3k00/USBDroid/releases/download/v1.1.0/$asset", prerelease: Boolean = false) = """{"tag_name":"$version","draft":false,"prerelease":$prerelease,"body":"Changes","assets":[{"name":"$asset","state":"uploaded","browser_download_url":"$url","size":1234,"digest":""}]}"""
 @Test fun versionsAreComparedNumerically() {
  assertTrue(GitHubUpdates.newer("v1.10.0", "1.9.9"))
  assertFalse(GitHubUpdates.newer("v1.0.0", "1.0.0"))
  assertFalse(GitHubUpdates.newer("v0.9.9", "1.0.0"))
  assertFalse(GitHubUpdates.newer("v2.0.0-beta1", "1.0.0"))
  assertFalse(GitHubUpdates.newer("v999999999999.0.0", "1.0.0"))
 }
 @Test fun selectsOnlyExpectedReleaseApk() {
  val result = GitHubUpdates.parse(release(), "1.0.0")!!
  assertEquals("v1.1.0", result.version)
  assertEquals(1234L, result.size)
  assertEquals("Changes", result.notes)
 }
 @Test fun skipsPrereleaseAndCurrentVersion() {
  assertNull(GitHubUpdates.parse(release(prerelease = true), "1.0.0"))
  assertNull(GitHubUpdates.parse(release(), "1.1.0"))
 }
 @Test(expected = IllegalStateException::class) fun rejectsUnexpectedAsset() {
  GitHubUpdates.parse(release(asset = "debug.apk"), "1.0.0")
 }
 @Test(expected = IllegalArgumentException::class) fun rejectsExternalDownload() {
  GitHubUpdates.parse(release(url = "https://example.com/update.apk"), "1.0.0")
 }
}
