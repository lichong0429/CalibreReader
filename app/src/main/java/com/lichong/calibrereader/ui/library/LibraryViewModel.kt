package com.lichong.calibrereader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.lichong.calibrereader.AppContainer
import com.lichong.calibrereader.data.db.BookEntity
import com.lichong.calibrereader.data.download.BookOpener
import com.lichong.calibrereader.ui.reader.ReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LibraryItem(
    val book: BookEntity,
    val fileAvailable: Boolean
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val items = container.bookRepo.books
        .map { list -> list.map { LibraryItem(it, runCatching { File(it.filePath).length() > 0 }.getOrDefault(false)) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun open(item: LibraryItem) {
        viewModelScope.launch {
            if (!item.fileAvailable) {
                _message.value = "文件已被删除，请重新下载"
                return@launch
            }
            container.bookRepo.markOpened(item.book.id)
            if (item.book.isReadableInApp) {
                openInApp(item.book)
            } else {
                val error = BookOpener.open(container.appContext, File(item.book.filePath))
                if (error != null) _message.value = error
            }
        }
    }

    private fun openInApp(book: BookEntity) {
        try {
            container.appContext.startActivity(
                ReaderActivity.newIntent(container.appContext, book.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            _message.value = e.message ?: "无法打开内置阅读器"
        }
    }

    fun delete(item: LibraryItem) {
        viewModelScope.launch {
            container.bookRepo.delete(item.book.id)
            _message.value = "已删除《${item.book.title}》"
        }
    }

    fun clearMessage() = _message.update { null }
}
