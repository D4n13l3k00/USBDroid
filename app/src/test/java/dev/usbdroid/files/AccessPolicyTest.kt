package dev.usbdroid.files

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AccessPolicyTest {
 @Test fun loopAliasFitsAndroids64ByteFilenameField() {
  val alias = AccessPolicy.loopAlias("1b0bfa63-6644-4e63-960f-e742296e54e2")
  assertTrue(alias.toByteArray(Charsets.UTF_8).size < 64)
  assertTrue(alias.startsWith("/data/local/tmp/ud-loop/"))
  assertThrows(IllegalArgumentException::class.java) { AccessPolicy.loopAlias("../../escape") }
 }
 private fun header(): ByteArray = ByteArray(34 * 512).also { it[510] = 0x55; it[511] = 0xaa.toByte() }
 @Test fun rejectsTraversalAndShellPathSeparators() {
  listOf("../x", "a/../x", "/root", "a\u0000b", "a\nb").forEach { assertThrows(IllegalArgumentException::class.java) { AccessPolicy.relative(it) } }
  listOf(".", "..", "a/b", "", "a\nb").forEach { assertThrows(IllegalArgumentException::class.java) { AccessPolicy.name(it) } }
  assertEquals("folder/file with spaces.txt", AccessPolicy.relative("folder/file with spaces.txt"))
  assertEquals("file'quoted.txt", AccessPolicy.name("file'quoted.txt"))
 }
 @Test fun rawFilesystemHasZeroOffset() { assertEquals(0L, AccessPolicy.offset(ByteArray(512), 1048576)) }
 @Test fun fatBootCodeIsNotMistakenForPartitionTable() {
  val h = header(); val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
  b.putShort(11, 512); h[13] = 1; b.putShort(14, 32); h[16] = 2; h[450] = 0x83.toByte()
  assertEquals(0L, AccessPolicy.offset(h, 1048576))
 }
 @Test fun parsesAndBoundsChecksMbr() {
  val h = header(); val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
  h[450] = 0x83.toByte(); b.putInt(454, 2048); b.putInt(458, 8192)
  assertEquals(1048576L, AccessPolicy.offset(h, 6 * 1048576L))
  assertThrows(IllegalArgumentException::class.java) { AccessPolicy.offset(h, 2 * 1048576L) }
 }
 @Test fun parsesGptFirstPartition() {
  val h = header(); val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
  h[450] = 0xee.toByte(); "EFI PART".toByteArray().copyInto(h, 512)
  b.putLong(584, 2); b.putInt(592, 128); b.putInt(596, 128); h[1024] = 1
  b.putLong(1056, 2048); b.putLong(1064, 10239)
  assertEquals(1048576L, AccessPolicy.offset(h, 6 * 1048576L))
 }
 @Test fun rejectsExtendedPartitions() {
  val h = header(); h[450] = 5
  assertThrows(IllegalArgumentException::class.java) { AccessPolicy.offset(h, 1048576) }
 }
}
