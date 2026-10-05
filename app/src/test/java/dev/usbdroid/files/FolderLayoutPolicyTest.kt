package dev.usbdroid.files
import dev.usbdroid.data.ImageFilesystem
import org.junit.Assert.*
import org.junit.Test

class FolderLayoutPolicyTest {
 @Test fun rejectsCaseCollisionsOnWindowsFilesystems() {
  val paths = listOf("a.txt" to 1L, "A.txt" to 1L)
  assertThrows(IllegalArgumentException::class.java) { FolderLayoutPolicy.validate(paths, ImageFilesystem.FAT32) }
  FolderLayoutPolicy.validate(paths, ImageFilesystem.EXT4)
 }
 @Test fun rejectsUnsupportedNamesBeforeCopying() {
  assertThrows(IllegalArgumentException::class.java) { FolderLayoutPolicy.validate(listOf("file?.txt" to 1L), ImageFilesystem.EXFAT) }
  FolderLayoutPolicy.validate(listOf("file?.txt" to 1L), ImageFilesystem.EXT4)
 }
 @Test fun boundsIndividualFatFiles() {
  assertThrows(IllegalArgumentException::class.java) { FolderLayoutPolicy.validate(listOf("large" to 0x100000000L), ImageFilesystem.FAT32) }
  FolderLayoutPolicy.validate(listOf("large" to 0x100000000L), ImageFilesystem.EXFAT)
 }
}
