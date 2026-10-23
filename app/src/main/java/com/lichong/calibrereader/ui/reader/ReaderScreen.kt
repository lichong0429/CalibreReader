package com.lichong.calibrereader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                    onSyncNow = onSyncNow
                )
            }
        }
    }

    if (showToc) {
        ModalBottomSheet(onDismissRequest = { showToc = false }, sheetState = sheetState) {
            SectionTitle("目录（${state.toc.size} 项）")
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                itemsIndexed(state.toc) { _, item ->
                    Surface(
                        onClick = {
                            showToc = false
                            onGoTo(item)
                        },
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 20.dp + (item.level * 16).dp,
                                    end = 20.dp,
                                    top = 12.dp,
                                    bottom = 12.dp
                                ),
                            style = if (item.level == 0) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            fontWeight = if (item.level == 0) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onClearMessage,
            confirmButton = { TextButton(onClick = onClearMessage) { Text("好") } },
            text = { Text(state.message) }
        )
    }
}

@Composable
private fun ReaderBottomBar(
    progress: Double,
    note: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSyncNow: () -> Unit
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
                TextButton(onClick = onSyncNow, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                    Text(note, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        LinearProgressIndicator(
            progress = { progress.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一页")
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
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

        Spacer(Modifier.height(14.dp))
        Text("字号 ${prefs.fontSize.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = prefs.fontSize.toFloat(),
            onValueChange = { onChange(prefs.copy(fontSize = it.toDouble())) },
            valueRange = 70f..220f,
            steps = 14
        )

        Text("行距 ${"%.1f".format(prefs.lineHeight)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = prefs.lineHeight.toFloat(),
            onValueChange = { onChange(prefs.copy(lineHeight = it.toDouble())) },
            valueRange = 1.0f..2.2f,
            steps = 5
        )

        Spacer(Modifier.height(8.dp))
        Text("主题", style = MaterialTheme.typography.bodyMedium)
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

        Spacer(Modifier.height(14.dp))
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

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("滚动阅读", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "开启后按连续滚动而非左右翻页",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = prefs.scroll, onCheckedChange = { onChange(prefs.copy(scroll = it)) })
        }
    }
}
