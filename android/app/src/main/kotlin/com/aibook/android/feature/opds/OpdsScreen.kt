package com.aibook.android.feature.opds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsRequestFactory
import com.aibook.android.core.network.opds.OpdsSyncState
import com.aibook.android.feature.importer.LocalBookImportViewModel
import com.aibook.android.feature.importer.rememberLocalBookImportLauncher
import com.aibook.android.feature.importer.supportedBookMimeTypes
import com.aibook.android.ui.design.DesignPage
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SectionHeader
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SlidingSegmentedControl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    onAddSourceClick: () -> Unit = {},
    onScanDirectoriesClick: () -> Unit = {},
    onImportBooksClick: () -> Unit = {},
    servicesOnly: Boolean = false,
    initialConnectionId: String? = null,
    initialHref: String? = null,
    pageTitle: String? = null,
    onConnectionClick: ((String) -> Unit)? = null,
    onCategoriesClick: ((String) -> Unit)? = null,
    onCategoryClick: ((String, String) -> Unit)? = null,
    categoriesOnly: Boolean = false,
    booksOnly: Boolean = false,
    viewModel: OpdsViewModel = viewModel(factory = OpdsViewModel.Factory),
    importViewModel: LocalBookImportViewModel = viewModel(factory = LocalBookImportViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLocalBookImportLauncher { uris ->
        importViewModel.importBooks(uris)
    }
    var openedInitialHref by rememberSaveable(initialConnectionId, initialHref) { mutableStateOf(false) }

    LaunchedEffect(initialConnectionId, state.connections) {
        if (initialConnectionId != null && state.activeConnection?.id != initialConnectionId) {
            state.connections.firstOrNull { it.id == initialConnectionId }?.let(viewModel::selectConnection)
        }
    }
    LaunchedEffect(initialHref, state.activeConnection?.id, state.currentFeed, openedInitialHref) {
        if (!initialHref.isNullOrBlank() && state.activeConnection?.id == initialConnectionId && state.currentFeed != null && !openedInitialHref) {
            openedInitialHref = true
            viewModel.browseLink(initialHref)
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    DesignPage(
        title = pageTitle ?: if (state.currentFeed == null) (if (servicesOnly) "OPDS 服务" else "发现") else "OPDS 数据源",
        modifier = Modifier.fillMaxSize(),
        actions = {
            if (state.currentFeed != null) {
                TextButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    Text("上一级")
                }
            }
        }
    ) {
        if (state.showConnectionForm) {
            ConnectionForm(state, viewModel)
        } else if (state.currentFeed != null) {
            CatalogBrowser(state, viewModel, onCategoriesClick, onCategoryClick, categoriesOnly, booksOnly)
        } else {
            DiscoveryHome(
                state = state,
                importState = importState,
                showLocalActions = !servicesOnly,
                onImportClick = onImportBooksClick,
                onScanDirectoriesClick = onScanDirectoriesClick,
                onAddSourceClick = onAddSourceClick,
                onBrowseConnection = { connection ->
                    if (onConnectionClick != null) onConnectionClick(connection.id)
                    else viewModel.selectConnection(connection)
                },
                onEditConnection = viewModel::editConnection,
                onToggleConnection = viewModel::toggleConnectionEnabled,
                onSyncConnection = viewModel::syncConnection,
                onSetSyncInterval = viewModel::setOpdsIntervalHours,
                onShowError = viewModel::showErrorDetails,
                onDeleteConnection = viewModel::deleteConnection
            )
        }

        state.errorDialogConnection?.let { connection ->
            OpdsErrorDialog(
                connection = connection,
                onDismiss = viewModel::dismissErrorDetails,
                onRetry = {
                    viewModel.dismissErrorDetails()
                    viewModel.syncConnection(connection)
                }
            )
        }

        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun DiscoveryHome(
    state: OpdsUiState,
    importState: com.aibook.android.feature.importer.LocalBookImportState,
    showLocalActions: Boolean,
    onImportClick: () -> Unit,
    onScanDirectoriesClick: () -> Unit,
    onAddSourceClick: () -> Unit,
    onBrowseConnection: (OpdsConnection) -> Unit,
    onEditConnection: (OpdsConnection) -> Unit,
    onToggleConnection: (OpdsConnection, Boolean) -> Unit,
    onSyncConnection: (OpdsConnection) -> Unit,
    onSetSyncInterval: (Int) -> Unit,
    onShowError: (OpdsConnection) -> Unit,
    onDeleteConnection: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)) {
        if (showLocalActions) {
            item { SectionHeader("本地书籍", "EPUB · TXT · MOBI · PDF · AZW3") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12), modifier = Modifier.fillMaxWidth()) {
                    DiscoveryActionCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.CloudUpload, null, tint = DesignTokens.Accent, modifier = Modifier.size(32.dp)) },
                        title = "立即导入文件",
                        subtitle = if (importState.isImporting) "正在导入本地书籍" else "选择一个或多个书籍文件，导入后可加入书架",
                        accent = DesignTokens.Accent,
                        onClick = onImportClick
                    )
                    DiscoveryActionCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Default.Folder, null, tint = DesignTokens.Success, modifier = Modifier.size(32.dp)) },
                        title = "扫描本地目录",
                        subtitle = "管理扫描目录、子目录规则并自动发现书籍",
                        accent = DesignTokens.Success,
                        onClick = onScanDirectoriesClick
                    )
                }
            }
        }
//        item { SectionHeader("已配置扫描目录", "+ 添加目录") }
//        item {
//            SoftCard {
//                sampleScanDirectories.forEachIndexed { index, directory ->
//                    ScanDirectoryRow(directory)
//                    if (index != sampleScanDirectories.lastIndex) {
//                        Spacer(Modifier.height(14.dp))
//                        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Hairline))
//                        Spacer(Modifier.height(14.dp))
//                    }
//                }
//            }
//        }
//        item { SectionHeader("最近导入", "全部 ›") }
//        item {
//            SoftCard {
//                sampleRecentImports.forEachIndexed { index, imported ->
//                    RecentImportRow(imported)
//                    if (index != sampleRecentImports.lastIndex) {
//                        Spacer(Modifier.height(14.dp))
//                        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Hairline))
//                        Spacer(Modifier.height(14.dp))
//                    }
//                }
//            }
//        }
        item { SectionHeader("OPDS 数据源", "${state.connections.size} 个已配置") }
        item {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = DesignTokens.Accent)
                        Column {
                            Text("后台定时同步", fontWeight = FontWeight.Bold)
                            Text("遵循设置中的“仅 Wi-Fi 同步”，完成或失败时发送通知", color = DesignTokens.SoftText)
                        }
                    }
                    val intervals = listOf(0 to "关闭", 1 to "每小时", 6 to "6 小时", 24 to "每天")
                    SlidingSegmentedControl(
                        options = intervals.map { it.second },
                        selectedIndex = intervals.indexOfFirst { it.first == state.opdsIntervalHours }.coerceAtLeast(0),
                        onSelected = { index -> onSetSyncInterval(intervals[index].first) }
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onAddSourceClick) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加连接")
                }
            }
        }
        if (state.isLoading) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.connections.isEmpty()) {
            item {
                SoftCard {
                    Text("还没有 OPDS 连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("添加数据源后，可在书城聚合浏览远程目录。", color = DesignTokens.SoftText)
                }
            }
        } else {
            items(state.connections, key = { it.id }) { connection ->
                OpdsConnectionCard(
                    connection = connection,
                    onBrowse = { onBrowseConnection(connection) },
                    onEdit = { onEditConnection(connection) },
                    onToggle = { enabled -> onToggleConnection(connection, enabled) },
                    onSync = { onSyncConnection(connection) },
                    onShowError = { onShowError(connection) },
                    onDelete = { onDeleteConnection(connection.id) }
                )
            }
        }
    }
}

@Composable
private fun DiscoveryActionCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit = {}
) {
    SoftCard(
        modifier = modifier.clickable(onClick = onClick),
        color = accent.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(DesignTokens.RadiusMedium)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space4), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
                Text(subtitle, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = accent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ScanDirectoryRow(directory: ScanDirectorySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            null,
            tint = DesignTokens.Accent,
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFFFFE5C4), RoundedCornerShape(10.dp))
                .padding(8.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(directory.path, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("上次扫描：${directory.lastScan} · 发现 ${directory.count} 本", color = DesignTokens.SoftText)
        }
        Text(
            if (directory.enabled) "已开启" else "已关闭",
            color = if (directory.enabled) DesignTokens.Accent else DesignTokens.SoftText,
            fontWeight = FontWeight.Bold
        )
        Switch(
            checked = directory.enabled,
            onCheckedChange = {},
            colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
        )
        Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = DesignTokens.SoftText)
    }
}

@Composable
private fun RecentImportRow(imported: RecentImport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(96.dp)
                .background(imported.color, RoundedCornerShape(10.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(imported.shortTitle, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(imported.fileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FormatBadge(imported.format)
            }
            Text(imported.author, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${imported.status} · ${imported.importedAt}", color = DesignTokens.SoftText)
        }
        Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = DesignTokens.SoftText)
    }
}

@Composable
private fun FormatBadge(format: String) {
    Text(
        format,
        modifier = Modifier
            .background(Color(0xFFF0EEEB), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = DesignTokens.SoftText,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun OpdsConnectionCard(
    connection: OpdsConnection,
    onBrowse: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSync: () -> Unit,
    onShowError: () -> Unit,
    onDelete: () -> Unit
) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(connection.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OpdsStatusBadge(connection)
                    }
                    Text(connection.baseUrl, style = MaterialTheme.typography.bodySmall, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (connection.username == null) "未配置认证" else "已配置 Basic Auth",
                        style = MaterialTheme.typography.labelSmall,
                        color = DesignTokens.SoftText
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = connection.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
                    )
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                OpdsMetaText("书目", "${connection.bookCount} 本")
                OpdsMetaText("上次同步", formatSyncTime(connection.lastSyncedAt))
                OpdsMetaText("状态", opdsSyncLabel(connection.syncState))
            }
            if (connection.syncState == OpdsSyncState.FAILED && !connection.lastErrorMessage.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF0EE), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "错误：${connection.lastErrorMessage}",
                        color = Color(0xFFB44A35),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "查看完整错误",
                        modifier = Modifier.clickable(onClick = onShowError),
                        color = Color(0xFFB44A35),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSync,
                    enabled = connection.enabled && connection.syncState != OpdsSyncState.SYNCING,
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    modifier = Modifier.weight(1f)
                ) {
                    if (connection.syncState == OpdsSyncState.SYNCING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Text(if (connection.syncState == OpdsSyncState.SYNCING) "同步中" else "立即同步")
                }
                Button(
                    onClick = onBrowse,
                    enabled = connection.enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("浏览目录")
                }
            }
            if (connection.syncState == OpdsSyncState.FAILED && !connection.lastErrorMessage.isNullOrBlank()) {
                TextButton(onClick = onShowError, modifier = Modifier.fillMaxWidth()) {
                    Text("查看错误详情", color = Color(0xFFB44A35))
                }
            }
        }
    }
}

@Composable
private fun OpdsStatusBadge(connection: OpdsConnection) {
    val (label, color) = when {
        !connection.enabled -> "已停用" to DesignTokens.SoftText
        connection.syncState == OpdsSyncState.SYNCING -> "同步中" to DesignTokens.Accent
        connection.syncState == OpdsSyncState.FAILED -> "有错误" to Color(0xFFB44A35)
        connection.syncState == OpdsSyncState.SUCCESS -> "正常" to DesignTokens.Success
        else -> "待同步" to DesignTokens.SoftText
    }
    Text(
        label,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun OpdsErrorDialog(
    connection: OpdsConnection,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("OPDS 错误详情", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OpdsMetaText("数据源", connection.name)
                OpdsMetaText("状态", opdsSyncLabel(connection.syncState))
                OpdsMetaText("上次同步", formatSyncTime(connection.lastSyncedAt))
                Text("错误信息", color = DesignTokens.SoftText, style = MaterialTheme.typography.labelSmall)
                Text(
                    connection.lastErrorMessage.orEmpty().ifBlank { "暂无错误详情" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF0EE), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    color = Color(0xFFB44A35),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                enabled = connection.enabled && connection.syncState != OpdsSyncState.SYNCING,
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("重新同步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = DesignTokens.Accent)
            }
        }
    )
}

@Composable
private fun OpdsMetaText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = DesignTokens.SoftText, style = MaterialTheme.typography.labelSmall)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun opdsSyncLabel(state: OpdsSyncState): String {
    return when (state) {
        OpdsSyncState.IDLE -> "待同步"
        OpdsSyncState.SYNCING -> "同步中"
        OpdsSyncState.SUCCESS -> "成功"
        OpdsSyncState.FAILED -> "失败"
    }
}

private fun formatSyncTime(value: Long?): String {
    if (value == null) return "从未"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}

@Composable
private fun ConnectionForm(
    state: OpdsUiState,
    viewModel: OpdsViewModel
) {
    val isEditing = state.editingConnectionId != null
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SoftCard(color = Color.White) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isEditing) "编辑 OPDS 数据源" else "添加 OPDS 数据源",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (isEditing) "修改名称、地址或认证信息，不会清除启停状态和同步记录。"
                    else "保存后会立即连接并浏览根目录。",
                    color = DesignTokens.SoftText
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formName,
            onValueChange = { viewModel.updateFormField("name", it) },
            label = { Text("连接名称") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formBaseUrl,
            onValueChange = { viewModel.updateFormField("baseUrl", it) },
            label = { Text("OPDS 地址") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            placeholder = { Text("http://192.168.1.100:8080/opds/") }
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formUsername,
            onValueChange = { viewModel.updateFormField("username", it) },
            label = { Text("用户名（可选）") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.formPassword,
            onValueChange = { viewModel.updateFormField("password", it) },
            label = { Text("密码（可选）") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.saveConnection(browseAfterSave = !isEditing) }) {
                Text(if (isEditing) "保存修改" else "保存并连接")
            }
            TextButton(onClick = { viewModel.showConnectionForm(false) }) { Text("取消") }
        }
    }
}

@Composable
private fun ConnectionList(
    state: OpdsUiState,
    viewModel: OpdsViewModel,
    onAddSourceClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Button(onClick = onAddSourceClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("添加连接")
        }
    }

    if (state.isLoading) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.connections.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("还没有 OPDS 连接", style = MaterialTheme.typography.titleMedium)
                        Text("点击「添加连接」配置你的汗牛充栋服务器 OPDS 地址。")
                    }
                }
            }
        }

        items(state.connections, key = { it.id }) { connection ->
            SoftCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(connection.name, style = MaterialTheme.typography.titleLarge)
                        Text(connection.baseUrl, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (connection.username == null) "未配置认证" else "已配置 Basic Auth",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = { viewModel.deleteConnection(connection.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
                Button(onClick = { viewModel.selectConnection(connection) }, modifier = Modifier.fillMaxWidth()) {
                    Text("同步 / 浏览")
                }
            }
        }
    }
}

@Composable
fun OpdsAddSourceScreen(
    connectionId: String? = null,
    onBack: () -> Unit,
    viewModel: OpdsAddSourceViewModel = viewModel(
        factory = OpdsAddSourceViewModel.Factory,
        key = "opds-add-source-${connectionId ?: "new"}"
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing = connectionId != null
    val isConnectionTested = state.testedFingerprint == state.fingerprint()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (connectionId != null) {
            viewModel.loadConnection(connectionId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 128.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            if (isEditing) "编辑 OPDS 数据源" else "添加 OPDS 数据源",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "连接你的在线电子书目录",
                            color = DesignTokens.SoftText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                OpdsFormCard {
                    OpdsFormSectionHeader(
                        icon = Icons.Default.Book,
                        title = "基本信息",
                        subtitle = "填写数据源名称和 OPDS 目录地址"
                    )
                    OpdsInputField(
                        icon = Icons.Default.Book,
                        label = "数据源名称",
                        value = state.formName,
                        placeholder = "例如：我的电子书库",
                        required = true,
                        trailing = "${state.formName.length}/50",
                        onValueChange = { viewModel.updateFormField("name", it.take(50)) }
                    )
                    OpdsInputField(
                        icon = Icons.Default.Link,
                        label = "服务器 URL",
                        value = state.formBaseUrl,
                        placeholder = "https://example.com/opds",
                        helper = "以 http:// 或 https:// 开头",
                        required = true,
                        onValueChange = { viewModel.updateFormField("baseUrl", it.trim()) }
                    )
                }

                OpdsFormCard {
                    OpdsFormSectionHeader(
                        icon = Icons.Default.Lock,
                        title = "身份认证",
                        subtitle = "公开目录可直接留空"
                    )
                    OpdsInputField(
                        icon = Icons.Default.Person,
                        label = "用户名",
                        value = state.formUsername,
                        placeholder = "请输入用户名",
                        onValueChange = { viewModel.updateFormField("username", it) }
                    )
                    OpdsInputField(
                        icon = Icons.Default.Lock,
                        label = "密码",
                        value = state.formPassword,
                        placeholder = "请输入密码",
                        password = true,
                        onValueChange = { viewModel.updateFormField("password", it) }
                    )
                }

                OpdsFormCard {
                    OpdsFormSectionHeader(
                        icon = Icons.Default.Refresh,
                        title = "同步模式",
                        subtitle = "选择目录内容的更新方式"
                    )
                    SyncChoice(
                        "完全同步（推荐）",
                        "每次同步时更新所有书目数据",
                        selected = state.syncMode == com.aibook.android.core.network.opds.OpdsSyncMode.FULL
                    ) {
                        viewModel.setSyncMode(com.aibook.android.core.network.opds.OpdsSyncMode.FULL)
                    }
                    SyncChoice(
                        "增量同步",
                        "只写入新增或有变化的书目",
                        selected = state.syncMode == com.aibook.android.core.network.opds.OpdsSyncMode.INCREMENTAL
                    ) {
                        viewModel.setSyncMode(com.aibook.android.core.network.opds.OpdsSyncMode.INCREMENTAL)
                    }
                }

                OpdsFormCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (isConnectionTested) Icons.Default.CheckCircle else Icons.Default.Wifi,
                            contentDescription = null,
                            tint = if (isConnectionTested) DesignTokens.Success else DesignTokens.Accent
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isConnectionTested) "连接测试通过" else "测试连接",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (isConnectionTested) "当前连接信息可以正常访问"
                                else "保存前需要确认数据源可用",
                                color = DesignTokens.SoftText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::testConnection,
                            enabled = !state.isTesting && !state.isSaving,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(if (isConnectionTested) "重新测试" else "开始测试")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF4E8), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = DesignTokens.Accent)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("支持 OPDS 1.0 和 OPDS 2.0", fontWeight = FontWeight.Bold)
                        Text(
                            "部分私人服务器可能需要用户名和密码。",
                            color = DesignTokens.SoftText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DesignTokens.AppBackground)
                .border(width = 1.dp, color = DesignTokens.Hairline)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (isEditing) "保存修改" else "保存并启用",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("取消", color = DesignTokens.Accent)
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun OpdsFormCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun OpdsFormSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = DesignTokens.Accent,
            modifier = Modifier
                .size(36.dp)
                .background(DesignTokens.Accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .padding(8.dp)
        )
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OpdsInputField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    placeholder: String,
    helper: String? = null,
    trailing: String? = null,
    required: Boolean = false,
    password: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (required) "$label *" else "$label（可选）") },
        placeholder = { Text(placeholder) },
        supportingText = helper?.let {
            { Text(it) }
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = DesignTokens.Accent) },
        trailingIcon = {
            when {
                password -> IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
                trailing != null -> Text(
                    trailing,
                    modifier = Modifier.padding(end = 12.dp),
                    color = DesignTokens.SoftText,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        singleLine = true,
        visualTransformation = if (password && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun SyncChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) DesignTokens.Accent.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = DesignTokens.Accent)
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class ScanDirectorySummary(
    val path: String,
    val lastScan: String,
    val count: Int,
    val enabled: Boolean
)

private data class RecentImport(
    val fileName: String,
    val shortTitle: String,
    val author: String,
    val format: String,
    val status: String,
    val importedAt: String,
    val color: Color
)

private val sampleScanDirectories = emptyList<ScanDirectorySummary>()

private val sampleRecentImports = emptyList<RecentImport>()

@Composable
private fun CatalogBrowser(
    state: OpdsUiState,
    viewModel: OpdsViewModel,
    onCategoriesClick: ((String) -> Unit)?,
    onCategoryClick: ((String, String) -> Unit)?,
    categoriesOnly: Boolean,
    booksOnly: Boolean
) {
    val feed = state.currentFeed ?: return
    val connection = state.activeConnection
    val categoryEntries = feed.entries.filter { it.acquisitionLink == null && it.alternateLink != null }
    val bookEntries = feed.entries.filter { it.acquisitionLink != null }
    val availableFormats = remember(bookEntries) { bookEntries.map(::opdsFormatLabel).distinct().sorted() }
    val catalogKey = state.navigationStack.lastOrNull() ?: connection?.baseUrl.orEmpty()
    var selectedFormat by rememberSaveable(catalogKey) { mutableStateOf<String?>(null) }
    var sort by rememberSaveable(catalogKey) { mutableStateOf(OpdsCatalogSort.MODIFIED) }
    val displayedEntries = remember(bookEntries, selectedFormat, sort) {
        presentOpdsEntries(bookEntries, selectedFormat, sort)
    }
    val recentEntries = remember(bookEntries) {
        presentOpdsEntries(bookEntries, null, OpdsCatalogSort.MODIFIED).take(4)
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            CatalogHeader(
                connection = connection,
                feedTitle = feed.title,
                bookCount = bookEntries.size,
                onBack = { viewModel.navigateBack() }
            )
        }
        if (categoryEntries.isNotEmpty() && !booksOnly) {
            item {
                SectionHeader(
                    title = "分类",
                    trailing = "查看全部 ›",
                    onTrailingClick = { connection?.id?.let { onCategoriesClick?.invoke(it) } }
                )
            }
            item {
                CatalogCategoryChips(
                    entries = categoryEntries,
                    onBrowse = { href ->
                        val connectionId = connection?.id
                        if (connectionId != null && onCategoryClick != null) onCategoryClick(connectionId, href)
                        else viewModel.browseLink(href)
                    }
                )
            }
        }
        if (categoriesOnly && categoryEntries.isEmpty() && !state.isLoading) {
            item { CatalogEmptyState(hasCategories = false) }
        }
        if (state.isLoading) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = DesignTokens.Accent)
                }
            }
        }
        if (recentEntries.isNotEmpty() && !categoriesOnly && !booksOnly) {
            item {
                CatalogRecentSection(recentEntries, connection)
            }
        }
        if (!categoriesOnly) item {
            CatalogListHeader(
                count = displayedEntries.size,
                availableFormats = availableFormats,
                selectedFormat = selectedFormat,
                sort = sort,
                onCycleFormat = {
                    selectedFormat = when (val index = selectedFormat?.let(availableFormats::indexOf) ?: -1) {
                        -1 -> availableFormats.firstOrNull()
                        availableFormats.lastIndex -> null
                        else -> availableFormats[index + 1]
                    }
                },
                onToggleSort = {
                    sort = if (sort == OpdsCatalogSort.MODIFIED) OpdsCatalogSort.TITLE else OpdsCatalogSort.MODIFIED
                }
            )
        }
        if (!categoriesOnly && displayedEntries.isEmpty()) {
            item {
                CatalogEmptyState(categoryEntries.isNotEmpty())
            }
        } else if (!categoriesOnly) {
            items(displayedEntries, key = { "${it.title}-${it.acquisitionLink?.href.orEmpty()}" }) { entry ->
                CatalogBookRow(
                    entry = entry,
                    connection = connection,
                    downloading = state.downloadingTitle == entry.title,
                    onDownload = { viewModel.downloadEntry(entry) }
                )
            }
            if (state.isLoadingNextPage) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = DesignTokens.Accent)
                    }
                }
            }
        }
        val nextLink = feed.nextLink
        if (nextLink != null && !state.isLoadingNextPage) {
            item {
                LaunchedEffect(nextLink.href) { viewModel.loadNextPage() }
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    connection: OpdsConnection?,
    feedTitle: String,
    bookCount: Int,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFFF4E8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudUpload, null, tint = DesignTokens.Accent, modifier = Modifier.size(34.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            connection?.name ?: feedTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "OPDS",
                            modifier = Modifier
                                .background(Color(0xFFF0F6EA), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            color = Color(0xFF6B8F42),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        connection?.baseUrl ?: feedTitle,
                        color = DesignTokens.SoftText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("来自 OPDS 源的图书内容", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
            Text(
                "共 $bookCount 本图书 · 最后更新 ${formatSyncTime(connection?.lastSyncedAt)}",
                color = DesignTokens.SoftText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CatalogCategoryChips(
    entries: List<OpdsEntry>,
    onBrowse: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        item {
            CatalogChip("全部", selected = true, onClick = {})
        }
        items(entries.take(8), key = { it.title }) { entry ->
            CatalogChip(
                label = entry.title,
                selected = false,
                onClick = { entry.alternateLink?.href?.let(onBrowse) }
            )
        }
    }
}

@Composable
private fun CatalogChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(if (selected) Color(0xFFFFF2E8) else Color(0xFFF7F4F0), RoundedCornerShape(999.dp))
            .border(
                width = 1.dp,
                color = if (selected) DesignTokens.Accent else DesignTokens.Hairline,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        color = if (selected) DesignTokens.Accent else Color(0xFF2C2A28),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CatalogRecentSection(entries: List<OpdsEntry>, connection: OpdsConnection?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("最近更新")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(entries, key = { it.title }) { entry ->
                RecentCatalogBook(entry, connection)
            }
        }
    }
}

@Composable
private fun RecentCatalogBook(entry: OpdsEntry, connection: OpdsConnection?) {
    Column(
        modifier = Modifier.width(128.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            CatalogCover(entry, connection, width = 112.dp, height = 154.dp)
        }
        Text(entry.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatOpdsModifiedAt(entry.modifiedAt), color = DesignTokens.SoftText)
    }
}

@Composable
private fun CatalogListHeader(
    count: Int,
    availableFormats: List<String>,
    selectedFormat: String?,
    sort: OpdsCatalogSort,
    onCycleFormat: () -> Unit,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("全部图书", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("共 $count 本", color = DesignTokens.SoftText)
            TextButton(onClick = onToggleSort) {
                Text(if (sort == OpdsCatalogSort.MODIFIED) "最新更新" else "书名排序")
            }
            TextButton(onClick = onCycleFormat, enabled = availableFormats.isNotEmpty()) {
                Text(selectedFormat ?: "全部格式")
                Icon(Icons.Default.FilterList, null, tint = DesignTokens.SoftText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CatalogBookRow(
    entry: OpdsEntry,
    connection: OpdsConnection?,
    downloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.78f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatalogCover(entry, connection, width = 86.dp, height = 118.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.author ?: "未知作者", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatOpdsModifiedAt(entry.modifiedAt), color = DesignTokens.SoftText)
                    Text(opdsFormatLabel(entry), color = DesignTokens.SoftText)
                }
                entry.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = DesignTokens.SoftText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(
                onClick = onDownload,
                enabled = !downloading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF4E8), contentColor = DesignTokens.Accent),
                shape = RoundedCornerShape(999.dp)
            ) {
                if (downloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = DesignTokens.Accent)
                } else {
                    Text("加入书架", fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = DesignTokens.SoftText)
        }
    }
}

@Composable
private fun CatalogEmptyState(hasCategories: Boolean) {
    SoftCard(color = DesignTokens.WarmCard) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (hasCategories) "当前分类还没有可下载图书" else "目录为空",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text("可以切换上方分类，或返回上一级目录继续浏览。", color = DesignTokens.SoftText)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
}

@Composable
private fun CatalogCover(
    entry: OpdsEntry,
    connection: OpdsConnection?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    val title = entry.title
    val palettes = listOf(
        listOf(Color(0xFFBFDDE7), Color(0xFF5A9EB0)),
        listOf(Color(0xFF203746), Color(0xFF101820)),
        listOf(Color(0xFFEADFC9), Color(0xFFC78B4A)),
        listOf(Color(0xFF9DBCC1), Color(0xFF38535B))
    )
    val colors = palettes[kotlin.math.abs(title.hashCode()) % palettes.size]
    val context = LocalContext.current
    val coverUrl = entry.coverLink?.href?.let { href ->
        connection?.let { runCatching { OpdsRequestFactory.resolveUrl(it, href) }.getOrNull() } ?: href
    }
    val request = coverUrl?.let { url ->
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                connection?.let(OpdsRequestFactory::basicAuthHeader)?.let { authorization ->
                    httpHeaders(NetworkHeaders.Builder().set("Authorization", authorization).build())
                }
            }
            .build()
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(Brush.verticalGradient(colors), RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.take(5),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}
