package dev.usbdroid.files
import dev.usbdroid.data.ImageFilesystem
import java.util.Locale

object FolderLayoutPolicy {
 fun validate(paths: List<Pair<String, Long>>, filesystem: ImageFilesystem) {
  if(filesystem in setOf(ImageFilesystem.FAT, ImageFilesystem.FAT32, ImageFilesystem.EXFAT, ImageFilesystem.NTFS)) {
   require(paths.map { it.first.lowercase(Locale.ROOT) }.distinct().size == paths.size) { "File names differ only by case; choose ext4 or Btrfs" }
   require(paths.all { (relative, _) -> relative.split('/').all { name -> !name.endsWith('.') && !name.endsWith(' ') && name.none { it in "\\:*?\"<>|" } } }) { "File names are unsupported by this filesystem; choose ext4 or Btrfs" }
  }
  if(filesystem in setOf(ImageFilesystem.FAT, ImageFilesystem.FAT32)) require(paths.none { it.second > 0xffffffffL }) { "FAT32 cannot store a file larger than 4 GiB; select exFAT" }
 }
}
