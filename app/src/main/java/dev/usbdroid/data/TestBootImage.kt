package dev.usbdroid.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Installs our BIOS message and UEFI x64 test program into a fresh FAT16 volume. */
object TestBootImage {
 fun install(file: File, bios: ByteArray, efi: ByteArray) {
  require(bios.size <= 448 && efi.size > 64 && efi[0] == 0x4d.toByte() && efi[1] == 0x5a.toByte())
  RandomAccessFile(file, "rw").use { out ->
   val boot = ByteArray(512).also { out.readFully(it) }; val b = ByteBuffer.wrap(boot).order(ByteOrder.LITTLE_ENDIAN)
   require(String(boot, 54, 8) == "FAT16   ")
   val reserved = b.getShort(14).toInt() and 65535; val fatSectors = b.getShort(22).toInt() and 65535
   val rootSectors = ((b.getShort(17).toInt() and 65535) * 32 + 511) / 512
   val clusterSize = (boot[13].toInt() and 255) * 512
   val rootOffset = (reserved + 2 * fatSectors) * 512L
   val dataOffset = rootOffset + rootSectors * 512L
   fun cluster(index: Int) = dataOffset + (index - 2) * clusterSize.toLong()
   fun entry(name: String, directory: Boolean, index: Int, size: Int = 0): ByteArray = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
    put(name.padEnd(11).toByteArray(Charsets.US_ASCII)); put(if(directory) 0x10 else 0x20)
    putShort(26, index.toShort()); putInt(28, size)
   }.array()
   fun fat(index: Int, next: Int) { repeat(2) { copy -> out.seek((reserved + copy * fatSectors) * 512L + index * 2L); out.write(next and 255); out.write(next ushr 8) } }
   val fileClusters = (efi.size + clusterSize - 1) / clusterSize
   fat(2, 0xffff); fat(3, 0xffff)
   repeat(fileClusters) { index -> fat(4 + index, if(index == fileClusters - 1) 0xffff else 5 + index) }
   val readmeCluster = 4 + fileClusters
   val readme = "USBDroid test disk. BIOS and UEFI x64 boot test included.\r\nSecure Boot must allow this unsigned test application.\r\nA successful kernel mount does not prove computer detection or boot.\r\n".toByteArray()
   fat(readmeCluster, 0xffff)
   out.seek(62); out.write(bios)
   out.seek(rootOffset + 32); out.write(entry("EFI", true, 2)); out.write(entry("README  TXT", false, readmeCluster, readme.size))
   out.seek(cluster(2)); out.write(entry(".", true, 2)); out.write(entry("..", true, 0)); out.write(entry("BOOT", true, 3))
   out.seek(cluster(3)); out.write(entry(".", true, 3)); out.write(entry("..", true, 2)); out.write(entry("BOOTX64 EFI", false, 4, efi.size))
   out.seek(cluster(4)); out.write(efi)
   out.seek(cluster(readmeCluster)); out.write(readme)
   out.fd.sync()
  }
 }
}
