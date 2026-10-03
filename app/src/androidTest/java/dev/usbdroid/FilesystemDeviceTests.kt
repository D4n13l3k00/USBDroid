package dev.usbdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.usbdroid.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class FilesystemDeviceTests {
 @Test fun createsEveryFilesystemWithExpectedSignature() = runBlocking {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val directory = File(context.cacheDir, "filesystem-test").apply { mkdirs() }
  for(format in ImageFilesystem.entries.filter { it != ImageFilesystem.NONE }) {
   val image = File(directory, ".${format.name}.partial")
   try {
    RandomAccessFile(image, "rw").use { it.setLength(format.minMiB * 1048576) }
    ImageFormatter.format(context, image, directory, format)
    RandomAccessFile(image, "r").use { input ->
     when(format) {
      ImageFilesystem.FAT, ImageFilesystem.FAT32 -> { input.seek(if(format == ImageFilesystem.FAT) 54 else 82); val signature = ByteArray(8); input.readFully(signature); assertEquals(if(format == ImageFilesystem.FAT) "FAT16   " else "FAT32   ", String(signature)) }
      ImageFilesystem.EXFAT, ImageFilesystem.NTFS -> { input.seek(3); val signature = ByteArray(8); input.readFully(signature); assertEquals(if(format == ImageFilesystem.EXFAT) "EXFAT   " else "NTFS    ", String(signature)) }
      ImageFilesystem.EXT4 -> { input.seek(1080); assertEquals(0x53, input.read()); assertEquals(0xef, input.read()) }
      ImageFilesystem.BTRFS -> { input.seek(65600); val signature = ByteArray(8); input.readFully(signature); assertEquals("_BHRfS_M", String(signature)) }
      else -> error("Unexpected format")
     }
    }
    assertEquals(format.minMiB * 1048576, image.length())
   } finally { image.delete() }
  }
  directory.delete(); Unit
 }
 @Test fun refusesExistingImagesAndInvalidSizes() = runBlocking {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val directory = File(context.cacheDir, "filesystem-guard").apply { mkdirs() }
  val image = File(directory, "existing.img").apply { writeText("preserve me") }
  try { assertTrue(runCatching { ImageFormatter.format(context, image, directory, ImageFilesystem.NTFS) }.isFailure); assertEquals("preserve me", image.readText()) } finally { image.delete(); directory.delete(); Unit }
 }
}
