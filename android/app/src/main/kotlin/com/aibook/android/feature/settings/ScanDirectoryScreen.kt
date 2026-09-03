package com.aibook.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.data.repository.ScanDirectory
import com.aibook.android.core.data.repository.DuplicateHandling
import com.aibook.android.ui.design.DesignTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanDirectoryScreen(
    onBack: () -> Unit,
    onStartScan: (() -> Unit)? = null,
    viewModel: ScanDirectoryViewModel = viewModel(factory = ScanDirectoryViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val completedStats = state.lastScanStats
    val snackbarHostState = remember { SnackbarHostState() }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.addDirectory(it) }
    }
    var repairingDirectoryId by remember { mutableStateOf<String?>(null) }
    val repairPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val id = repairingDirectoryId
        repairingDirectoryId = null
        if (uri != null && id != null) viewModel.reauthorizeDirectory(id, uri)
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAuthorizationStates()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (onStartScan != null) {
        ScanDirectoriesBundlePage(
            state = state,
            onBack = onBack,
            onAdd = { directoryPicker.launch(null) },
            onToggle = viewModel::setIncludeSubdirectories,
            onDelete = viewModel::deleteDirectory,
            onStartScan = onStartScan
        )
        SnackbarHost(snackbarHostState)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "扫描目录管理",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (state.isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(DesignTokens.CardRadius)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(DesignTokens.Space32),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
                    ) {
                        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp, color = DesignTokens.Accent)
                            Icon(Icons.Default.Folder, null, tint = DesignTokens.Accent, modifier = Modifier.size(54.dp))
                        }
                        Text("正在扫描中…", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("发现新书将自动加入书城", color = DesignTokens.SoftText)
                    }
                }
            } else if (completedStats != null) {
                val stats = completedStats
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(DesignTokens.CardRadius)
                ) {
                    Column(Modifier.padding(DesignTokens.Space16), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                        Text("扫描完成 · 共发现 ${stats.scanned} 本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("新增 ${stats.added + stats.restored} 本", color = DesignTokens.Success)
                            Text("已存在 ${stats.duplicate} 本", color = DesignTokens.SoftText)
                            Text("无法识别 ${stats.unsupported + stats.failed} 本", color = DesignTokens.Warning)
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectoryAction(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Refresh,
                        title = if (state.isScanning && state.scanningDirectoryId == null) "正在扫描" else "立即扫描",
                        subtitle = "手动扫描所有已启用目录",
                        enabled = !state.isScanning && state.directories.any { it.enabled && !it.requiresAuthorization },
                        onClick = onStartScan ?: viewModel::scanAll
                    )
                    Spacer(
                        Modifier
                            .padding(horizontal = 14.dp)
                            .height(48.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    )
                    DirectoryAction(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Add,
                        title = "添加目录",
                        subtitle = "选择并授权文件夹",
                        enabled = !state.isScanning,
                        onClick = { directoryPicker.launch(null) }
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("扫描到重复书时", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DuplicateHandling.entries.forEach { handling ->
                        val selected = state.duplicateHandling == handling
                        Text(
                            handling.label,
                            color = if (selected) Color.White else DesignTokens.Accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) DesignTokens.Accent else DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .clickable(enabled = !state.isScanning) { viewModel.setDuplicateHandling(handling) }
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                        )
                    }
                }
                Text(
                    when (state.duplicateHandling) {
                        DuplicateHandling.KEEP_VERSION -> "为重复内容创建独立版本，保留原书。"
                        DuplicateHandling.REPLACE -> "用扫描到的文件替换原文件，并保留阅读进度。"
                        DuplicateHandling.CANCEL -> "跳过重复文件，不修改原书。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = DesignTokens.Accent, modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f)) {
                            Text("后台自动扫描", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("由 WorkManager 在后台执行，系统重启后仍会保留", color = DesignTokens.SoftText)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启动应用时扫描", modifier = Modifier.weight(1f))
                        Switch(checked = state.autoScanOnStart, onCheckedChange = viewModel::setAutoScanOnStart, colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent))
                    }
                    Text("定时扫描", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "关闭", 6 to "每 6 小时", 24 to "每天", 168 to "每周").forEach { (hours, label) ->
                            val selected = state.scanIntervalHours == hours
                            Text(label, modifier = Modifier
                                .background(if (selected) DesignTokens.Accent else DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .clickable { viewModel.setScanIntervalHours(hours) }
                                .padding(horizontal = 12.dp, vertical = 9.dp), color = if (selected) Color.White else DesignTokens.Accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已配置目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("共 ${state.directories.size} 个目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                if (state.directories.isEmpty()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("还没有扫描目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("点击「添加目录」选择本机或云盘文件夹，授权后即可递归扫描电子书文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column {
                        state.directories.forEachIndexed { index, item ->
                            DirectoryRow(
                                item = item,
                                scanning = state.scanningDirectoryId == item.id,
                                busy = state.isScanning,
                                onScan = { viewModel.scanDirectory(item) },
                                onToggle = { enabled -> viewModel.setDirectoryEnabled(item.id, enabled) },
                                onReauthorize = {
                                    repairingDirectoryId = item.id
                                    repairPicker.launch(android.net.Uri.parse(item.uri))
                                },
                                onDelete = { viewModel.deleteDirectory(item.id) }
                            )
                            if (index != state.directories.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lightbulb, null, tint = DesignTokens.Accent)
                    Column {
                        Text("提示", fontWeight = FontWeight.Bold)
                        Text("支持扫描 EPUB、TXT、PDF、MOBI、AZW3、Markdown、HTML 文件；重复书籍按上方选择处理。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ScanDirectoriesBundlePage(
    state: ScanDirectoryUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onStartScan: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = DesignTokens.PagePadding, vertical = DesignTokens.Space16),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("扫描目录管理", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.MoreVert, "更多", tint = DesignTokens.TextPrimary)
        }
        Text("已添加的目录", color = DesignTokens.SoftText, style = MaterialTheme.typography.titleMedium)
        if (state.directories.isEmpty()) {
            SoftScanCard {
                Text("还没有扫描目录", fontWeight = FontWeight.Bold)
                Text("添加保存电子书的文件夹后即可开始扫描", color = DesignTokens.SoftText)
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(DesignTokens.CardRadius)) {
                state.directories.forEachIndexed { index, directory ->
                    Row(
                        Modifier.fillMaxWidth().padding(DesignTokens.Space16),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
                    ) {
                        Box(Modifier.size(52.dp).background(DesignTokens.WarmCard, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, null, tint = DesignTokens.Warning)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Space4)) {
                            Text(directory.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                        checked = directory.includeSubdirectories,
                                    onCheckedChange = { onToggle(directory.id, it) }
                                )
                                Text("包含子目录", color = DesignTokens.SoftText)
                            }
                        }
                        IconButton(onClick = { onDelete(directory.id) }) { Icon(Icons.Default.Delete, "删除目录", tint = DesignTokens.SoftText) }
                    }
                    if (index != state.directories.lastIndex) HorizontalDivider(color = DesignTokens.Hairline)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(116.dp).border(1.dp, DesignTokens.Accent.copy(alpha = 0.55f), RoundedCornerShape(DesignTokens.CardRadius)).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = DesignTokens.Accent)
                Text("添加扫描目录", color = DesignTokens.Accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "开始扫描",
            modifier = Modifier.fillMaxWidth().background(DesignTokens.Accent, RoundedCornerShape(DesignTokens.RadiusMedium)).clickable(enabled = state.directories.any { it.enabled }, onClick = onStartScan).padding(vertical = DesignTokens.Space16),
            color = Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LocalScanScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: ScanDirectoryViewModel = viewModel(factory = ScanDirectoryViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(state.directories, started) {
        if (!started && state.directories.isNotEmpty()) {
            started = true
            viewModel.scanAll()
        }
    }
    LaunchedEffect(started, state.isScanning, state.lastScanStats) {
        if (started && !state.isScanning && state.lastScanStats != null) onComplete()
    }
    val discovered = state.directories.sumOf { it.discoveredCount }
    val activeDirectory = state.directories.firstOrNull { it.id == state.scanningDirectoryId }
        ?: state.directories.firstOrNull { it.enabled }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = DesignTokens.PagePadding, vertical = DesignTokens.Space16).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("扫描本地书籍", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space16),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
            ) {
                Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 10.dp, color = DesignTokens.Accent, trackColor = DesignTokens.WarmCard)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, null, tint = DesignTokens.Warning, modifier = Modifier.size(58.dp))
                        Text(if (state.isScanning) "扫描中" else "准备中", color = DesignTokens.Accent, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (state.directories.isEmpty()) "没有可扫描的目录" else "正在扫描中…", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("发现新书将自动添加到书城", color = DesignTokens.SoftText)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                ScanMetricCard(Icons.Default.Folder, "扫描目录", "${state.directories.count { it.enabled }} 个", Modifier.weight(1f))
                ScanMetricCard(Icons.Default.Book, "发现书籍", "$discovered 本", Modifier.weight(1f))
                ScanMetricCard(Icons.Default.Schedule, "扫描状态", if (state.isScanning) "进行中" else "等待", Modifier.weight(1f))
            }
            SoftScanCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                    Icon(Icons.Default.Folder, null, tint = DesignTokens.Accent)
                    Column(Modifier.weight(1f)) {
                        Text("当前扫描路径", fontWeight = FontWeight.Bold)
                        Text(activeDirectory?.name ?: "等待选择目录", color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(
                "停止扫描",
                modifier = Modifier.fillMaxWidth().background(DesignTokens.Accent, RoundedCornerShape(DesignTokens.RadiusMedium)).clickable(onClick = viewModel::stopScan).padding(vertical = DesignTokens.Space16),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            SoftScanCard {
                Text("当前扫描状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(DesignTokens.Space12))
                Text("正在扫描：${activeDirectory?.name ?: "等待目录"}", color = DesignTokens.SoftText)
                Text("文件类型：EPUB、TXT、PDF、MOBI、AZW3", color = DesignTokens.SoftText)
                Text("提示：大文件夹扫描可能需要较长时间，请耐心等待", color = DesignTokens.SoftText)
            }
        }
    }
}

@Composable
fun ScanResultScreen(
    onBack: () -> Unit,
    onViewBooks: () -> Unit,
    onDone: () -> Unit,
    viewModel: ScanDirectoryViewModel = viewModel(factory = ScanDirectoryViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stats = state.lastScanStats
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = DesignTokens.PagePadding, vertical = DesignTokens.Space16).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("扫描完成", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Box(Modifier.size(128.dp).background(DesignTokens.Success.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CheckCircle, null, tint = DesignTokens.Success, modifier = Modifier.size(76.dp))
                }
                Text("共发现 ${stats?.scanned ?: 0} 本书籍", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("扫描已完成，书籍已自动分类整理", color = DesignTokens.SoftText)
            }
            SoftScanCard {
                Text("结果摘要", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ScanResultLine("新增书籍", (stats?.added ?: 0) + (stats?.restored ?: 0), DesignTokens.Success)
                ScanResultLine("已存在书籍", stats?.duplicate ?: 0, DesignTokens.Accent)
                ScanResultLine("无法识别", (stats?.unsupported ?: 0) + (stats?.failed ?: 0), DesignTokens.Warning)
            }
            SoftScanCard {
                Text("扫描目录（${state.directories.size} 个）", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                state.directories.forEach { directory ->
                    Row(Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space8), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = DesignTokens.Warning)
                        Text(directory.name, modifier = Modifier.weight(1f).padding(horizontal = DesignTokens.Space12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${directory.discoveredCount} 本", color = DesignTokens.SoftText)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
                Text("查看新增书籍", modifier = Modifier.weight(1f).background(DesignTokens.WarmCard, RoundedCornerShape(DesignTokens.RadiusMedium)).clickable(onClick = onViewBooks).padding(vertical = DesignTokens.Space16), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = DesignTokens.Accent, fontWeight = FontWeight.Bold)
                Text("完成", modifier = Modifier.weight(1f).background(DesignTokens.Accent, RoundedCornerShape(DesignTokens.RadiusMedium)).clickable(onClick = onDone).padding(vertical = DesignTokens.Space16), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScanMetricCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(DesignTokens.CardRadius)) {
        Column(Modifier.fillMaxWidth().padding(DesignTokens.Space12), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DesignTokens.Space8)) {
            Icon(icon, null, tint = DesignTokens.Warning)
            Text(label, color = DesignTokens.SoftText, style = MaterialTheme.typography.labelMedium)
            Text(value, color = DesignTokens.Accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SoftScanCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(DesignTokens.CardRadius)) {
        Column(Modifier.fillMaxWidth().padding(DesignTokens.Space16), content = content)
    }
}

@Composable
private fun ScanResultLine(label: String, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space8), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(label, modifier = Modifier.weight(1f).padding(horizontal = DesignTokens.Space12))
        Text("$count 本", color = DesignTokens.Accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DirectoryAction(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFFF5E7D8), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (enabled) DesignTokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DirectoryRow(
    item: ScanDirectory,
    scanning: Boolean,
    busy: Boolean,
    onScan: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onReauthorize: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFFFC67A), Color(0xFFE98732))),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "扫${item.discoveredCount}/新${item.addedCount}/重${item.duplicateCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            item.lastErrorMessage?.let {
                Text("错误：$it", color = Color(0xFFB44A35), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            if (item.requiresAuthorization) {
                Row(
                    modifier = Modifier.clickable(enabled = !busy, onClick = onReauthorize),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFFB44A35), modifier = Modifier.size(16.dp))
                    Text("重新授权文件夹", color = Color(0xFFB44A35), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            when {
                item.requiresAuthorization -> "需授权"
                item.enabled -> "已开启"
                else -> "已关闭"
            },
            color = when {
                item.requiresAuthorization -> Color(0xFFB44A35)
                item.enabled -> DesignTokens.Accent
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelSmall
        )
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
            enabled = !busy && !item.requiresAuthorization,
            colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
        )
        IconButton(onClick = onScan, enabled = item.enabled && !busy && !item.requiresAuthorization, modifier = Modifier.size(36.dp)) {
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "扫描", tint = DesignTokens.Accent, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatScanTime(value: Long?): String {
    if (value == null) return "未扫描"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
