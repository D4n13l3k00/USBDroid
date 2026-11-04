package dev.usbdroid.data

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.CRC32

enum class ImageLayout { NONE, MBR, GPT }

/** One MiB alignment, plus one MiB reserved at the end for GPT backup metadata. */
object PartitionTable {
 const val START = 2048L
 fun payloadBytes(total: Long, layout: ImageLayout): Long {
  require(total % 1048576 == 0L)
  return if(layout == ImageLayout.NONE) total else total - 2097152L
 }
 fun write(out: RandomAccessFile, layout: ImageLayout, filesystem: ImageFilesystem) {
  if(layout == ImageLayout.NONE) return
  val sectors = out.length() / 512
  val count = sectors - 4096
  require(count > 0 && sectors <= 0xffffffffL)
  fun buffer(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
  val mbr = buffer(512)
  if(layout == ImageLayout.MBR) mbr.putInt(440, UUID.randomUUID().hashCode().let { if(it == 0) 1 else it })
  mbr.position(446); mbr.put(0); mbr.put(byteArrayOf(0xfe.toByte(), 0xff.toByte(), 0xff.toByte()))
  val type = if(layout == ImageLayout.GPT) 0xee else when(filesystem) {
   ImageFilesystem.FAT -> 0x0e; ImageFilesystem.FAT32 -> 0x0c
   ImageFilesystem.EXT4, ImageFilesystem.BTRFS -> 0x83; else -> 0x07
  }
  mbr.put(type.toByte()); mbr.put(byteArrayOf(0xfe.toByte(), 0xff.toByte(), 0xff.toByte()))
  mbr.putInt(if(layout == ImageLayout.GPT) 1 else START.toInt())
  mbr.putInt(if(layout == ImageLayout.GPT) (sectors - 1).toInt() else count.toInt())
  mbr.putShort(510, 0xaa55.toShort()); out.seek(0); out.write(mbr.array())
  if(layout == ImageLayout.GPT) {
   fun guid(b: ByteBuffer, value: UUID) {
    b.putInt((value.mostSignificantBits ushr 32).toInt()); b.putShort((value.mostSignificantBits ushr 16).toShort()); b.putShort(value.mostSignificantBits.toShort())
    for(i in 7 downTo 0) b.put((value.leastSignificantBits ushr (i * 8)).toByte())
   }
   fun crc(bytes: ByteArray) = CRC32().apply { update(bytes) }.value.toInt()
   val entries = buffer(16384)
   guid(entries, UUID.fromString(if(filesystem in setOf(ImageFilesystem.EXT4, ImageFilesystem.BTRFS)) "0fc63daf-8483-4772-8e79-3d69d8477de4" else "ebd0a0a2-b9e5-4433-87c0-68b6b72699c7"))
   guid(entries, UUID.randomUUID()); entries.putLong(START); entries.putLong(START + count - 1); entries.putLong(0)
   entries.put("USBDroid".toByteArray(Charsets.UTF_16LE))
   val disk = UUID.randomUUID()
   fun header(current: Long, backup: Long, table: Long): ByteArray {
    val h = buffer(512); h.put("EFI PART".toByteArray()); h.putInt(0x10000); h.putInt(92); h.putInt(0); h.putInt(0)
    h.putLong(current); h.putLong(backup); h.putLong(34); h.putLong(sectors - 34); guid(h, disk)
    h.putLong(table); h.putInt(128); h.putInt(128); h.putInt(crc(entries.array()))
    h.putInt(16, crc(h.array().copyOf(92))); return h.array()
   }
   out.seek(1024); out.write(entries.array()); out.seek((sectors - 33) * 512); out.write(entries.array())
   out.seek(512); out.write(header(1, sectors - 1, 2)); out.seek((sectors - 1) * 512); out.write(header(sectors - 1, 1, sectors - 33))
  }
  if(filesystem == ImageFilesystem.FAT || filesystem == ImageFilesystem.FAT32) {
   val hidden = buffer(4).putInt(START.toInt()).array()
   out.seek(START * 512 + 28); out.write(hidden)
   if(filesystem == ImageFilesystem.FAT32) { out.seek(START * 512 + 6 * 512 + 28); out.write(hidden) }
  }
  if(filesystem == ImageFilesystem.NTFS) {
   val hidden = buffer(4).putInt(START.toInt()).array()
   out.seek(START * 512 + 28); out.write(hidden)
   out.seek((START + count - 1) * 512 + 28); out.write(hidden)
  }
  if(filesystem == ImageFilesystem.EXFAT) {
   out.seek(START * 512 + 108)
   val shift = out.readUnsignedByte(); require(shift in 9..12) { "Invalid exFAT sector size" }
   val sectorBytes = 1 shl shift
   for(region in listOf(0, 12)) {
    val boot = ByteArray(11 * sectorBytes); out.seek(START * 512 + region * sectorBytes); out.readFully(boot)
    ByteBuffer.wrap(boot).order(ByteOrder.LITTLE_ENDIAN).putLong(64, START * 512 / sectorBytes)
    var checksum = 0
    boot.forEachIndexed { i, byte -> if(i != 106 && i != 107 && i != 112) checksum = Integer.rotateRight(checksum, 1) + (byte.toInt() and 255) }
    out.seek(START * 512 + region * sectorBytes); out.write(boot)
    val checksums = buffer(sectorBytes); repeat(sectorBytes / 4) { checksums.putInt(checksum) }; out.write(checksums.array())
   }
  }
  out.fd.sync()
 }
}
