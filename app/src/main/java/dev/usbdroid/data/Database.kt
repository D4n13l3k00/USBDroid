package dev.usbdroid.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "images")
data class ImageEntry(@PrimaryKey val id: String, val title: String, val location: String, val physicalPath: String?, val size: Long, val modified: Long, val storageId: String, val hidden: Boolean = false, @ColumnInfo(defaultValue = "NULL") val allocatedSize: Long? = null, @ColumnInfo(defaultValue = "'IMAGE'") val kind: String = "IMAGE", @ColumnInfo(defaultValue = "0") val mtpReadOnly: Boolean = false) {
 @get:Ignore val isMtp: Boolean get() = kind == "MTP"
 @get:Ignore val path: String get() = id
 @get:Ignore val file: java.io.File get() = java.io.File(physicalPath ?: location)
}
@Entity(tableName = "storage")
data class StorageLocation(@PrimaryKey val id: String, val title: String, val location: String, val kind: String, val primary: Boolean = false)
@Entity(tableName = "repositories")
data class CatalogRepository(@PrimaryKey val id: String, val title: String, val url: String, val enabled: Boolean = true, val allowHttp: Boolean = false)
@Entity(tableName = "jobs")
data class TransferJob(@PrimaryKey val id: String, val kind: String, val title: String, val args: String, val state: String = "QUEUED", val progress: Long = 0, val total: Long = 0, val result: String = "", val error: String = "", val etag: String = "", val lastModified: String = "", @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis())

@Dao interface AppDao {
 @Query("SELECT * FROM images WHERE hidden = 0 ORDER BY title COLLATE NOCASE") fun images(): Flow<List<ImageEntry>>
 @Query("SELECT * FROM images") suspend fun allImages(): List<ImageEntry>
 @Query("SELECT * FROM images WHERE id = :id") suspend fun image(id: String): ImageEntry?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putImage(value: ImageEntry)
 @Query("DELETE FROM images WHERE id = :id") suspend fun deleteImage(id: String)
 @Query("UPDATE images SET allocatedSize = :bytes WHERE id = :id") suspend fun imageAllocation(id: String, bytes: Long?)
 @Query("UPDATE images SET hidden = 0") suspend fun revealImages()
 @Query("SELECT * FROM storage ORDER BY `primary` DESC, title") fun storage(): Flow<List<StorageLocation>>
 @Query("SELECT * FROM storage ORDER BY `primary` DESC, title") suspend fun allStorage(): List<StorageLocation>
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStorage(value: StorageLocation)
 @Query("UPDATE storage SET `primary` = 0") suspend fun clearPrimary()
 @Query("DELETE FROM storage WHERE id = :id") suspend fun deleteStorage(id: String)
 @Query("SELECT * FROM repositories ORDER BY title") fun repositories(): Flow<List<CatalogRepository>>
 @Query("SELECT * FROM repositories ORDER BY title") suspend fun allRepositories(): List<CatalogRepository>
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRepository(value: CatalogRepository)
 @Query("DELETE FROM repositories WHERE id = :id") suspend fun deleteRepository(id: String)
 @Query("SELECT * FROM jobs ORDER BY rowid DESC") fun jobs(): Flow<List<TransferJob>>
 @Query("SELECT * FROM jobs WHERE id = :id") suspend fun job(id: String): TransferJob?
 @Query("SELECT * FROM jobs WHERE state IN ('RUNNING','QUEUED')") suspend fun pendingJobs(): List<TransferJob>
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putJob(value: TransferJob)
 @Query("UPDATE jobs SET state = :state WHERE id = :id") suspend fun jobState(id: String, state: String)
 @Query("DELETE FROM jobs WHERE id = :id") suspend fun deleteJob(id: String)
}
@Database(entities = [ImageEntry::class, StorageLocation::class, CatalogRepository::class, TransferJob::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
 abstract fun dao(): AppDao
 companion object {
  val MIGRATION_1_2 = object: Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE jobs ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0") } }
  val MIGRATION_2_3 = object: Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE images ADD COLUMN allocatedSize INTEGER DEFAULT NULL") } }
  val MIGRATION_3_4 = object: Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE images ADD COLUMN kind TEXT NOT NULL DEFAULT 'IMAGE'"); db.execSQL("ALTER TABLE images ADD COLUMN mtpReadOnly INTEGER NOT NULL DEFAULT 0") } }
  fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, "usbdroid.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
 }
}
