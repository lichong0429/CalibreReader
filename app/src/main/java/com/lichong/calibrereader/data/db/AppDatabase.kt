package com.lichong.calibrereader.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val serverName: String,
    val title: String,
    val authors: String = "",
    val format: String = "EPUB",
    val filePath: String,
    val coverUrl: String? = null,
    val calibreId: String? = null,
    /** Calibre-Web 的书籍 uuid，Kobo 进度接口需要，calibre-server 下为空 */
    val koboUuid: String? = null,
    val sizeBytes: Long = 0,
    val downloadedAt: Long = 0,
    val lastOpenedAt: Long = 0,
    /** 0f..1f，阅读器回写 */
    val progress: Float = 0f,
    /** Readium locator JSON，用于精确续读 */
    val locatorJson: String? = null
) {
    val isReadableInApp: Boolean
        get() = format == "EPUB" || format == "KEPUB"
}

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, downloadedAt) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, downloadedAt) DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE filePath = :path LIMIT 1")
    suspend fun findByPath(path: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET lastOpenedAt = :ts WHERE id = :id")
    suspend fun markOpened(id: String, ts: Long)

    @Query("UPDATE books SET progress = :progress, locatorJson = :locator, lastOpenedAt = :ts WHERE id = :id")
    suspend fun saveProgress(id: String, progress: Float, locator: String?, ts: Long)
}

@Database(entities = [BookEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN koboUuid TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "calibre_reader.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
