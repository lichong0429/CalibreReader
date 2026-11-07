package com.lichong.calibrereader.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
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
        get() = format == "EPUB" || format == "KEPUB" || format == "PDF"

    val isPdf: Boolean get() = format.equals("PDF", ignoreCase = true)
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

/**
 * 阅读批注 / 划线。
 *
 * locatorJson 是 Readium 的 Locator JSON，翻页或换字号后仍能重新定位；
 * selectedText 存一份原文快照，方便批注列表里展示，即使 EPUB 内容变了也能看懂。
 */
@Entity(tableName = "annotations", indices = [Index("bookId")])
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val selectedText: String = "",
    /** 批注内容；null 表示只有划线没有文字 */
    val note: String? = null,
    /** ARGB 颜色 */
    val color: Long = 0x66FFD54F,
    val chapterTitle: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val hasNote: Boolean get() = !note.isNullOrBlank()
}

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    suspend fun listByBook(bookId: String): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

@Database(entities = [BookEntity::class, AnnotationEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun annotationDao(): AnnotationDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN koboUuid TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `annotations` (" +
                        "`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, " +
                        "`selectedText` TEXT NOT NULL, `note` TEXT, `color` INTEGER NOT NULL, " +
                        "`chapterTitle` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "calibre_reader.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
