package com.lichong.calibrereader.data.net

import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.model.ServerKind
import com.lichong.calibrereader.data.opds.OpdsFeed
import com.lichong.calibrereader.data.prefs.ServerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

sealed interface ProbeResult {
    data class Success(
        val config: ServerConfig,
        val feed: OpdsFeed,
        val notes: List<String> = emptyList()
    ) : ProbeResult

    data class Failure(val attempts: List<Attempt>) : ProbeResult {
        val summary: String
            get() = attempts.lastOrNull()?.error ?: "探测失败"
    }

    data class Attempt(val url: String, val error: String?)
}

/**
 * 探测自建 Calibre 服务：找出可用 OPDS 根地址并判断是 Calibre-Web 还是 calibre-server。
 * 判定只作展示用途，功能路径全部走 OPDS 链接，不依赖类型。
 */
class ServerProbe(private val client: OpdsClient) {

    suspend fun probe(draft: ServerConfig): ProbeResult = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(draft.baseUrl)
        if (base.isBlank()) {
            return@withContext ProbeResult.Failure(
                listOf(ProbeResult.Attempt("", "请填写服务器地址，例如 http://192.168.1.10:8083"))
            )
        }
        val attempts = mutableListOf<ProbeResult.Attempt>()
        for (candidate in candidates(base)) {
            try {
                val resp = client.fetch(draft, candidate)
                if (resp.feed.entries.isEmpty() && resp.feed.facets.isEmpty()) {
                    attempts.add(ProbeResult.Attempt(candidate, "该地址不是 OPDS 目录（无条目）"))
                    continue
                }
                val kind = detectKind(resp.feed, resp.serverHeader, resp.contentType)
                val notes = buildList {
                    if (resp.contentType?.contains("xml", true) != true) {
                        add("响应不是 XML，可能被登录页或反向代理拦截")
                    }
                    if (resp.feed.searchTemplate == null) add("该服务未提供 OPDS 搜索")
                    if (kind == ServerKind.UNKNOWN) add("未能识别服务类型，功能仍可正常使用")
                }
                return@withContext ProbeResult.Success(
                    config = draft.copy(opdsRootUrl = resp.finalUrl, kind = kind),
                    feed = resp.feed,
                    notes = notes
                )
            } catch (e: Exception) {
                val msg = when (e) {
                    is OpdsHttpException -> when (e.code) {
                        401, 403 -> "认证失败（HTTP ${e.code}），请检查用户名与密码"
                        404 -> "该路径不存在（HTTP 404）"
                        else -> "服务端返回 HTTP ${e.code}"
                    }
                    else -> e.message ?: e.javaClass.simpleName
                }
                attempts.add(ProbeResult.Attempt(candidate, msg))
            }
        }
        ProbeResult.Failure(attempts)
    }

    /** 只做形式探测，不校验网络 */
    fun candidates(base: String): List<String> {
        val trimmed = base.trimEnd('/')
        val list = mutableListOf<String>()
        if (trimmed.contains("/opds", ignoreCase = true)) {
            list.add(if (trimmed.endsWith("/")) trimmed else "$trimmed/")
        } else {
            list.add("$trimmed/opds/")
            list.add("$trimmed/opds")
            list.add("$trimmed/opds/v1.2/catalog")
        }
        // 用户可能直接填了完整 OPDS 地址
        if (!list.contains(base)) list.add(base)
        return list.distinct()
    }

    fun detectKind(feed: OpdsFeed, serverHeader: String?, contentType: String?): ServerKind {
        val server = serverHeader.orEmpty().lowercase()
        if (server.contains("calibre-web") || server.contains("tornado")) return ServerKind.CALIBRE_WEB
        if (server.startsWith("calibre")) return ServerKind.CALIBRE_SERVER

        val titles = feed.entries.map { it.title.lowercase().trim() }
        var webScore = 0
        var serverScore = 0
        val webHints = listOf("hot books", "top rated", "read books", "unread books", "archived books", "discover")
        val serverHints = listOf("by newest", "by title", "by author", "by series", "by publisher", "by rating", "by tag")
        webHints.forEach { h -> if (titles.any { it.contains(h) }) webScore++ }
        serverHints.forEach { h -> if (titles.any { it.contains(h) }) serverScore++ }

        return when {
            webScore > serverScore -> ServerKind.CALIBRE_WEB
            serverScore > webScore -> ServerKind.CALIBRE_SERVER
            else -> ServerKind.UNKNOWN
        }
    }

    companion object {
        fun normalizeBaseUrl(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return ""
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                s = "http://$s"
            }
            s = s.trimEnd('/')
            // 若用户粘贴了带 /opds 的完整地址，回退到站点根
            val idx = s.indexOf("/opds", ignoreCase = true)
            if (idx > 0) s = s.substring(0, idx)
            return try {
                val uri = URI(s)
                if (uri.host.isNullOrBlank()) "" else s
            } catch (e: Exception) {
                ""
            }
        }

        fun guessName(config: ServerConfig): String = try {
            URI(config.baseUrl).host ?: config.baseUrl
        } catch (e: Exception) {
            config.baseUrl
        }

        fun newDraft(): ServerConfig = ServerConfig(
            id = ServerStore.newId(),
            baseUrl = "",
            name = ""
        )
    }
}
