package com.lichong.calibrereader.data.db

import kotlinx.coroutines.flow.Flow
import java.io.File

class BookRepository(private val dao: BookDao) {

    val books: Flow<List<BookEntity>> = dao.observeAll()

    suspend fun all(): List<BookEntity> = dao.getAll()

    suspend fun find(id: String): BookEntity? = dao.findById(id)

    suspend fun findByPath(path: String): BookEntity? = dao.findByPath(path)

    suspend fun save(book: BookEntity) = dao.upsert(book)

    suspend fun delete(id: String) {
        dao.findById(id)?.let { book ->
            runCatching { File(book.filePath).delete() }
        }
        dao.deleteById(id)
    }

    suspend fun deleteRecordOnly(id: String) = dao.deleteById(id)

    suspend fun markOpened(id: String) = dao.markOpened(id, System.currentTimeMillis())

    suspend fun saveProgress(id: String, progress: Float, locatorJson: String?) =
        dao.saveProgress(id, progress, locatorJson, System.currentTimeMillis())
}
