package com.lichong.calibrereader.data.download

import android.content.Context
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.net.OpdsClient
import com.lichong.calibrereader.data.opds.BookFormat
import com.lichong.calibrereader.data.opds.OpdsLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 下载到应用私有目录 books/，绕开 scoped storage 与 SAF 授权。
 * 用 .part 临时文件 + 完成后重命名，避免半包被当成完整文件打开。
 */
class BookDownloader(
    private val context: Context,
    private val client: OpdsClient
) {

    val booksDir: File
        get() = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }

    suspend fun download(
        config: ServerConfig,
        link: OpdsLink,
        title: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(link.href)
            .apply {
                if (config.hasAuth) {
                    header("Authorization", Credentials.basic(config.username, config.password))
                }
            }
            .build()

        val response = client.client(config.trustAllCerts).newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("下载失败：HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("下载失败：响应为空")
            val total = body.contentLength()

            val fileName = buildFileName(title, link, resp.header("Content-Disposition"))
            val target = File(booksDir, fileName)
            val tmp = File(booksDir, "$fileName.part")

            body.byteStream().use { ins ->
                tmp.outputStream().use { outs ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastNotify = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        outs.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 150) {
                            lastNotify = now
                            onProgress(done, if (total > 0) total else -1)
                        }
                    }
                    outs.flush()
                    onProgress(done, if (total > 0) total else done)
                }
            }

            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("无法写入目标文件")
            target
        }
    }

    private fun buildFileName(title: String, link: OpdsLink, disposition: String?): String {
        val fromDisposition = disposition?.let { parseFileName(it) }
        val ext = link.format.takeIf { it != BookFormat.OTHER }?.extension
            ?: fromDisposition?.substringAfterLast('.', "")
            ?: BookFormat.fromUrl(link.href).extension
        val rawName = fromDisposition?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: title
        val safe = rawName.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .take(90)
            .ifBlank { "book" }
        return if (ext.isNullOrBlank()) safe else "$safe.$ext"
    }

    private fun parseFileName(disposition: String): String? {
        val star = Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.get(1)
        if (!star.isNullOrBlank()) {
            return runCatching { java.net.URLDecoder.decode(star.trim(), "UTF-8") }.getOrNull()
        }
        val plain = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.get(1)
        return plain?.trim()
    }

    fun existingFile(title: String, format: BookFormat): File? {
        val safe = title.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .take(90)
            .ifBlank { "book" }
        val f = File(booksDir, "$safe.${format.extension}")
        return f.takeIf { it.exists() && it.length() > 0 }
    }
}
