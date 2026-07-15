package com.aibook.android.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    viewModel: ScanDirectoryViewModel = viewModel(factory = ScanDirectoryViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                        onClick = viewModel::scanAll
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
