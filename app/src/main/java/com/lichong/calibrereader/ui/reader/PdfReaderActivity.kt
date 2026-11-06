package com.lichong.calibrereader.ui.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lichong.calibrereader.CalibreApp
import com.lichong.calibrereader.data.db.BookEntity
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.reader.PdfDocument
import com.lichong.calibrereader.data.sync.ReadStatus
import com.lichong.calibrereader.ui.theme.CalibreTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

data class PdfReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val pageCount: Int = 0,
    val page: Int = 0,
    val bitmap: Bitmap? = null,
    val rendering: Boolean = false,
    val chromeVisible: Boolean = true,
    val syncNote: String? = null,
    val message: String? = null
)

/** 内置 PDF 阅读器：Android 系统 PdfRenderer + Compose，不跳外部应用。 */
class PdfReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        private const val PDF_PAGE_KEY = "pdfPage"

        fun newIntent(context: Context, bookId: String): Intent =
            Intent(context, PdfReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }

    private val container get() = (application as CalibreApp).container

    private var ui by mutableStateOf(PdfReaderUiState())
    private var document: PdfDocument? = null
    private var book: BookEntity? = null
    private var server: ServerConfig? = null
    private var renderJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.themePrefs.settings.collectAsStateWithLifecycle()
            CalibreTheme(palette = settings.palette, mode = settings.mode) {
                PdfReaderScreen(
                    state = ui,
                    onBack = { finish() },
                    onPrev = { goTo(ui.page - 1) },
                    onNext = { goTo(ui.page + 1) },
                    onSeek = { goTo(it) },
                    onToggleChrome = { ui = ui.copy(chromeVisible = !ui.chromeVisible) },
                    onSyncNow = { manualSync() },
                    onClearMessage = { ui = ui.copy(message = null) }
                )
            }
        }
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId.isNullOrBlank()) {
            ui = ui.copy(loading = false, error = "缺少书籍参数")
            return
        }
        val entity = container.bookRepo.find(bookId)
        if (entity == null) {
            ui = ui.copy(loading = false, error = "书架里没有这本书，请重新下载")
            return
        }
        val file = File(entity.filePath)
        if (!file.exists() || file.length() == 0L) {
            ui = ui.copy(loading = false, error = "PDF 文件不存在或已损坏")
            return
        }
        val doc = runCatching { PdfDocument(file) }.getOrElse { e ->
            ui = ui.copy(loading = false, error = "PDF 打开失败：${e.message ?: "文件不可读"}")
            return
        }
        document = doc
        if (doc.pageCount == 0) {
            ui = ui.copy(loading = false, error = "这个 PDF 没有页面")
            return
        }
        book = entity
        val servers = container.serverStore.servers.first()
        server = servers.firstOrNull { it.id == entity.serverId } ?: servers.firstOrNull()

        val saved = entity.locatorJson?.let { raw ->
            runCatching { JSONObject(raw).optInt(PDF_PAGE_KEY, -1) }.getOrDefault(-1)
        } ?: -1
        val page = saved.takeIf { it in 0 until doc.pageCount }
            ?: (entity.progress * (doc.pageCount - 1)).roundToInt()
                .coerceIn(0, doc.pageCount - 1)

        ui = ui.copy(
            loading = false,
            title = entity.title,
            pageCount = doc.pageCount,
            page = page,
            syncNote = syncDescription()
        )
        renderCurrent()
    }

    private fun renderWidth(): Int =
        (resources.displayMetrics.widthPixels * 1.5f).roundToInt().coerceIn(720, 1440)

    private fun renderCurrent() {
        val doc = document ?: return
        val index = ui.page
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            ui = ui.copy(rendering = true)
            val bitmap = doc.render(index, renderWidth())
            // 翻页很快时旧任务可能已取消，只接受当前页的结果
            if (ui.page == index) {
                ui = ui.copy(bitmap = bitmap, rendering = bitmap == null)
            }
        }
    }

    private fun goTo(index: Int) {
        val count = ui.pageCount
        if (count <= 0) return
        val next = index.coerceIn(0, count - 1)
        if (next == ui.page) return
        ui = ui.copy(page = next)
        renderCurrent()
        saveProgress(next)
    }

    private fun percentOf(page: Int): Float {
        val count = ui.pageCount
        return if (count <= 1) 1f else (page.toFloat() / (count - 1)).coerceIn(0f, 1f)
    }

    private fun saveProgress(page: Int) {
        val b = book ?: return
        val json = runCatching {
            JSONObject().put(PDF_PAGE_KEY, page).toString()
        }.getOrNull()
        container.appScope.launch {
            container.bookRepo.saveProgress(b.id, percentOf(page), json)
        }
    }

    private fun pushRemote(page: Int) {
        val b = book ?: return
        val srv = server ?: return
        val uuid = b.koboUuid ?: return
        if (!container.progressSync.isSupported(srv)) return
        val status = when {
            percentOf(page) >= 0.99f -> ReadStatus.FINISHED
            page > 0 -> ReadStatus.READING
            else -> ReadStatus.UNREAD
        }
        container.appScope.launch {
            val result = container.progressSync.push(
                server = srv,
                uuid = uuid,
                percent = percentOf(page) * 100f,
                locationValue = null,
                source = "pdf:page:${page + 1}",
                status = status
            )
            if (result.isFailure) {
                val note = "进度同步失败：${result.exceptionOrNull()?.message.orEmpty()}"
                runOnUiThread { ui = ui.copy(syncNote = note) }
            }
        }
    }

    private fun manualSync() {
        val srv = server ?: return
        if (!container.progressSync.isSupported(srv)) {
            ui = ui.copy(message = "当前服务器不支持进度同步（需要 Calibre-Web + Kobo 令牌）")
            return
        }
        if (book?.koboUuid == null) {
            ui = ui.copy(message = "服务端未提供 uuid，进度仅保存在本机")
            return
        }
        saveProgress(ui.page)
        pushRemote(ui.page)
        ui = ui.copy(message = "已提交进度 ${(percentOf(ui.page) * 100).toInt()}%")
    }

    private fun syncDescription(): String? {
        val srv = server ?: return "进度仅保存在本机"
        val b = book ?: return "进度仅保存在本机"
        return when {
            !container.progressSync.isSupported(srv) -> "进度仅保存在本机"
            b.koboUuid == null -> "服务端未提供 uuid，进度仅保存在本机"
            else -> "进度同步至 ${srv.displayName}"
        }
    }

    override fun onStop() {
        if (ui.pageCount > 0) {
            saveProgress(ui.page)
            pushRemote(ui.page)
        }
        super.onStop()
    }

    override fun onDestroy() {
        renderJob?.cancel()
        document?.close()
        document = null
        super.onDestroy()
    }
}
