package com.lichong.calibrereader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import com.lichong.calibrereader.R
import com.lichong.calibrereader.ui.LoadingBox
import com.lichong.calibrereader.ui.MessageState
import com.lichong.calibrereader.ui.SectionTitle
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onGoTo: (TocItem?) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onPrefsChange: (ReaderPrefs) -> Unit,
    onSyncNow: () -> Unit,
    onClearMessage: () -> Unit,
    onNavigatorHostReady: () -> Unit = {}
) {
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 全屏（隐藏工具栏）时不再给系统栏留白，否则上下仍有黑边，看起来像没全屏
    val insetModifier = if (state.chromeVisible) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    } else {
        Modifier
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().then(insetModifier)) {
            if (state.chromeVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.title.ifBlank { "阅读" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.authors.isNotBlank()) {
                                Text(
                                    state.authors,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showToc = state.toc.isNotEmpty() }) {
                            Icon(Icons.Default.List, contentDescription = "目录")
                        }
                        TextButton(onClick = { showSettings = true }) {
                            Text("Aa", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> LoadingBox("正在解析 EPUB…")
                    state.error != null -> MessageState(
                        title = "打不开这本书",
                        detail = state.error,
                        actionText = "返回",
                        onAction = onBack
                    )

                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            FragmentContainerView(ctx).apply {
                                id = R.id.reader_fragment_container
                            }.also { view -> view.post { onNavigatorHostReady() } }
                        }
                    )
                }
            }

            if (state.chromeVisible && !state.loading && state.error == null) {
                ReaderBottomBar(
                    progress = state.progress,
                    note = state.syncNote,
                    onPrev = onPrev,
                    onNext = onNext,
                    onSyncNow = onSyncNow,
                    onToggleChrome = onToggleChrome
                )
            }
        }

        // 全屏时上下各留一条窄热区呼出工具栏：中间留给翻页/滚动，
        // Readium 的 WebView 会吃掉正文区域的点击，写在正文里的点击监听收不到。
        if (!state.chromeVisible) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.TopCenter)
                    .clickable { onToggleChrome() }
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .clickable { onToggleChrome() }
            )
        }
    }

    if (showToc) {
        ModalBottomSheet(onDismissRequest = { showToc = false }, sheetState = sheetState) {
            TocSheet(
                toc = state.toc,
                onGoTo = { item ->
                    showToc = false
                    onGoTo(item)
                }
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = sheetState) {
            ReaderSettings(
                prefs = state.prefs,
                onChange = onPrefsChange,
                onClose = { showSettings = false }
            )
        }
    }

    if (state.message != null) {
        AlertDialog(
            onDismissRequest = onClearMessage,
            confirmButton = { TextButton(onClick = onClearMessage) { Text("好") } },
            text = { Text(state.message) }
        )
    }
}

/** 目录面板：按层级缩进，父节点可折叠 */
@Composable
private fun TocSheet(toc: List<TocItem>, onGoTo: (TocItem) -> Unit) {
    var collapsed by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 折叠后要显示的索引：被收起节点的所有后代都跳过
    val visible = remember(toc, collapsed) {
        buildList {
            var i = 0
            while (i < toc.size) {
                add(i)
                if (i in collapsed) {
                    val level = toc[i].level
                    var j = i + 1
                    while (j < toc.size && toc[j].level > level) j++
                    i = j
                } else {
                    i++
                }
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "目录（${toc.size} 项）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { collapsed = emptySet() }) { Text("全展开") }
        TextButton(onClick = {
            collapsed = toc.indices.filter { toc[it].hasChildren }.toSet()
        }) { Text("全收起") }
    }

    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        items(visible, key = { it }) { index ->
            val item = toc[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onGoTo(item) }
                    .padding(start = (8 + item.level * 14).dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.hasChildren) {
                    IconButton(
                        onClick = {
                            collapsed = if (index in collapsed) collapsed - index
                            else collapsed + index
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (index in collapsed) Icons.Default.KeyboardArrowRight
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (index in collapsed) "展开" else "收起",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.width(30.dp))
                }
                Text(
                    item.title,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    style = if (item.level == 0) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (item.level == 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ReaderBottomBar(
    progress: Double,
    note: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleChrome: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!note.isNullOrBlank()) {
                TextButton(
                    onClick = onSyncNow,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                ) {
                    Text(note, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一页")
            }
            TextButton(onClick = onToggleChrome) {
                Text("全屏阅读", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一页")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettings(
    prefs: ReaderPrefs,
    onChange: (ReaderPrefs) -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(max = 580.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }
        HorizontalDivider()

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // ---------- 字号与间距 ----------
            SettingSlider(
                label = "字号",
                valueText = "${prefs.fontSize.roundToInt()}%",
                value = prefs.fontSize.toFloat(),
                range = 70f..220f
            ) { onChange(prefs.copy(fontSize = it.toDouble())) }

            SettingSlider(
                label = "行距",
                valueText = "%.2f".format(prefs.lineHeight),
                value = prefs.lineHeight.toFloat(),
                range = 1.0f..2.4f
            ) { onChange(prefs.copy(lineHeight = it.toDouble())) }

            SettingSlider(
                label = "页边距",
                valueText = "%.1f".format(prefs.pageMargins),
                value = prefs.pageMargins.toFloat(),
                range = 0f..2f
            ) { onChange(prefs.copy(pageMargins = it.toDouble())) }

            SettingSlider(
                label = "段间距",
                valueText = "%.1f".format(prefs.paragraphSpacing),
                value = prefs.paragraphSpacing.toFloat(),
                range = 0f..2f
            ) { onChange(prefs.copy(paragraphSpacing = it.toDouble())) }

            SettingSlider(
                label = "首行缩进",
                valueText = "%.1f".format(prefs.paragraphIndent),
                value = prefs.paragraphIndent.toFloat(),
                range = 0f..4f
            ) { onChange(prefs.copy(paragraphIndent = it.toDouble())) }

            SettingSlider(
                label = "字母间距",
                valueText = "%.2f".format(prefs.letterSpacing),
                value = prefs.letterSpacing.toFloat(),
                range = 0f..0.2f
            ) { onChange(prefs.copy(letterSpacing = it.toDouble())) }

            // ---------- 字重 ----------
            Spacer(Modifier.height(12.dp))
            Text("字重", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    300.0 to "细",
                    400.0 to "常规",
                    700.0 to "粗"
                ).forEach { (weight, label) ->
                    FilterChip(
                        selected = prefs.fontWeight == weight,
                        onClick = { onChange(prefs.copy(fontWeight = weight)) },
                        label = { Text(label) }
                    )
                }
            }

            // ---------- 对齐 ----------
            Spacer(Modifier.height(10.dp))
            Text("对齐", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to "默认",
                    TextAlign.LEFT to "左对齐",
                    TextAlign.JUSTIFY to "两端对齐"
                ).forEach { (align, label) ->
                    FilterChip(
                        selected = prefs.textAlign == align,
                        onClick = { onChange(prefs.copy(textAlign = align)) },
                        label = { Text(label) }
                    )
                }
            }

            // ---------- 字体 ----------
            Spacer(Modifier.height(10.dp))
            Text("字体", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to "出版社",
                    FontFamily.SERIF to "衬线",
                    FontFamily.SANS_SERIF to "无衬线"
                ).forEach { (family, label) ->
                    FilterChip(
                        selected = prefs.fontFamily?.name == family?.name,
                        onClick = { onChange(prefs.copy(fontFamily = family)) },
                        label = { Text(label) }
                    )
                }
            }

            // ---------- 主题 ----------
            Spacer(Modifier.height(10.dp))
            Text("背景", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Theme.LIGHT to "白底",
                    Theme.SEPIA to "护眼",
                    Theme.DARK to "夜间"
                ).forEach { (theme, label) ->
                    FilterChip(
                        selected = prefs.theme == theme,
                        onClick = { onChange(prefs.copy(theme = theme)) },
                        label = { Text(label) }
                    )
                }
            }

            // ---------- 开关 ----------
            Spacer(Modifier.height(6.dp))
            SettingSwitch(
                title = "滚动阅读",
                subtitle = "连续滚动，而不是左右翻页",
                checked = prefs.scroll
            ) { onChange(prefs.copy(scroll = it)) }

            SettingSwitch(
                title = "自动断词",
                subtitle = "英文长单词按音节换行，右侧更齐",
                checked = prefs.hyphens
            ) { onChange(prefs.copy(hyphens = it)) }

            SettingSwitch(
                title = "使用出版社排版",
                subtitle = "开启后出版社自带样式会覆盖上面的字号与行距",
                checked = prefs.publisherStyles
            ) { onChange(prefs.copy(publisherStyles = it)) }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
