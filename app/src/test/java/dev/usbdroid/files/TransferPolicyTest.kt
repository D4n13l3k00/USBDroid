package dev.usbdroid.files
import org.junit.Assert.*
import org.junit.Test

class TransferPolicyTest {
 @Test fun rejectsCopyingDirectoryIntoItself() {
  listOf("folder", "folder/child").forEach { parent -> assertThrows(IllegalArgumentException::class.java) { TransferPolicy.sources(listOf("folder"), parent) } }
 }
 @Test fun rejectsCollisionsBeforeAnyWrite() {
  assertThrows(IllegalArgumentException::class.java) { TransferPolicy.sources(listOf("a/file", "b/file"), "target") }
 }
 @Test fun removesSelectedDescendants() {
  assertEquals(listOf("a"), TransferPolicy.sources(listOf("a", "a/b"), "target"))
 }
 @Test fun validatesSourceAndDestinationPaths() {
  assertThrows(IllegalArgumentException::class.java) { TransferPolicy.sources(listOf("../escape"), "target") }
  assertThrows(IllegalArgumentException::class.java) { TransferPolicy.sources(listOf("file"), "../escape") }
  assertThrows(IllegalArgumentException::class.java) { TransferPolicy.sources(listOf(""), "target") }
 }
 @Test fun permitsRootAndQuotedNames() { assertEquals(listOf("a/file ' one"), TransferPolicy.sources(listOf("a/file ' one"), "")) }
}
