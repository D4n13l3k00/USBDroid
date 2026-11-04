package dev.usbdroid.data

import dev.usbdroid.files.AccessPolicy
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.*
import org.junit.Test

class PartitionTableTest {
 private fun image(layout: ImageLayout, check: (RandomAccessFile) -> Unit) {
  val file = File.createTempFile("partition-test", ".img")
  try { RandomAccessFile(file, "rw").use { it.setLength(130L * 1048576); PartitionTable.write(it, layout, ImageFilesystem.FAT32); check(it) } }
  finally { file.delete() }
 }
 @Test fun mbrBoundsAndHiddenSectors() = image(ImageLayout.MBR) { file ->
  val bytes = ByteArray(17408); file.seek(0); file.readFully(bytes)
  val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  assertEquals(0x0c, bytes[450].toInt()); assertEquals(2048, b.getInt(454)); assertEquals(128 * 2048, b.getInt(458))
  assertEquals(1048576L, AccessPolicy.offset(bytes, file.length()))
  file.seek(1048576 + 28); assertEquals(2048, Integer.reverseBytes(file.readInt()))
  file.seek(1048576 + 6 * 512 + 28); assertEquals(2048, Integer.reverseBytes(file.readInt()))
 }
 @Test fun gptPrimaryBackupAndCrc() = image(ImageLayout.GPT) { file ->
  val bytes = ByteArray(17408); file.seek(0); file.readFully(bytes)
  assertEquals(1048576L, AccessPolicy.offset(bytes, file.length()))
  fun verify(position: Long) {
   val h = ByteArray(512); file.seek(position); file.readFully(h)
   val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
   assertEquals("EFI PART", String(h, 0, 8)); val saved = b.getInt(16); b.putInt(16, 0)
   assertEquals(saved, CRC32().apply { update(h, 0, 92) }.value.toInt())
   val entries = ByteArray(16384); file.seek(b.getLong(72) * 512); file.readFully(entries)
   assertEquals(b.getInt(88), CRC32().apply { update(entries) }.value.toInt())
  }
  verify(512); verify(file.length() - 512)
 }
 @Test fun rawLayoutRetainsSize() { assertEquals(128L * 1048576, PartitionTable.payloadBytes(128L * 1048576, ImageLayout.NONE)); assertEquals(126L * 1048576, PartitionTable.payloadBytes(128L * 1048576, ImageLayout.GPT)) }
}
