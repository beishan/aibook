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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.data.repository.ScanDirectory
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

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
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
                colors = CardDefaults.cardColors(containerColor = DesignTokens.WarmCard),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
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
                        enabled = !state.isScanning && state.directories.any { it.enabled },
                        onClick = viewModel::scanAll
                    )
                    Spacer(
                        Modifier
                            .padding(horizontal = 14.dp)
                            .height(48.dp)
                            .width(1.dp)
                            .background(DesignTokens.Hairline)
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
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .shadow(8.dp, RoundedCornerShape(18.dp)),
//                colors = CardDefaults.cardColors(containerColor = Color.White),
//                shape = RoundedCornerShape(18.dp)
//            ) {
//                Row(
//                    modifier = Modifier.padding(20.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(16.dp)
//                ) {
//                    Icon(Icons.Default.Schedule, null, tint = DesignTokens.Accent, modifier = Modifier.size(34.dp))
//                    Column(Modifier.weight(1f)) {
//                        Text("启动应用时自动扫描", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
//                        Text("真实目录授权已保存，自动扫描任务后续可接入 WorkManager", color = DesignTokens.SoftText)
//                    }
//                    Switch(
//                        checked = false,
//                        onCheckedChange = {},
//                        enabled = false,
//                        colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
//                    )
//                }
//            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已配置目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("共 ${state.directories.size} 个目录", color = DesignTokens.SoftText)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DesignTokens.Hairline)
            ) {
                if (state.directories.isEmpty()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("还没有扫描目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("点击「添加目录」选择本机或云盘文件夹，授权后即可递归扫描电子书文件。", color = DesignTokens.SoftText)
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
                                onDelete = { viewModel.deleteDirectory(item.id) }
                            )
                            if (index != state.directories.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color = DesignTokens.Hairline
                                )
                            }
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DesignTokens.WarmCard),
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
                        Text("支持扫描 EPUB、TXT、PDF、Markdown、HTML 文件，重复书籍会按内容哈希自动跳过。", color = DesignTokens.SoftText)
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
            Icon(icon, null, tint = if (enabled) DesignTokens.Accent else DesignTokens.SoftText, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DesignTokens.SoftText, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DesignTokens.SoftText)
    }
}

@Composable
private fun DirectoryRow(
    item: ScanDirectory,
    scanning: Boolean,
    busy: Boolean,
    onScan: () -> Unit,
    onToggle: (Boolean) -> Unit,
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
            Text(item.uri, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Text(
                "扫描 ${formatScanTime(item.lastScanAt)} · 发现 ${item.discoveredCount} · 新增 ${item.addedCount} · 重复 ${item.duplicateCount}",
                color = DesignTokens.SoftText,
                style = MaterialTheme.typography.bodySmall
            )
            item.lastErrorMessage?.let {
                Text("错误：$it", color = Color(0xFFB44A35), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            if (item.enabled) "已开启" else "已关闭",
            color = if (item.enabled) DesignTokens.Accent else DesignTokens.SoftText,
            style = MaterialTheme.typography.labelSmall
        )
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
            enabled = !busy,
            colors = SwitchDefaults.colors(checkedTrackColor = DesignTokens.Accent)
        )
        IconButton(onClick = onScan, enabled = item.enabled && !busy, modifier = Modifier.size(36.dp)) {
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "扫描", tint = DesignTokens.Accent, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = DesignTokens.SoftText, modifier = Modifier.size(20.dp))
        }
    }
}

private fun formatScanTime(value: Long?): String {
    if (value == null) return "未扫描"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
