package com.lichong.calibrereader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lichong.calibrereader.AppContainer
import com.lichong.calibrereader.BuildConfig
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.ui.browse.BrowseScreen
import com.lichong.calibrereader.ui.browse.BrowseViewModel
import com.lichong.calibrereader.ui.library.LibraryScreen
import com.lichong.calibrereader.ui.library.LibraryViewModel
import com.lichong.calibrereader.ui.server.ServerEditDialog
import com.lichong.calibrereader.ui.server.ServerListScreen
import com.lichong.calibrereader.ui.theme.AppButtonShape
import com.lichong.calibrereader.ui.theme.AppCardShape
import com.lichong.calibrereader.ui.update.UpdatePromptDialog
import com.lichong.calibrereader.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAB_BROWSE = 0
private const val TAB_LIBRARY = 1
private const val TAB_SETTINGS = 2

/** DataStore 首帧为空，用 null 区分「还没读到配置」与「确实没有服务器」。 */
private data class ServerSnapshot(
    val servers: List<ServerConfig>,
    val active: ServerConfig?
)

@Composable
fun AppRoot(container: AppContainer) {
    // 一次收集两份配置，避免首帧闪过设置页。
    // 超时/异常都回退成「无服务器」，绝不让界面卡在无限加载圈里。
    val snapshot by produceState<ServerSnapshot?>(initialValue = null) {
        val flow = combine(
            container.serverStore.servers,
            container.serverStore.activeServer
        ) { list, active -> ServerSnapshot(list, active) }

        val first = runCatching {
            withTimeoutOrNull(3_000L) { flow.first() }
        }.getOrNull() ?: ServerSnapshot(emptyList(), null)
        value = first

        runCatching { flow.collect { value = it } }
    }
    val servers = snapshot?.servers.orEmpty()
    val active = snapshot?.active

    // 冷启动永远是书库。这里刻意不用 rememberSaveable：
    // 旧版本会把「停留在设置页」的 tab 存进 savedInstanceState，升级后被恢复回来，
    // 表现就是「打开 App 还是设置页、怎么点都回不去」。
    var tab by remember { mutableStateOf(TAB_BROWSE) }
    var showAddServer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active?.id) { container.serverStore.updateCache(active) }

    val libraryVm: LibraryViewModel = viewModel(
        factory = viewModelFactory { initializer { LibraryViewModel(container) } }
    )
    val libraryItems by libraryVm.items.collectAsStateWithLifecycle()
    val libraryMessage by libraryVm.message.collectAsStateWithLifecycle()

    val updateVm: UpdateViewModel = viewModel(
        factory = viewModelFactory { initializer { UpdateViewModel(container) } }
    )
    val updateState by updateVm.state.collectAsStateWithLifecycle()

    // 启动静默检查（12h 节流，只有发现新版才弹窗）
    LaunchedEffect(Unit) { updateVm.checkSilent() }
    // 从「安装未知应用」授权页返回后继续安装
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updateVm.retryInstall() }

    if (updateState.promptVisible && updateState.info != null) {
        UpdatePromptDialog(
            state = updateState,
            onDismiss = updateVm::dismissPrompt,
            onUpdate = updateVm::download
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = tab == TAB_BROWSE,
                        onClick = { tab = TAB_BROWSE },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("书库") }
                    )
                    NavigationBarItem(
                        selected = tab == TAB_LIBRARY,
                        onClick = { tab = TAB_LIBRARY },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("书架") }
                    )
                    NavigationBarItem(
                        selected = tab == TAB_SETTINGS,
                        onClick = { tab = TAB_SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                snapshot == null -> LoadingBox("正在准备书库…")

                tab == TAB_BROWSE -> {
                    if (active != null) {
                        BrowseRoute(
                            container = container,
                            server = active,
                            servers = servers,
                            onSwitchServer = { cfg ->
                                scope.launch { container.serverStore.setActive(cfg.id) }
                            },
                            onOpenServers = { tab = TAB_SETTINGS }
                        )
                    } else {
                        ConnectLibraryScreen(
                            hasSavedServers = servers.isNotEmpty(),
                            onAdd = { showAddServer = true },
                            onOpenSettings = { tab = TAB_SETTINGS }
                        )
                    }
                }

                tab == TAB_LIBRARY -> LibraryScreen(
                    items = libraryItems,
                    message = libraryMessage,
                    onOpen = libraryVm::open,
                    onDelete = libraryVm::delete,
                    onClearMessage = libraryVm::clearMessage,
                    onBrowse = { tab = TAB_BROWSE }
                )

                else -> ServerListScreen(
                    container = container,
                    servers = servers,
                    activeId = active?.id,
                    showBack = false,
                    updateState = updateState,
                    onCheckUpdate = updateVm::checkManual,
                    onDownloadUpdate = updateVm::download,
                    onRetryInstall = updateVm::retryInstall,
                    onBack = {},
                    onOpenLibrary = { tab = TAB_BROWSE },
                    onUse = { cfg: ServerConfig ->
                        scope.launch { container.serverStore.setActive(cfg.id) }
                        tab = TAB_BROWSE
                    }
                )
            }
        }
    }

    // 在书库页里直接弹添加对话框，不用先跳去设置页
    if (showAddServer) {
        ServerEditDialog(
            container = container,
            initial = null,
            onDismiss = { showAddServer = false },
            onSaved = { cfg ->
                showAddServer = false
                scope.launch { container.serverStore.setActive(cfg.id) }
                tab = TAB_BROWSE
            }
        )
    }
}

/** 还没连服务器时的书库首页：说明用途 + 一步连接，而不是整屏设置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectLibraryScreen(
    hasSavedServers: Boolean,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书库") },
                actions = {
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BrandIllustration(Modifier.size(88.dp))
            Spacer(Modifier.height(22.dp))
            Text(
                "把书库装进口袋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "连上自建 Calibre 后，打开就是书库：翻目录、搜书、看封面、下载即读，\n不用再在手机浏览器里缩放网页。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = AppCardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("只需填一次地址", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "例如 http://192.168.1.10:8083\nOPDS 路径自动探测，用户名密码可选。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = onAdd, label = { Text("calibre-server") })
                        AssistChip(onClick = onAdd, label = { Text("Calibre-Web") })
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdd,
                shape = AppButtonShape,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("连接 Calibre 服务器")
            }
            if (hasSavedServers) {
                TextButton(onClick = onOpenSettings) { Text("管理已保存的服务器") }
            } else {
                TextButton(onClick = onOpenSettings) { Text("服务器与更新设置") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "右上角是当前版本号。若不是 v${BuildConfig.VERSION_NAME}，说明新安装包还没有装成功。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 引导页品牌插画：渐变底 + 错落书脊，比一枚默认图标更有产品感。 */
@Composable
private fun BrandIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.secondary))),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(bottom = 22.dp)
        ) {
            Spacer(
                Modifier.size(11.dp, 30.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.45f))
            )
            Spacer(
                Modifier.size(13.dp, 44.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.90f))
            )
            Spacer(
                Modifier.size(11.dp, 36.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.62f))
            )
            Spacer(
                Modifier.size(9.dp, 26.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.38f))
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.32f))
        )
    }
}

@Composable
private fun BrowseRoute(
    container: AppContainer,
    server: ServerConfig,
    servers: List<ServerConfig>,
    onSwitchServer: (ServerConfig) -> Unit,
    onOpenServers: () -> Unit
) {
    val vm: BrowseViewModel = viewModel(
        key = "browse_${server.id}_${server.opdsRootUrl.hashCode()}",
        factory = viewModelFactory {
            initializer { BrowseViewModel(container, server) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    BrowseScreen(
        state = state,
        server = server,
        servers = servers,
        onSwitchServer = onSwitchServer,
        onBack = { vm.back() },
        onOpen = vm::openEntry,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onSearch = vm::search,
        onSearchActiveChange = vm::setSearchActive,
        onQueryChange = vm::setQuery,
        onSelect = vm::select,
        onDownload = vm::download,
        onQuickRead = vm::quickRead,
        onOpenFile = vm::openLocalFile,
        onClearDownload = vm::clearDownload,
        onClearMessage = vm::clearMessage,
        onOpenServers = onOpenServers
    )
}
