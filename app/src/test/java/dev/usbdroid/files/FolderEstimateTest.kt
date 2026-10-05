package dev.usbdroid.files
import dev.usbdroid.data.ImageFilesystem
import org.junit.Assert.*
import org.junit.Test

class FolderEstimateTest {
 @Test fun roundsUpAndIncludesFilesystemReserve() {
  val bytes = FolderImage.imageBytes(200L * 1048576, 10, ImageFilesystem.EXFAT, 16)
  assertEquals(267L * 1048576, bytes)
 }
 @Test fun honorsFilesystemMinimum() {
  ImageFilesystem.entries.filter { it != ImageFilesystem.NONE }.forEach { filesystem -> assertTrue(FolderImage.imageBytes(0, 0, filesystem, 16) >= filesystem.minMiB * 1048576) }
 }
 @Test fun rejectsOverflowsAndInvalidBounds() {
  assertThrows(ArithmeticException::class.java) { FolderImage.imageBytes(Long.MAX_VALUE, 1, ImageFilesystem.EXT4, 16) }
  assertThrows(IllegalArgumentException::class.java) { FolderImage.imageBytes(1, 1, ImageFilesystem.EXFAT, 0) }
  assertThrows(IllegalArgumentException::class.java) { FolderImage.imageBytes(1, 1, ImageFilesystem.NONE, 16) }
 }
}
