package dev.usbdroid
import dev.usbdroid.data.FatFormatter
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FatFormatterTest {
 private fun verify(mib: Long, fat32: Boolean) {
  val file = File.createTempFile("fat-volume", ".img")
  try {
   FatFormatter.format(file, mib * 1048576)
   assertEquals(mib * 1048576, file.length())
   RandomAccessFile(file, "r").use { input ->
    val bytes = ByteArray(512); input.readFully(bytes); val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(512, b.getShort(11).toInt()); assertEquals(0xaa55, b.getShort(510).toInt() and 0xffff)
    val reserved = b.getShort(14).toInt() and 0xffff
    val sectors = if(b.getShort(19).toInt() != 0) b.getShort(19).toInt() and 0xffff else b.getInt(32).toLong() and 0xffffffffL
    val fat = if(fat32) b.getInt(36).toLong() else (b.getShort(22).toInt() and 0xffff).toLong()
    val root = if(fat32) 0 else 32
    val clusters = (sectors.toLong() - reserved - fat * 2 - root) / (b.get(13).toInt() and 0xff)
    assertTrue(if(fat32) clusters >= 65525 && clusters < 0x0ffffff5 else clusters in 4085..65524)
    assertEquals(if(fat32) "FAT32   " else "FAT16   ", String(bytes, if(fat32) 82 else 54, 8))
    val first = ByteArray(512); val second = ByteArray(512); input.seek(reserved * 512L); input.readFully(first); input.seek((reserved + fat) * 512L); input.readFully(second); assertArrayEquals(first, second)
    if(fat32) { input.seek(6 * 512); val backup = ByteArray(512); input.readFully(backup); assertArrayEquals(bytes, backup); input.seek(512); val info = ByteArray(512); input.readFully(info); assertEquals(0x41615252, ByteBuffer.wrap(info).order(ByteOrder.LITTLE_ENDIAN).getInt()) }
   }
  } finally { file.delete() }
 }
 @Test fun minimumFat16() = verify(4, false)
 @Test fun typicalFat16() = verify(32, false)
 @Test fun largestFat16Range() = verify(511, false)
 @Test fun fat32Boundary() = verify(512, true)
 @Test fun largeFat32() = verify(4096, true)
 @Test fun rejectsSmallImage() { val f = File.createTempFile("small", ".img"); try { assertThrows(IllegalArgumentException::class.java) { FatFormatter.format(f, 1024) } } finally { f.delete() } }
}
