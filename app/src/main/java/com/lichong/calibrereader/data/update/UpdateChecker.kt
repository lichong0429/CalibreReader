package com.lichong.calibrereader.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
private data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

data class UpdateInfo(
    /** 归一化后的版本号，如 0.3.0 */
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
}

/**
 * 基于 GitHub Releases 的版本检查。
 *
 * 注意：这里必须用**独立的** OkHttpClient —— 不能复用 OpdsClient 里的实例，
 * 那套带 Basic 认证拦截器，会把 Calibre 服务器的用户名密码发给 api.github.com。
 */
class UpdateChecker(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CalibreReader-Android")
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext UpdateResult.UpToDate
            if (!resp.isSuccessful) throw IOException("GitHub 返回 HTTP ${resp.code}")
            val text = resp.body?.string() ?: throw IOException("响应为空")
            val rel = json.decodeFromString<GhRelease>(text)

            val remote = normalizeVersion(rel.tagName)
            if (remote.isBlank()) throw IOException("无法解析版本号：${rel.tagName}")
            if (compareVersions(remote, currentVersion) <= 0) return@withContext UpdateResult.UpToDate

            val apk = rel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: throw IOException("该 Release 没有 APK 附件")

            UpdateResult.Available(
                UpdateInfo(
                    version = remote,
                    title = rel.name?.takeIf { it.isNotBlank() } ?: "v$remote",
                    notes = rel.body.orEmpty().trim(),
                    apkUrl = apk.downloadUrl,
                    sizeBytes = apk.size,
                    pageUrl = rel.htmlUrl,
                )
            )
        }
    }

    /** 下载 APK 到缓存目录，先写 .part 再改名，避免半包被当成完整文件。 */
    suspend fun downloadApk(
        info: UpdateInfo,
        destDir: File,
        onProgress: (done: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val target = File(destDir, "CalibreReader-${info.version}.apk")
        if (target.exists() && target.length() > 0 &&
            (info.sizeBytes <= 0 || target.length() == info.sizeBytes)
        ) {
            return@withContext target
        }

        val part = File(destDir, "${target.name}.part")
        part.delete()
        val req = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "CalibreReader-Android")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("下载失败 HTTP ${resp.code}")
            val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: info.sizeBytes
            resp.body?.byteStream()?.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastTick = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        done += n
                        // 限流回调，避免每 64KB 就刷一次 UI
                        if (done - lastTick > 512 * 1024) {
                            lastTick = done
                            onProgress(done, total)
                        }
                    }
                    out.flush()
                    onProgress(done, total)
                }
            }
        }

        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        target
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/lichong0429/CalibreReader/releases/latest"

        /** 去掉 v 前缀与预发布后缀，只留数字段 */
        fun normalizeVersion(raw: String): String =
            raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
                .trim()

        /**
         * 语义化版本比较。a > b 返回正数，相等返回 0。
         * 缺失段按 0 处理，非数字段按 0 处理（避免 "1.0.beta" 抛异常）。
         */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String): List<Int> =
                normalizeVersion(v).split('.')
                    .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x - y
            }
            return 0
        }
    }
}
