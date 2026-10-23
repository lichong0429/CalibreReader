package com.lichong.calibrereader.data.sync

import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.net.OpdsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** 服务端已保存的阅读进度 */
data class RemoteProgress(
    /** 0..100，Kobo 协议用百分数 */
    val percent: Float? = null,
    val locationValue: String? = null,
    val locationType: String? = null,
    val status: String? = null,
    val lastModified: String? = null
) {
    /** 我们自己写入的 locator JSON，可直接恢复精确位置 */
    val isOwnLocator: Boolean
        get() = locationType == ProgressSync.TYPE_LOCATOR && !locationValue.isNullOrBlank()
}

enum class ReadStatus(val wire: String) {
    UNREAD("ReadyToRead"),
    READING("Reading"),
    FINISHED("Finished")
}

/**
 * Calibre-Web 阅读进度同步。
 *
 * 走 Kobo 官方第三方客户端协议（Calibre-Web 为 Kobo 设备实现的那一套）：
 *   GET  /kobo/<token>/v1/library/<uuid>/state
 *   PUT  /kobo/<token>/v1/library/<uuid>/state
 * 令牌是用户在 Calibre-Web「用户设置 → Kobo 授权令牌」里生成的一串 32 位 hex，
 * 直接拼在 URL 里，不需要会话 Cookie，也没有 CSRF。
 *
 * calibre-server 没有进度 API，[isSupported] 直接返回 false，进度只存本地。
 */
class ProgressSync(private val opdsClient: OpdsClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isSupported(server: ServerConfig): Boolean =
        server.kind == com.lichong.calibrereader.data.model.ServerKind.CALIBRE_WEB &&
            server.koboToken.isNotBlank()

    private fun stateUrl(server: ServerConfig, uuid: String): String {
        val base = server.baseUrl.trimEnd('/')
        return "$base/kobo/${server.koboToken}/v1/library/$uuid/state"
    }

    suspend fun pull(server: ServerConfig, uuid: String): Result<RemoteProgress> =
        withContext(Dispatchers.IO) {
            if (!isSupported(server)) return@withContext Result.success(RemoteProgress())
            val request = Request.Builder()
                .url(stateUrl(server, uuid))
                .header("Accept", "application/json")
                .apply { authHeader(server)?.let { header("Authorization", it) } }
                .get()
                .build()
            runCatching {
                val text = execute(server, request)
                parseState(text)
            }.recoverCatching { throw it }
        }

    suspend fun push(
        server: ServerConfig,
        uuid: String,
        percent: Float,
        locationValue: String?,
        source: String?,
        status: ReadStatus
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isSupported(server)) return@withContext Result.success(Unit)
        val clamped = percent.coerceIn(0f, 100f)
        val payload = buildJsonObject {
            putJsonArray("ReadingStates") {
                addJsonObject {
                    putJsonObject("CurrentBookmark") {
                        put("ProgressPercent", clamped)
                        put("ContentSourceProgressPercent", clamped)
                        putJsonObject("Location") {
                            put("Value", locationValue ?: "")
                            put("Type", if (locationValue.isNullOrBlank()) "" else TYPE_LOCATOR)
                            put("Source", source ?: "")
                        }
                    }
                    putJsonObject("Statistics") {
                        // 服务端要求这两个键存在，否则 400；客户端不统计阅读时长
                        put("SpentReadingMinutes", 0)
                        put("RemainingTimeMinutes", 0)
                    }
                    putJsonObject("StatusInfo") { put("Status", status.wire) }
                }
            }
        }
        val request = Request.Builder()
            .url(stateUrl(server, uuid))
            .header("Accept", "application/json")
            .apply { authHeader(server)?.let { header("Authorization", it) } }
            .put(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        runCatching { execute(server, request); Unit }
            .recoverCatching { throw it }
    }

    private fun execute(server: ServerConfig, request: Request): String {
        val response = try {
            opdsClient.client(server.trustAllCerts).newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("无法连接服务器：${e.message.orEmpty()}", e)
        }
        response.use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException(describe(resp.code, body))
            }
            return body
        }
    }

    private fun describe(code: Int, body: String): String {
        val hint = when (code) {
            401, 403 -> "Kobo 令牌无效或无权限，请在 Calibre-Web 用户设置里重新生成"
            404 -> "服务端没有这本书（uuid 不匹配），可能是 OPDS 缓存过期"
            400 -> "服务端拒绝了进度数据（协议不匹配）"
            else -> ""
        }
        val snippet = body.take(120).replace("\n", " ")
        return "HTTP $code ${if (hint.isEmpty()) snippet else hint}"
    }

    private fun authHeader(server: ServerConfig): String? =
        if (server.hasAuth) Credentials.basic(server.username, server.password) else null

    private fun parseState(text: String): RemoteProgress {
        if (text.isBlank()) return RemoteProgress()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return RemoteProgress()
        val item = runCatching { root.jsonArray.first().jsonObject }.getOrNull()
            ?: runCatching { root.jsonObject }.getOrNull()
            ?: return RemoteProgress()

        val bookmark = item["CurrentBookmark"]?.jsonObject
        val location = bookmark?.get("Location")?.jsonObject
        val status = item["StatusInfo"]?.jsonObject?.get("Status")?.jsonPrimitive?.contentOrNull
        return RemoteProgress(
            percent = bookmark?.get("ProgressPercent")?.jsonPrimitive?.floatOrNull,
            locationValue = location?.get("Value")?.jsonPrimitive?.contentOrNull,
            locationType = location?.get("Type")?.jsonPrimitive?.contentOrNull,
            status = status,
            lastModified = item["LastModified"]?.jsonPrimitive?.contentOrNull
        )
    }

    companion object {
        /** Location.Type 标记位，用来区分「我们写的 Readium locator」与 Kobo 设备的 KoboSpan */
        const val TYPE_LOCATOR = "CalibreReaderLocator"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
