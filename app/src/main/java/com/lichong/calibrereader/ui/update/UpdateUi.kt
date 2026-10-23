package com.lichong.calibrereader.ui.update

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lichong.calibrereader.ui.formatSize

/** 服务器页底部的「关于」区块：版本号 + 检查更新 + 更新卡片 */
@Composable
fun AboutCard(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onRetryInstall: () -> Unit
) {
    var notesExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Text("关于", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "v${state.currentVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "CalibreReader · OPDS 浏览与内置 EPUB 阅读器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            when (state.stage) {
                UpdateUiState.Stage.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("正在检查更新…", style = MaterialTheme.typography.bodySmall)
                }

                UpdateUiState.Stage.Idle, UpdateUiState.Stage.UpToDate -> Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (state.stage == UpdateUiState.Stage.UpToDate) "已是最新版本" else "检查是否有新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCheck) { Text("检查更新") }
                }

                UpdateUiState.Stage.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.error ?: "检查失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCheck) { Text("重试") }
                }

                UpdateUiState.Stage.Available -> {
                    val info = state.info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { notesExpanded = !notesExpanded },
                            label = { Text("新版本 v${info?.version.orEmpty()}") }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatSize(info?.sizeBytes ?: 0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    info?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (notesExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (notes.lines().size > 3) {
                            TextButton(
                                onClick = { notesExpanded = !notesExpanded },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) { Text(if (notesExpanded) "收起" else "展开全部") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDownload) { Text("下载并安装") }
                    }
                }

                UpdateUiState.Stage.Downloading -> {
                    Text(
                        "正在下载 ${formatSize(state.downloaded)} / ${formatSize(state.total)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                UpdateUiState.Stage.Downloaded -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已下载，等待安装",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetryInstall) { Text("继续安装") }
                    }
                    if (state.permissionNeeded) {
                        Text(
                            "需要在系统设置里允许本应用安装 APK，授权后回来点「继续安装」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/** 启动静默检查发现新版本时弹的框 */
@Composable
fun UpdatePromptDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val info = state.info ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "当前 v${state.currentVersion} → 新版本 v${info.version}" +
                        if (info.sizeBytes > 0) "（${formatSize(info.sizeBytes)}）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}
