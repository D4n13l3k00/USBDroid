package dev.usbdroid

import dev.usbdroid.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SortingTest {
 @Test fun imagesRespectSizeDateAndDirection() {
  val a = ImageEntry("a", "zebra", "/a", "/a", 4, 20, "default")
  val b = a.copy(id = "b", title = "Alpha", size = 8, modified = 10)
  assertEquals(listOf("b", "a"), sortedImages(listOf(a, b), "name", false).map { it.id })
  assertEquals(listOf("b", "a"), sortedImages(listOf(a, b), "size", true).map { it.id })
  assertEquals(listOf("b", "a"), sortedImages(listOf(a, b), "date", false).map { it.id })
 }
 @Test fun versionsUseNumericPartsAndArchitecturesHaveIndependentOrdering() {
  val a = Release("Linux", "9.2", "x86_64", "https://a", 32)
  val b = a.copy(version = "10.0", arch = "arm64", url = "https://b", size = 16)
  assertEquals(listOf("9.2", "10.0"), sortedReleases(listOf(b, a), "version", false).map { it.version })
  assertEquals(listOf("arm64", "x86_64"), sortedReleases(listOf(a, b), "arch", false).map { it.arch })
  assertEquals(listOf(32L, 16L), sortedReleases(listOf(b, a), "size", true).map { it.size })
 }
 @Test fun downloadJobsPreserveChronologyAndSortByProgress() {
  val newest = TransferJob("new", "DOWNLOAD", "B", "{}", total = 100, progress = 10, createdAt = 20)
  val oldest = newest.copy(id = "old", title = "A", progress = 90, createdAt = 10)
  assertEquals(listOf("old", "new"), sortedJobs(listOf(newest, oldest), "date", false).map { it.id })
  assertEquals(listOf("old", "new"), sortedJobs(listOf(newest, oldest), "progress", true).map { it.id })
 }
 @Test fun workerActivityDisablesOnlyItsOwnOperationAndSurvivesViewModelRecreation() {
  val job = TransferJob("1", "CHECKSUM", "hash", JSONObject().put("image", "disk-a").put("algorithm", "SHA-256").toString())
  val state = AppState(jobs = listOf(job))
  assertTrue(state.working("checksum:disk-a:SHA-256")); assertFalse(state.working("checksum:disk-b:SHA-256"))
  assertTrue(state.imageWorking("disk-a")); assertFalse(state.imageWorking("disk-b"))
  assertFalse(state.copy(jobs = listOf(job.copy(state = "FAILED"))).working(job.operationKey()))
 }
}
