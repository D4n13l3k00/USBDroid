package dev.usbdroid.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fresh superfloppy FAT16/FAT32 volume. Existing images are never reformatted. */
object FatFormatter {
 fun format(file: File, bytes: Long, label: String = "USBDROID", forceFat32: Boolean? = null) {
  require(bytes >= 4L * 1024 * 1024 && bytes % 512 == 0L)
  val sectors = bytes / 512
  require(sectors <= 0xffffffffL) { "Образ слишком велик для FAT" }
  val fat32 = forceFat32 ?: (bytes >= 512L * 1024 * 1024)
  val reserved = if (fat32) 32 else 1
  val rootSectors = if (fat32) 0 else 32
  var spc = 1
  var fatSectors: Long
  var clusters: Long
  while (true) {
   val entryBytes = if(fat32) 4L else 2L
   val numerator = (sectors - reserved - rootSectors + 2L * spc) * entryBytes
   val denominator = 512L * spc + 2L * entryBytes
   fatSectors = (numerator + denominator - 1) / denominator
   clusters = (sectors - reserved - rootSectors - 2 * fatSectors) / spc
   if (clusters < (if (fat32) 0x0ffffff5L else 65525L)) break
   spc *= 2
   require(spc <= 128) { "Недопустимый размер FAT" }
  }
  require(clusters >= if (fat32) 65525 else 4085)
  RandomAccessFile(file, "rw").use { out ->
   out.setLength(bytes)
   val boot = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
   boot.put(byteArrayOf(0xeb.toByte(), if (fat32) 0x58 else 0x3c, 0x90.toByte()))
   boot.put("USBDROID".toByteArray())
   boot.putShort(512); boot.put(spc.toByte()); boot.putShort(reserved.toShort()); boot.put(2)
   boot.putShort(if (fat32) 0 else 512); boot.putShort(if (sectors < 65536) sectors.toShort() else 0)
   boot.put(0xf8.toByte()); boot.putShort(if (fat32) 0 else fatSectors.toShort())
   boot.putShort(63); boot.putShort(255); boot.putInt(0); boot.putInt(if (sectors >= 65536) sectors.toInt() else 0)
   if (fat32) {
    boot.putInt(fatSectors.toInt()); boot.putShort(0); boot.putShort(0); boot.putInt(2); boot.putShort(1); boot.putShort(6)
    boot.position(64)
   }
   boot.put(0x80.toByte()); boot.put(0); boot.put(0x29); boot.putInt(0x55444231)
   boot.put(label.uppercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(11).padEnd(11).toByteArray())
   boot.put((if (fat32) "FAT32   " else "FAT16   ").toByteArray())
   boot.putShort(510, 0xaa55.toShort())
   out.write(boot.array())
   if (fat32) {
    out.seek(6 * 512); out.write(boot.array())
    val info = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
    info.putInt(0, 0x41615252); info.putInt(484, 0x61417272); info.putInt(488, (clusters - 1).toInt()); info.putInt(492, 3); info.putInt(508, 0xaa550000.toInt())
    out.seek(512); out.write(info.array()); out.seek(7 * 512); out.write(info.array())
   }
   val first = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
   if (fat32) { first.putInt(0x0ffffff8); first.putInt(0x0fffffff); first.putInt(0x0fffffff) }
   else { first.putShort(0xfff8.toShort()); first.putShort(0xffff.toShort()) }
   repeat(2) { out.seek((reserved + it * fatSectors) * 512); out.write(first.array()) }
   val labelEntry = ByteBuffer.allocate(32).apply { put(label.uppercase().filter { it.isLetterOrDigit() && it.code < 128 }.take(11).padEnd(11).toByteArray()); put(0x08) }
   out.seek((reserved + 2 * fatSectors) * 512); out.write(labelEntry.array())
   out.fd.sync()
  }
 }
}
