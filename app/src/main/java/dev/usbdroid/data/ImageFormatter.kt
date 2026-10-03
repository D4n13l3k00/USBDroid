package dev.usbdroid.data

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.io.File

enum class ImageFilesystem(val label: String, val minMiB: Long, val maxMiB: Long = 1048576) {
 NONE("", 4), FAT("FAT", 4, 2048), FAT32("FAT32", 64), EXFAT("exFAT", 4), EXT4("ext4", 16), NTFS("NTFS", 4), BTRFS("Btrfs", 128)
}

val ImageFilesystem.minimumWorkingSpace: Long get() = (if(this == ImageFilesystem.BTRFS) 64L else if(this in setOf(ImageFilesystem.EXT4, ImageFilesystem.NTFS, ImageFilesystem.EXFAT)) 16L else 4L) * 1048576

/** Formats only newly created private staging files, never devices or library entries. */
object ImageFormatter {
 suspend fun format(context: Context, file: File, directory: File, filesystem: ImageFilesystem) = withContext(Dispatchers.IO) {
  require(file.canonicalFile.parentFile == directory.canonicalFile && file.name.startsWith(".") && file.name.endsWith(".partial") && file.isFile) { "Only new staging images may be formatted" }
  require(file.length() % 1048576 == 0L && file.length() / 1048576 in filesystem.minMiB..filesystem.maxMiB) { "Invalid size for ${filesystem.label}" }
  if(filesystem == ImageFilesystem.NONE) return@withContext
  if(filesystem == ImageFilesystem.FAT || filesystem == ImageFilesystem.FAT32) {
   FatFormatter.format(file, file.length(), forceFat32 = filesystem == ImageFilesystem.FAT32); return@withContext
  }
  val native = File(context.applicationInfo.nativeLibraryDir)
  val aliases = File(context.filesDir, "formatter-libs").apply { mkdirs() }
  synchronized(ImageFormatter) {
   for((alias, original) in listOf("libz.so.1" to "libfs_z.so", "libzstd.so.1" to "libfs_zstd.so")) {
    val link = File(aliases, alias)
    val target = File(native, original).absolutePath
    if(runCatching { Os.readlink(link.absolutePath) }.getOrNull() != target) {
     link.delete()
     Os.symlink(target, link.absolutePath)
    }
   }
  }
  val config = File(aliases, "mke2fs.conf")
  synchronized(ImageFormatter) { if(!config.exists()) context.assets.open("formatters/mke2fs.conf").use { input -> config.outputStream().use { input.copyTo(it) } } }
  val args = when(filesystem) {
   ImageFilesystem.EXFAT -> listOf("libmkfs_exfat.so", "-L", "USBDROID")
   ImageFilesystem.EXT4 -> listOf("libmke2fs.so", "-t", "ext4", "-F", "-q", "-L", "USBDROID")
   ImageFilesystem.NTFS -> listOf("libmkntfs.so", "-F", "-Q", "-L", "USBDROID")
   ImageFilesystem.BTRFS -> listOf("libmkfs_btrfs.so", "-f", "-m", "single", "-d", "single", "--nodiscard", "-L", "USBDROID")
   else -> error("Unsupported filesystem")
  }
  val log = File(context.cacheDir, "${file.name}.format-log")
  val builder = ProcessBuilder(listOf(File(native, args.first()).absolutePath) + args.drop(1) + file.absolutePath).redirectErrorStream(true).redirectOutput(log)
  builder.environment()["LD_LIBRARY_PATH"] = "${aliases.absolutePath}:${native.absolutePath}"
  builder.environment()["MKE2FS_CONFIG"] = config.absolutePath
  val process = builder.start()
  try {
   withTimeout(600000) { while(process.isAlive) delay(100) }
   check(process.exitValue() == 0) { "${filesystem.label}: ${log.readText().takeLast(2048)}" }
  } finally { if(process.isAlive) process.destroyForcibly(); log.delete() }
 }
}
