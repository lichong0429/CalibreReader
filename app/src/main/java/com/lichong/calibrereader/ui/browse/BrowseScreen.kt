package com.lichong.calibrereader.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lichong.calibrereader.BuildConfig
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.opds.FeedKind
import com.lichong.calibrereader.data.opds.OpdsEntry
import com.lichong.calibrereader.data.opds.OpdsLink
import com.lichong.calibrereader.ui.LoadingBox
import com.lichong.calibrereader.ui.MessageState
import com.lichong.calibrereader.ui.SectionTitle
import com.lichong.calibrereader.ui.components.RemoteCover
import com.lichong.calibrereader.ui.formatSize
import com.lichong.calibrereader.ui.theme.AppButtonShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    state: BrowseUiState,
    server: ServerConfig,
    servers: List<ServerConfig>,
    onSwitchServer: (ServerConfig) -> Unit,
    onBack: () -> Unit,
    onOpen: (OpdsEntry) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSearch: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (OpdsEntry?) -> Unit,
    onDownload: (OpdsEntry, OpdsLink) -> Unit,
    onQuickRead: (OpdsEntry) -> Unit,
    onOpenFile: (String) -> Unit,
    onClearDownload: () -> Unit,
    onClearMessage: () -> Unit,
    onOpenServers: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            onClearMessage()
        }
    }

    BackHandler(enabled = state.canGoBack) { onBack() }

    Scaffold(
        topBar = {
            BrowseTopBar(
                state = state,
                server = server,
                servers = servers,
                onSwitchServer = onSwitchServer,
                onBack = onBack,
                onRefresh = onRefresh,
                onOpenServers = onOpenServers,
                onSearchActiveChange = onSearchActiveChange,
                onQueryChange = onQueryChange,
                onSearch = onSearch
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading && state.entries.isEmpty() -> LoadingBox()

                state.error != null && state.entries.isEmpty() -> MessageState(
                    title = "连不上书库",
                    detail = state.error,
                    actionText = "重试",
                    onAction = onRefresh,
                    secondActionText = "检查服务器设置",
                    onSecondAction = onOpenServers,
                    showIcon = true
                )

                state.entries.isEmpty() -> MessageState(
                    title = "这个目录是空的",
                    detail = "换一个分类，或检查服务器上的书目过滤设置"
                )

                state.feed?.kind == FeedKind.NAVIGATION -> NavList(state, onOpen, onLoadMore)

                else -> BookGrid(state, onOpen, onLoadMore)
            }

            if (state.loading && state.entries.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }
    }

    state.selected?.let { entry ->
        BookDetailSheet(
            entry = entry,
            download = state.download,
            onDismiss = { onSelect(null) },
            onDownload = { link -> onDownload(entry, link) },
            onQuickRead = { onQuickRead(entry) },
            onOpenFile = onOpenFile,
            onResetDownload = onClearDownload
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseTopBar(
    state: BrowseUiState,
    server: ServerConfig,
    servers: List<ServerConfig>,
    onSwitchServer: (ServerConfig) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenServers: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    var serverMenu by remember { mutableStateOf(false) }

    if (state.searchActive) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { onSearchActiveChange(false) }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                }
            },
            title = {
                TextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("搜索书名或作者") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(state.searchQuery) }),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            actions = {
                IconButton(onClick = { onSearch(state.searchQuery) }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            }
        )
    } else {
        TopAppBar(
            navigationIcon = {
                if (state.canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回分类目录")
                    }
                } else {
                    IconButton(onClick = onOpenServers) {
                        Icon(Icons.Default.Settings, contentDescription = "服务器设置")
                    }
                }
            },
            title = {
                Column {
                    Text(
                        state.title.ifBlank { server.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(server.displayName)
                            if (!state.loading && state.entries.isNotEmpty()) {
                                append(" · ").append(state.entries.size).append(" 项")
                            }
                            append(" · v").append(BuildConfig.VERSION_NAME)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = {
                // 多台服务器时，书库顶栏直接切换，不用绕去设置页
                if (servers.size > 1) {
                    Box {
                        TextButton(onClick = { serverMenu = true }) {
                            Text("切换 ▾", style = MaterialTheme.typography.labelMedium)
                        }
                        DropdownMenu(
                            expanded = serverMenu,
                            onDismissRequest = { serverMenu = false }
                        ) {
                            servers.forEach { cfg ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            cfg.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        if (cfg.id == server.id) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "当前使用",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    },
                                    onClick = {
                                        serverMenu = false
                                        if (cfg.id != server.id) onSwitchServer(cfg)
                                    }
                                )
                            }
                        }
                    }
                }
                if (!state.searchTemplate.isNullOrBlank()) {
                    IconButton(onClick = { onSearchActiveChange(true) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )
    }
}

@Composable
private fun NavList(
    state: BrowseUiState,
    onOpen: (OpdsEntry) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (state.isRootCatalog && state.entries.size > 1) {
            item { QuickLinksRow(state.entries, onOpen) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) }
        }
        listItems(state.entries, key = { it.id }) { entry ->
            ListItem(
                headlineContent = {
                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = entry.summary?.takeIf { it.isNotBlank() }?.let { text ->
                    {
                        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                },
                leadingContent = entry.coverUrl?.let { url ->
                    {
                        RemoteCover(
                            url = url,
                            fallbackText = entry.title,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp, 50.dp)
                        )
                    }
                },
                trailingContent = {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                },
                modifier = Modifier.clickable { onOpen(entry) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        }
        if (state.nextUrl != null) {
            item {
                LoadMoreRow(state.loadingMore, state.nextUrl, onLoadMore)
            }
        }
    }
}

/** 根分类页顶部的一排快捷入口，少点一层目录 */
@Composable
private fun QuickLinksRow(
    entries: List<OpdsEntry>,
    onOpen: (OpdsEntry) -> Unit
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(
            "快捷入口",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entries.take(8).forEach { entry ->
                AssistChip(
                    onClick = { onOpen(entry) },
                    shape = RoundedCornerShape(12.dp),
                    label = {
                        Text(
                            entry.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun BookGrid(
    state: BrowseUiState,
    onOpen: (OpdsEntry) -> Unit,
    onLoadMore: () -> Unit
) {
    // 混合 feed 里既有子目录又有书，目录单独成行，别把目录画成书封面
    val navEntries = state.entries.filter { it.isNavigation }
    val bookEntries = state.entries.filterNot { it.isNavigation }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (navEntries.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    navEntries.forEach { entry ->
                        ListItem(
                            headlineContent = {
                                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onOpen(entry) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
        gridItems(bookEntries, key = { it.id }) { entry ->
            BookCard(entry) { onOpen(entry) }
        }
        if (state.nextUrl != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreRow(state.loadingMore, state.nextUrl, onLoadMore)
            }
        }
    }
}

@Composable
private fun LoadMoreRow(loading: Boolean, nextUrl: String?, onLoadMore: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            LaunchedEffect(nextUrl) { onLoadMore() }
            TextButton(onClick = onLoadMore) { Text("加载更多") }
        }
    }
}

@Composable
private fun BookCard(entry: OpdsEntry, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.68f)) {
                RemoteCover(
                    url = entry.coverUrl,
                    fallbackText = entry.title,
                    contentDescription = entry.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (entry.authors.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                entry.authorLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailSheet(
    entry: OpdsEntry,
    download: DownloadState,
    onDismiss: () -> Unit,
    onDownload: (OpdsLink) -> Unit,
    onQuickRead: () -> Unit,
    onOpenFile: (String) -> Unit,
    onResetDownload: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(Modifier.width(96.dp).aspectRatio(0.68f)) {
                        RemoteCover(
                            url = entry.largeCoverUrl,
                            fallbackText = entry.title,
                            contentDescription = entry.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entry.authors.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            entry.authorLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val meta = buildList {
                        entry.publisher?.let { add(it) }
                        entry.issued?.take(4)?.let { add(it) }
                        entry.language?.let { add(it) }
                    }
                    if (meta.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (entry.categories.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            entry.categories.take(4).joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val summary = entry.summary
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(18.dp))
                SectionTitle("简介")
                Text(summary, style = MaterialTheme.typography.bodyMedium)
            }

            if (entry.acquisitionLinks.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionTitle("阅读")
                Button(
                    onClick = onQuickRead,
                    enabled = download !is DownloadState.Running,
                    shape = AppButtonShape,
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("一键阅读") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "已下载的书直接打开；没下载的会自动取 EPUB 并拉起内置阅读器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("下载其它格式")

            if (entry.acquisitionLinks.isEmpty()) {
                Text("该书目没有可下载的格式", style = MaterialTheme.typography.bodySmall)
            }

            entry.acquisitionLinks.forEach { link ->
                val running = download is DownloadState.Running
                OutlinedButton(
                    onClick = { onResetDownload(); onDownload(link) },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Text(
                        buildString {
                            append("下载 ").append(link.format.label)
                            link.length?.takeIf { it > 0 }?.let { append(" · ").append(formatSize(it)) }
                        }
                    )
                }
            }

            when (download) {
                is DownloadState.Running -> {
                    Spacer(Modifier.height(14.dp))
                    if (download.indeterminate) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { download.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append("正在下载 ${download.label}")
                            if (!download.indeterminate) {
                                append(" · ").append((download.fraction * 100).toInt()).append("%")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadState.Done -> {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "已保存：${download.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onOpenFile(download.path) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("打开文件") }
                }

                is DownloadState.Failed -> {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "下载失败：${download.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                DownloadState.Idle -> Unit
            }
        }
    }
}
