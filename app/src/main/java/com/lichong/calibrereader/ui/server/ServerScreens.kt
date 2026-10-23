package com.lichong.calibrereader.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lichong.calibrereader.AppContainer
import com.lichong.calibrereader.data.model.ServerConfig
import com.lichong.calibrereader.data.net.ProbeResult
import com.lichong.calibrereader.data.net.ServerProbe
import com.lichong.calibrereader.ui.MessageState
import com.lichong.calibrereader.ui.update.AboutCard
import com.lichong.calibrereader.ui.update.UpdateUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    container: AppContainer,
    servers: List<ServerConfig>,
    activeId: String?,
    showBack: Boolean,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onRetryInstall: () -> Unit,
    onBack: () -> Unit,
    onUse: (ServerConfig) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerConfig?>(null) }
    var pendingDelete by remember { mutableStateOf<ServerConfig?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibre 服务器") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加服务器") }
            )
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    MessageState(
                        title = "还没有添加 Calibre 服务器",
                        detail = "支持 calibre-server（官方内容服务器）与 Calibre-Web。\n填写地址后会自动探测 OPDS 入口，无需手动输入 /opds。",
                        actionText = "添加服务器",
                        onAction = { creating = true }
                    )
                }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    AboutCard(
                        state = updateState,
                        onCheck = onCheckUpdate,
                        onDownload = onDownloadUpdate,
                        onRetryInstall = onRetryInstall
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(servers, key = { it.id }) { s ->
                    ServerCard(
                        server = s,
                        isActive = s.id == activeId,
                        onClick = { onUse(s) },
                        onEdit = { editing = s },
                        onDelete = { pendingDelete = s }
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    AboutCard(
                        state = updateState,
                        onCheck = onCheckUpdate,
                        onDownload = onDownloadUpdate,
                        onRetryInstall = onRetryInstall
                    )
                }
            }
        }
    }

    if (creating) {
        ServerEditDialog(
            container = container,
            initial = null,
            onDismiss = { creating = false },
            onSaved = { cfg ->
                creating = false
                onUse(cfg)
            }
        )
    }

    editing?.let { s ->
        ServerEditDialog(
            container = container,
            initial = s,
            onDismiss = { editing = null },
            onSaved = { editing = null }
        )
    }

    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除服务器") },
            text = { Text("将从列表中移除「${s.displayName}」，已下载的书籍不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.serverStore.delete(s.id) }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ServerCard(
    server: ServerConfig,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        server.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "当前使用",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(server.kind.label, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (server.hasAuth) {
                        AssistChip(
                            onClick = onClick,
                            label = {
                                Text(
                                    server.username.ifBlank { "已登录" },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerEditDialog(
    container: AppContainer,
    initial: ServerConfig?,
    onDismiss: () -> Unit,
    onSaved: (ServerConfig) -> Unit
) {
    var baseUrl by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var trustAll by remember { mutableStateOf(initial?.trustAllCerts ?: false) }
    var koboToken by remember { mutableStateOf(initial?.koboToken.orEmpty()) }

    var busy by remember { mutableStateOf(false) }
    var okMessage by remember { mutableStateOf<String?>(null) }
    var errMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun buildDraft(): ServerConfig = (initial ?: ServerProbe.newDraft()).copy(
        baseUrl = baseUrl.trim(),
        name = name.trim(),
        username = username.trim(),
        password = password,
        trustAllCerts = trustAll,
        koboToken = koboToken.trim()
    )

    fun runProbe(saveAfter: Boolean) {
        if (baseUrl.isBlank()) {
            errMessage = "请填写服务器地址，例如 http://192.168.1.10:8083"
            return
        }
        val draft = buildDraft()
        scope.launch {
            busy = true
            okMessage = null
            errMessage = null
            when (val result = container.serverProbe.probe(draft)) {
                is ProbeResult.Success -> {
                    okMessage = buildString {
                        append("连接成功 · ").append(result.config.kind.label)
                        append(" · ").append(result.feed.entries.size).append(" 个入口")
                        result.notes.forEach { append("\n· ").append(it) }
                    }
                    if (saveAfter) {
                        container.serverStore.upsert(result.config)
                        onSaved(result.config)
                    }
                }

                is ProbeResult.Failure -> {
                    errMessage = result.summary
                }
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (initial == null) "添加服务器" else "编辑服务器",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "填地址即可，OPDS 路径会自动探测",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; okMessage = null; errMessage = null },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.10:8083") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("显示名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（可选）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = trustAll, onCheckedChange = { trustAll = it })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("信任自签证书", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "HTTPS 证书不受信任时开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = koboToken,
                    onValueChange = { koboToken = it },
                    label = { Text("Kobo 同步令牌（可选）") },
                    placeholder = { Text("用于阅读进度双向同步") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (okMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        okMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (errMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        errMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { runProbe(false) }, enabled = !busy) { Text("测试连接") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { runProbe(true) }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}
