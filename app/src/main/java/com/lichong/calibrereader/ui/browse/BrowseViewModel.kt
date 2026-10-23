package com.lichong.calibrereader.ui.browse

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lichong.calibrereader.AppContainer
import com.lichong.calibrereader.data.db.BookEntity
import com.lichong.calibrereader.data.download.BookOpener
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.net.OpdsHttpException
import com.lichong.calibrereader.data.opds.OpdsEntry
import com.lichong.calibrereader.data.opds.OpdsFeed
import com.lichong.calibrereader.data.opds.OpdsLink
import com.lichong.calibrereader.ui.reader.ReaderActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder

data class Crumb(val url: String, val title: String)

sealed interface DownloadState {
    data object Idle : DownloadState

    data class Running(val label: String, val downloaded: Long, val total: Long) : DownloadState {
        val fraction: Float
            get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
        val indeterminate: Boolean get() = total <= 0
    }

    data class Done(val path: String, val name: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

data class BrowseUiState(
    val loading: Boolean = true,
    val entries: List<OpdsEntry> = emptyList(),
    val feed: OpdsFeed? = null,
    val nextUrl: String? = null,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val crumbs: List<Crumb> = emptyList(),
    val searchTemplate: String? = null,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val selected: OpdsEntry? = null,
    val download: DownloadState = DownloadState.Idle,
    val message: String? = null
) {
    val canGoBack: Boolean get() = crumbs.size > 1
    val title: String get() = feed?.title?.takeIf { it.isNotBlank() } ?: crumbs.lastOrNull()?.title.orEmpty()
}

class BrowseViewModel(
    private val container: AppContainer,
    private val server: ServerConfig
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state = _state.asStateFlow()

    private val stack = mutableListOf<Crumb>()
    private var loadJob: Job? = null

    init {
        val root = server.opdsRootUrl.ifBlank { server.baseUrl.trimEnd('/') + "/opds/" }
        stack.add(Crumb(root, ""))
        load(root)
    }

    private fun load(url: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = container.opdsClient.fetch(server, url)
                val feedTitle = resp.feed.title.orEmpty()
                val idx = stack.indexOfLast { it.url == url }
                if (idx >= 0 && stack[idx].title.isBlank()) {
                    stack[idx] = stack[idx].copy(title = feedTitle)
                }
                _state.update {
                    it.copy(
                        loading = false,
                        feed = resp.feed,
                        entries = resp.feed.entries,
                        nextUrl = resp.feed.nextUrl,
                        searchTemplate = resp.feed.searchTemplate,
                        crumbs = stack.toList(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                val msg = when (e) {
                    is OpdsHttpException -> when (e.code) {
                        401, 403 -> "认证失败（HTTP ${e.code}），请检查用户名与密码"
                        404 -> "目录不存在（HTTP 404）"
                        else -> "服务端错误（HTTP ${e.code}）"
                    }

                    else -> e.message ?: "加载失败"
                }
                _state.update { it.copy(loading = false, error = msg, crumbs = stack.toList()) }
            }
        }
    }

    fun openEntry(entry: OpdsEntry) {
        if (entry.isBook) {
            select(entry)
            return
        }
        val nav = entry.navLinks.firstOrNull()
        if (nav == null) {
            _state.update { it.copy(message = "该条目没有可打开的内容") }
            return
        }
        stack.add(Crumb(nav.href, entry.title))
        load(nav.href)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        load(stack.last().url)
        return true
    }

    fun refresh() {
        stack.lastOrNull()?.let { load(it.url) }
    }

    fun loadMore() {
        val next = _state.value.nextUrl ?: return
        val cur = _state.value
        if (cur.loading || cur.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val resp = container.opdsClient.fetch(server, next)
                _state.update { s ->
                    val existing = s.entries.map { it.id }.toHashSet()
                    s.copy(
                        loadingMore = false,
                        entries = s.entries + resp.feed.entries.filterNot { it.id in existing },
                        nextUrl = resp.feed.nextUrl
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loadingMore = false, message = e.message ?: "加载下一页失败")
                }
            }
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val tpl = _state.value.searchTemplate
        if (tpl.isNullOrBlank()) {
            _state.update { it.copy(message = "该目录不支持搜索") }
            return
        }
        val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        val url = if (tpl.contains("{searchTerms}")) {
            tpl.replace("{searchTerms}", encoded)
        } else {
            tpl + (if (tpl.contains('?')) "&query=" else "?query=") + encoded
        }
        stack.add(Crumb(url, "搜索：$q"))
        _state.update { it.copy(searchActive = false) }
        load(url)
    }

    fun setSearchActive(active: Boolean) = _state.update {
        it.copy(searchActive = active, searchQuery = if (active) it.searchQuery else "")
    }

    fun setQuery(q: String) = _state.update { it.copy(searchQuery = q) }

    fun select(entry: OpdsEntry?) = _state.update { it.copy(selected = entry) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearDownload() = _state.update { it.copy(download = DownloadState.Idle) }

    fun download(entry: OpdsEntry, link: OpdsLink) {
        if (_state.value.download is DownloadState.Running) return
        viewModelScope.launch {
            _state.update { it.copy(download = DownloadState.Running(link.format.label, 0, -1)) }
            try {
                val file = container.downloader.download(server, link, entry.title) { done, total ->
                    _state.update { s ->
                        val running = s.download as? DownloadState.Running ?: return@update s
                        s.copy(download = running.copy(downloaded = done, total = total))
                    }
                }
                container.bookRepo.save(
                    BookEntity(
                        id = "${server.id}:${entry.id}",
                        serverId = server.id,
                        serverName = server.displayName,
                        title = entry.title,
                        authors = entry.authorLine,
                        format = link.format.name,
                        filePath = file.absolutePath,
                        coverUrl = entry.coverUrl,
                        calibreId = entry.calibreId,
                        koboUuid = entry.koboUuid,
                        sizeBytes = file.length(),
                        downloadedAt = System.currentTimeMillis()
                    )
                )
                _state.update { it.copy(download = DownloadState.Done(file.absolutePath, file.name)) }
            } catch (e: Exception) {
                _state.update { it.copy(download = DownloadState.Failed(e.message ?: "下载失败")) }
            }
        }
    }

    fun openLocalFile(path: String) {
        val ctx = container.appContext
        viewModelScope.launch {
            val book = runCatching { container.bookRepo.findByPath(path) }.getOrNull()
            if (book != null && book.isReadableInApp) {
                container.bookRepo.markOpened(book.id)
                runCatching {
                    ctx.startActivity(
                        ReaderActivity.newIntent(ctx, book.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { e ->
                    _state.update { it.copy(message = e.message ?: "无法打开内置阅读器") }
                }
                return@launch
            }
            val error = BookOpener.open(ctx, File(path))
            if (error != null) {
                _state.update { it.copy(message = error) }
            } else if (book != null) {
                container.bookRepo.markOpened(book.id)
            }
        }
    }
}
