package com.lichong.calibrereader.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.lichong.calibrereader.CalibreApp
import com.lichong.calibrereader.R
import com.lichong.calibrereader.data.db.BookEntity
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.sync.ReadStatus
import com.lichong.calibrereader.ui.theme.CalibreTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import java.io.File

data class TocItem(val title: String, val level: Int, val link: Link)

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val authors: String = "",
    val toc: List<TocItem> = emptyList(),
    val progress: Double = 0.0,
    val prefs: ReaderPrefs = ReaderPrefs(),
    val chromeVisible: Boolean = true,
    val syncNote: String? = null,
    val message: String? = null,
    val attached: Boolean = false
)

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        private const val TAG_NAV = "epub_navigator"
        private const val LOCAL_SAVE_INTERVAL = 3_000L
        private const val REMOTE_PUSH_INTERVAL = 45_000L

        fun newIntent(context: Context, bookId: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }

    private val container get() = (application as CalibreApp).container

    private var ui by mutableStateOf(ReaderUiState())

    private var publication: Publication? = null
    private var book: BookEntity? = null
    private var server: ServerConfig? = null
    private var prefs = ReaderPrefs()

    private var progressJob: Job? = null
    private var lastLocalSave = 0L
    private var lastRemotePush = 0L
    private var currentLocator: Locator? = null
    /** 容器尚未创建时暂存待挂载参数 */
    private var pendingNav: Pair<Publication, Locator?>? = null

    private val navigator: EpubNavigatorFragment?
        get() = supportFragmentManager.findFragmentByTag(TAG_NAV) as? EpubNavigatorFragment

    private val visualNavigator: OverflowableNavigator?
        get() = navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Readium navigator 绑定了内存中的 Publication，进程重建后无法恢复，直接重启
        if (savedInstanceState != null) {
            startActivity(Intent(this, ReaderActivity::class.java).putExtras(intent))
            finish()
            return
        }

        prefs = ReaderPrefs.load(this)
        ui = ui.copy(prefs = prefs)

        setContent {
            CalibreTheme {
                ReaderScreen(
                    state = ui,
                    onBack = { finish() },
                    onToggleChrome = { ui = ui.copy(chromeVisible = !ui.chromeVisible) },
                    onGoTo = { item -> item?.let { navigator?.go(it.link, animated = false) } },
                    onNext = { visualNavigator?.goForward(animated = true) },
                    onPrev = { visualNavigator?.goBackward(animated = true) },
                    onPrefsChange = { updated ->
                        prefs = updated
                        prefs.save(this)
                        ui = ui.copy(prefs = updated)
                        navigator?.submitPreferences(updated.toEpubPreferences())
                    },
                    onSyncNow = { manualSync() },
                    onClearMessage = { ui = ui.copy(message = null) },
                    onNavigatorHostReady = { attachNavigatorIfPossible() }
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
        book = entity
        val servers = container.serverStore.servers.first()
        server = servers.firstOrNull { it.id == entity.serverId } ?: servers.firstOrNull()
        ui = ui.copy(title = entity.title, authors = entity.authors)

        val opened = container.publicationLoader.open(File(entity.filePath))
        val pub = opened.getOrElse { e ->
            ui = ui.copy(loading = false, error = e.message ?: "打开失败")
            return
        }
        publication = pub

        val initial = resolveInitialLocator(pub, entity)
        val toc = runCatching { buildToc(pub) }.getOrDefault(emptyList())

        pendingNav = pub to initial
        ui = ui.copy(
            loading = false,
            toc = toc,
            progress = initial?.locations?.totalProgression ?: entity.progress.toDouble(),
            syncNote = syncDescription()
        )
        attachNavigatorIfPossible()
    }

    /** 本地优先；本地明显落后于服务端时改用服务端进度 */
    private suspend fun resolveInitialLocator(pub: Publication, entity: BookEntity): Locator? {
        val local = entity.locatorJson?.let { raw ->
            runCatching { Locator.fromJSON(JSONObject(raw)) }.getOrNull()
        }
        val localPercent = (local?.locations?.totalProgression ?: entity.progress.toDouble()) * 100.0

        val srv = server ?: return local
        val uuid = entity.koboUuid ?: return local
        if (!container.progressSync.isSupported(srv)) return local

        val remote = container.progressSync.pull(srv, uuid).getOrNull() ?: return local
        val remotePercent = remote.percent ?: return local
        if (remotePercent <= localPercent + 1.0) return local

        if (remote.isOwnLocator) {
            val remoteLocator = runCatching {
                Locator.fromJSON(JSONObject(remote.locationValue!!))
            }.getOrNull()
            if (remoteLocator != null) {
                container.bookRepo.saveProgress(
                    entity.id,
                    (remotePercent / 100.0).toFloat().coerceIn(0f, 1f),
                    remote.locationValue
                )
                ui = ui.copy(syncNote = "已同步服务端进度 ${remotePercent.toInt()}%")
                return remoteLocator
            }
        }
        val byProgression = runCatching {
            pub.locateProgression((remotePercent / 100.0).coerceIn(0.0, 1.0))
        }.getOrNull()
        if (byProgression != null) {
            ui = ui.copy(syncNote = "已同步服务端进度 ${remotePercent.toInt()}%")
        }
        return byProgression ?: local
    }

    private fun buildToc(pub: Publication): List<TocItem> {
        val out = mutableListOf<TocItem>()
        fun walk(links: List<Link>, level: Int) {
            for (link in links) {
                out.add(TocItem(link.title.orEmpty().ifBlank { "未命名章节" }, level, link))
                if (link.children.isNotEmpty() && level < 4) walk(link.children, level + 1)
            }
        }
        walk(pub.manifest.tableOfContents, 0)
        return out
    }

    private fun attachNavigatorIfPossible() {
        val (pub, initial) = pendingNav ?: return
        if (navigator != null) return
        if (supportFragmentManager.isStateSaved) return
        val host = findViewById<android.view.View>(R.id.reader_fragment_container) ?: return
        pendingNav = null

        val factory = EpubNavigatorFactory(pub).createFragmentFactory(
            initialLocator = initial,
            initialPreferences = prefs.toEpubPreferences()
        )
        val fragment = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name)
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(host.id, fragment, TAG_NAV)
        }
        supportFragmentManager.executePendingTransactions()
        ui = ui.copy(attached = true)
        observeProgress()
    }

    private fun observeProgress() {
        val nav = navigator ?: return
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            nav.currentLocator.collect { locator ->
                currentLocator = locator
                val percent = percentOf(locator)
                ui = ui.copy(progress = percent)

                val now = SystemClock.elapsedRealtime()
                if (now - lastLocalSave > LOCAL_SAVE_INTERVAL) {
                    lastLocalSave = now
                    saveLocal(percent, locator)
                }
                if (now - lastRemotePush > REMOTE_PUSH_INTERVAL) {
                    lastRemotePush = now
                    pushRemote(percent, locator)
                }
            }
        }
    }

    private fun percentOf(locator: Locator): Double {
        locator.locations.totalProgression?.let { return it }
        val order = publication?.readingOrder ?: return 0.0
        if (order.isEmpty()) return 0.0
        val idx = order.indexOfFirst { it.href.toString() == locator.href.toString() }
        return if (idx < 0) 0.0 else (idx + 1).toDouble() / order.size
    }

    private fun saveLocal(percent: Double, locator: Locator) {
        val b = book ?: return
        val json = runCatching { locator.toJSON().toString() }.getOrNull()
        val value = percent.toFloat().coerceIn(0f, 1f)
        container.appScope.launch { container.bookRepo.saveProgress(b.id, value, json) }
    }

    private fun pushRemote(percent: Double, locator: Locator) {
        val b = book ?: return
        val srv = server ?: return
        val uuid = b.koboUuid ?: return
        if (!container.progressSync.isSupported(srv)) return
        val json = runCatching { locator.toJSON().toString() }.getOrNull()
        val status = when {
            percent >= 0.99 -> ReadStatus.FINISHED
            percent > 0.0 -> ReadStatus.READING
            else -> ReadStatus.UNREAD
        }
        container.appScope.launch {
            val result = container.progressSync.push(
                server = srv,
                uuid = uuid,
                percent = (percent * 100.0).toFloat(),
                locationValue = json,
                source = locator.href.toString(),
                status = status
            )
            if (result.isFailure) {
                val note = "进度同步失败：${result.exceptionOrNull()?.message.orEmpty()}"
                runOnUiThread { ui = ui.copy(syncNote = note) }
            }
        }
    }

    private fun manualSync() {
        val locator = currentLocator
        if (locator == null) {
            ui = ui.copy(message = "还没开始阅读，暂无进度可同步")
            return
        }
        val percent = percentOf(locator)
        saveLocal(percent, locator)
        if (server?.let { container.progressSync.isSupported(it) } != true) {
            ui = ui.copy(message = "当前服务器不支持进度同步（需要 Calibre-Web + Kobo 令牌）")
            return
        }
        pushRemote(percent, locator)
        ui = ui.copy(message = "已提交进度 ${(percent * 100).toInt()}%")
    }

    private fun syncDescription(): String? {
        val srv = server ?: return null
        val b = book ?: return null
        return when {
            !container.progressSync.isSupported(srv) -> "进度仅保存在本机"
            b.koboUuid == null -> "服务端未提供 uuid，进度仅保存在本机"
            else -> "进度同步至 ${srv.displayName}"
        }
    }

    override fun onStop() {
        currentLocator?.let { locator ->
            val percent = percentOf(locator)
            saveLocal(percent, locator)
            pushRemote(percent, locator)
        }
        super.onStop()
    }

    override fun onDestroy() {
        progressJob?.cancel()
        super.onDestroy()
    }
}
