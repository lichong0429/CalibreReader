package com.lichong.calibrereader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lichong.calibrereader.AppContainer
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.ui.browse.BrowseScreen
import com.lichong.calibrereader.ui.browse.BrowseViewModel
import com.lichong.calibrereader.ui.library.LibraryScreen
import com.lichong.calibrereader.ui.library.LibraryViewModel
import com.lichong.calibrereader.ui.server.ServerListScreen
import com.lichong.calibrereader.ui.update.UpdatePromptDialog
import com.lichong.calibrereader.ui.update.UpdateViewModel
import kotlinx.coroutines.launch

private const val TAB_BROWSE = 0
private const val TAB_LIBRARY = 1
private const val TAB_SERVERS = 2

@Composable
fun AppRoot(container: AppContainer) {
    val servers by container.serverStore.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by container.serverStore.activeServer.collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableStateOf(TAB_BROWSE) }
    val scope = rememberCoroutineScope()

    // 首帧后把激活配置写进内存快照，供认证拦截器使用
    LaunchedEffect(servers, active?.id) {
        container.serverStore.updateCache(active)
    }

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

    if (updateState.promptVisible) {
        UpdatePromptDialog(
            state = updateState,
            onDismiss = updateVm::dismissPrompt,
            onUpdate = updateVm::download
        )
    }

    val effectiveTab = if (servers.isEmpty()) TAB_SERVERS else tab

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = effectiveTab == TAB_BROWSE,
                    onClick = { tab = TAB_BROWSE },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("书库") }
                )
                NavigationBarItem(
                    selected = effectiveTab == TAB_LIBRARY,
                    onClick = { tab = TAB_LIBRARY },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("书架") }
                )
                NavigationBarItem(
                    selected = effectiveTab == TAB_SERVERS,
                    onClick = { tab = TAB_SERVERS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("服务器") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (effectiveTab) {
                TAB_BROWSE -> {
                    val server = active
                    if (server != null) {
                        BrowseRoute(
                            container = container,
                            server = server,
                            onOpenServers = { tab = TAB_SERVERS }
                        )
                    } else {
                        MessageState(
                            title = "还没有连接服务器",
                            detail = "先在「服务器」里添加你的 Calibre 地址",
                            actionText = "去设置",
                            onAction = { tab = TAB_SERVERS }
                        )
                    }
                }

                TAB_LIBRARY -> LibraryScreen(
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
                    onUse = { cfg: ServerConfig ->
                        scope.launch { container.serverStore.setActive(cfg.id) }
                        tab = TAB_BROWSE
                    }
                )
            }
        }
    }
}

@Composable
private fun BrowseRoute(
    container: AppContainer,
    server: ServerConfig,
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
        onBack = { vm.back() },
        onOpen = vm::openEntry,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onSearch = vm::search,
        onSearchActiveChange = vm::setSearchActive,
        onQueryChange = vm::setQuery,
        onSelect = vm::select,
        onDownload = vm::download,
        onOpenFile = vm::openLocalFile,
        onClearDownload = vm::clearDownload,
        onClearMessage = vm::clearMessage,
        onOpenServers = onOpenServers
    )
}
