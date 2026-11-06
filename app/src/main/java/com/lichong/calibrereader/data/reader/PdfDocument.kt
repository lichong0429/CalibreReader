package com.lichong.calibrereader.data.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * 系统 PdfRenderer 封装：不引入第三方 PDF 引擎，按页渲染为 Bitmap。
 *
 * PdfRenderer 不是线程安全的，所有 openPage/render 都限制在单线程 dispatcher 上。
 */
class PdfDocument(file: File) : AutoCloseable {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    val pageCount: Int = renderer.pageCount

    /** 渲染指定页；targetWidthPx 会按安全上限裁剪，避免大页面直接 OOM。 */
    suspend fun render(pageIndex: Int, targetWidthPx: Int): Bitmap? = withContext(dispatcher) {
        if (pageIndex !in 0 until pageCount) return@withContext null
        val page = runCatching { renderer.openPage(pageIndex) }.getOrNull()
            ?: return@withContext null
        try {
            val width = targetWidthPx.coerceIn(480, 1440)
            val scale = width.toFloat() / page.width.coerceAtLeast(1).toFloat()
            val height = (page.height * scale).roundToInt().coerceIn(480, 2600)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PdfRenderer 按透明底渲染，先刷白否则 PDF 页面会发灰/发黑
            bitmap.eraseColor(Color.WHITE)
            runCatching {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }.onFailure {
                bitmap.recycle()
                return@withContext null
            }
            bitmap
        } finally {
            page.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        dispatcher.close()
    }
}
