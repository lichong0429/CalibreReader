package com.lichong.calibrereader.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lichong.calibrereader.ui.LoadingBox
import com.lichong.calibrereader.ui.MessageState
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    state: PdfReaderUiState,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onSyncNow: () -> Unit,
    onClearMessage: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            onClearMessage()
        }
    }

    // 缩放/平移属于纯 UI 状态，翻页时自动复位
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(state.page) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (state.chromeVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.title.ifBlank { "PDF 阅读" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "内置阅读 · ${state.page + 1} / ${state.pageCount} 页",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            scale = when {
                                scale < 1.4f -> 1.5f
                                scale < 1.9f -> 2f
                                scale < 2.9f -> 3f
                                else -> 1f
                            }
                            if (scale == 1f) offset = Offset.Zero
                        }) { Text("${(scale * 100).roundToInt()}%") }
                        TextButton(onClick = onSyncNow) { Text("同步") }
                    }
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    // 放大后的白色页面不能画到顶栏/底栏上去，否则工具栏会被盖住点不到
                    .clipToBounds()
                    .pointerInput(state.page, state.pageCount) {
                        detectTapGestures { pos ->
                            val w = size.width.toFloat()
                            when {
                                pos.x < w * 0.28f -> onPrev()
                                pos.x > w * 0.72f -> onNext()
                                else -> onToggleChrome()
                            }
                        }
                    }
                    .pointerInput(scale) {
                        if (scale > 1.01f) {
                            detectDragGestures { _, drag -> offset += drag }
                        } else {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (abs(total) > 90f) {
                                        if (total < 0f) onNext() else onPrev()
                                    }
                                    total = 0f
                                },
                                onDragCancel = { total = 0f },
                                onHorizontalDrag = { _, delta -> total += delta }
                            )
                        }
                    }
            ) {
                when {
                    state.loading -> LoadingBox()
                    state.error != null -> MessageState(
                        title = "打不开这本书",
                        detail = state.error,
                        actionText = "返回",
                        onAction = onBack,
                        showIcon = true
                    )

                    state.bitmap != null -> {
                        val imageBitmap = remember(state.bitmap) {
                            state.bitmap!!.asImageBitmap()
                        }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "第 ${state.page + 1} 页",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    }

                    else -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            if (state.chromeVisible && state.error == null && !state.loading) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                ) {
                    var seek by remember(state.page) { mutableStateOf(state.page.toFloat()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrev, enabled = state.page > 0) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一页")
                        }
                        Slider(
                            value = seek,
                            onValueChange = { seek = it },
                            onValueChangeFinished = { onSeek(seek.roundToInt()) },
                            valueRange = 0f..(state.pageCount - 1).coerceAtLeast(0).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onNext,
                            enabled = state.page < state.pageCount - 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一页")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            state.syncNote ?: "进度自动保存在本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${state.page + 1} / ${state.pageCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
