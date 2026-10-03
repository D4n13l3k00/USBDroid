package dev.usbdroid

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.usbdroid.data.AppDatabase
import dev.usbdroid.data.Library
import dev.usbdroid.usb.UsbController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AllocationDeviceTests {
 @Test fun migrationKeepsImagesAndAddsUnknownAllocation() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val name = "allocation-${UUID.randomUUID()}.db"
  val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object: SupportSQLiteOpenHelper.Callback(2) {
   override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE images (id TEXT NOT NULL PRIMARY KEY, size INTEGER NOT NULL)"); db.execSQL("INSERT INTO images VALUES ('kept', 4194304)") }
   override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
  }).build())
  try {
   val db = helper.writableDatabase
   AppDatabase.MIGRATION_2_3.migrate(db)
   db.query("SELECT id, size, allocatedSize FROM images").use { assertTrue(it.moveToFirst()); assertEquals("kept", it.getString(0)); assertEquals(4194304L, it.getLong(1)); assertTrue(it.isNull(2)) }
  } finally { helper.close(); context.deleteDatabase(name) }
 }
 @Test fun allocationComesFromFileBlocks() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val directory = File(context.cacheDir, "allocation-${UUID.randomUUID()}").apply { mkdirs() }
  val database = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
  try {
   val library = Library(context, database.dao(), UsbController(File(directory, "usb.json")))
   val file = File(directory, "sparse.img")
   RandomAccessFile(file, "rw").use { it.setLength(64 * 1024 * 1024L) }
   val expected = android.system.Os.stat(file.path).st_blocks * 512L
   assertEquals(expected, library.allocated(file.path))
   assertEquals(64 * 1024 * 1024L, file.length())
   assertNull(library.allocated(File(directory, "missing.img").path))
  } finally { database.close(); directory.deleteRecursively() }
 }
}
