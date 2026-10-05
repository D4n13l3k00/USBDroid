package dev.usbdroid.files

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AccessPolicy {
 fun loopAlias(id: String): String {
  require(id.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")))
  return "/data/local/tmp/ud-loop/${id.replace("-", "")}.img"
 }
 fun name(value: String): String {
  require(value.isNotBlank() && value !in listOf(".", "..") && value.length <= 255 && value.none { it == '/' || it == '\u0000' || it == '\n' || it == '\r' }) { "Invalid file name" }
  return value
 }
 fun relative(value: String): String {
  require(!value.startsWith('/') && value.none { it == '\u0000' || it == '\n' || it == '\r' } && value.split('/').none { it == "." || it == ".." }) { "Invalid image path" }
  return value
 }
 /** Raw filesystems have offset zero; partitioned images use the first nonempty partition. */
 fun offset(header: ByteArray, imageSize: Long): Long {
  if(header.size < 512 || header[510] != 0x55.toByte() || header[511] != 0xaa.toByte()) return 0
  val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
  val oem = String(header, 3, 8, Charsets.US_ASCII)
  val sector = b.getShort(11).toInt() and 0xffff
  val cluster = header[13].toInt() and 255
  if(oem == "EXFAT   " || oem == "NTFS    " || sector in listOf(512, 1024, 2048, 4096) && cluster > 0 && cluster and (cluster - 1) == 0 && (b.getShort(14).toInt() and 0xffff) > 0 && header[16].toInt() in 1..2) return 0
  val entries = (0..3).filter { (header[446 + it * 16 + 4].toInt() and 255) != 0 }
  if(entries.isEmpty()) return 0
  val start: Long
  val length: Long
  if(entries.any { (header[446 + it * 16 + 4].toInt() and 255) == 0xee }) {
   require(header.size >= 1024 && String(header, 512, 8, Charsets.US_ASCII) == "EFI PART") { "Invalid GPT image" }
   val table = Math.multiplyExact(b.getLong(584), 512L)
   val count = b.getInt(592); val entrySize = b.getInt(596)
   require(table >= 1024 && table <= header.size - 128 && count > 0 && entrySize >= 128) { "Unsupported GPT layout" }
   val p = (0 until minOf(count, (header.size - table.toInt()) / entrySize)).map { table.toInt() + it * entrySize }.firstOrNull { at -> (0..15).any { header[at + it] != 0.toByte() } } ?: error("No partition found")
   start = b.getLong(p + 32); length = Math.addExact(Math.subtractExact(b.getLong(p + 40), start), 1)
  } else {
   val p = 446 + entries.first() * 16
   require((header[p + 4].toInt() and 255) !in listOf(5, 15, 0x85)) { "Extended partitions are not supported" }
   start = b.getInt(p + 8).toLong() and 0xffffffffL; length = b.getInt(p + 12).toLong() and 0xffffffffL
  }
  require(start > 0 && length > 0 && Math.multiplyExact(Math.addExact(start, length), 512L) <= imageSize) { "Partition is outside the image" }
  return Math.multiplyExact(start, 512L)
 }
}
