package com.lichong.calibrereader.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.net.OpdsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Request

/**
 * 封面加载器：OPDS 封面通常需要 Basic 认证，第三方图片库配置起来反而更绕，
 * 这里直接复用 [OpdsClient] 的认证与证书策略，配内存 LRU + OkHttp 磁盘缓存。
 */
class CoverLoader(
    context: Context,
    private val client: OpdsClient,
    private val activeConfig: () -> ServerConfig?
) {

    private val memory = object : LruCache<String, Bitmap>(memoryBudget(context)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(url: String): Bitmap? = memory.get(url)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        val cfg = activeConfig() ?: return@withContext null

        val request = Request.Builder().url(url).apply {
            if (cfg.hasAuth) {
                header("Authorization", Credentials.basic(cfg.username, cfg.password))
            }
        }.build()

        runCatching {
            client.imageClient(cfg.trustAllCerts).newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body ?: return@use null
                val len = body.contentLength()
                if (len > MAX_BYTES) return@use null
                val bytes = body.bytes()
                if (bytes.size > MAX_BYTES) return@use null
                decodeSampled(bytes, TARGET_WIDTH)?.also { memory.put(url, it) }
            }
        }.getOrNull()
    }

    private fun decodeSampled(bytes: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    companion object {
        private const val TARGET_WIDTH = 320
        private const val MAX_BYTES = 12 * 1024 * 1024

        private fun memoryBudget(context: Context): Int {
            val heap = Runtime.getRuntime().maxMemory()
            val cacheDir = (heap / 10).coerceAtMost(48L * 1024 * 1024)
            return cacheDir.toInt().coerceAtLeast(8 * 1024 * 1024)
        }
    }
}
