package com.aibook.android.feature.downloads

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibook.android.core.data.repository.DownloadStatus
import com.aibook.android.core.data.repository.DownloadTask
import com.aibook.android.ui.design.DesignTokens
import com.aibook.android.ui.design.SoftCard
import com.aibook.android.ui.design.SlidingSegmentedControl
import com.aibook.android.ui.design.BookCover
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
    onTaskClick: (String) -> Unit = {},
    viewModel: DownloadManagerViewModel = viewModel(factory = DownloadManagerViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text("下载管理", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("下载中 ${state.activeCount} · 失败 ${state.failedCount} · 已完成 ${state.completedCount}", color = DesignTokens.SoftText)
            }
            IconButton(onClick = viewModel::clearFinished) { Icon(Icons.Default.MoreVert, "更多") }
        }

        SlidingSegmentedControl(
            options = listOf("下载中 (${state.activeCount})", "已完成 (${state.completedCount})"),
            selectedIndex = if (state.filter == DownloadFilter.COMPLETED) 1 else 0,
            onSelected = { viewModel.setFilter(if (it == 0) DownloadFilter.ACTIVE else DownloadFilter.COMPLETED) }
        )

        if (state.selectedIds.isNotEmpty()) {
            val selectedTasks = state.tasks.filter { it.id in state.selectedIds }
            SoftCard(color = DesignTokens.WarmCard) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("已选 ${state.selectedIds.size} 项", fontWeight = FontWeight.Bold)
                        Text("取消选择", modifier = Modifier.clickable(onClick = viewModel::clearSelection), color = DesignTokens.Accent)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item { TextButton(onClick = viewModel::pauseSelected, enabled = selectedTasks.any { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }) { Icon(Icons.Default.Pause, null); Text("暂停") } }
                        item { TextButton(onClick = viewModel::resumeSelected, enabled = selectedTasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }) { Icon(Icons.Default.PlayArrow, null); Text("继续/重试") } }
                        item { TextButton(onClick = viewModel::cancelSelected, enabled = selectedTasks.any { it.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED) }) { Icon(Icons.Default.Cancel, null); Text("取消") } }
                        item { TextButton(onClick = viewModel::removeSelected, enabled = selectedTasks.any { it.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.FAILED) }) { Icon(Icons.Default.Delete, null); Text("删除记录") } }
                    }
                }
            }
        } else if (state.visibleTasks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("全选当前分组", modifier = Modifier.clickable(onClick = viewModel::selectAllVisible), color = DesignTokens.Accent, fontWeight = FontWeight.Bold)
            }
        }

        if (state.visibleTasks.isEmpty()) {
            SoftCard(color = Color.White) {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = DesignTokens.SoftText)
                    Text("当前分组没有下载任务", fontWeight = FontWeight.Bold)
                    Text("从书城或 OPDS 目录下载书籍后，任务会显示在这里。", color = DesignTokens.SoftText)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visibleTasks, key = { it.id }) { task ->
                    DownloadTaskCard(task, state.speeds[task.id] ?: 0L, task.id in state.selectedIds, viewModel) { onTaskClick(task.id) }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
        if (state.filter == DownloadFilter.ACTIVE && state.visibleTasks.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)
            ) {
                Icon(Icons.Default.Download, null, tint = DesignTokens.SoftText)
                Column(Modifier.weight(1f)) {
                    Text("下载保存位置", color = DesignTokens.SoftText)
                    Text("手机存储/Books/Downloads")
                }
                Text("›", color = DesignTokens.SoftText, style = MaterialTheme.typography.headlineMedium)
            }
            Button(
                onClick = viewModel::pauseAll,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
            ) { Text("全部暂停", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: DownloadTask, speed: Long, selected: Boolean, viewModel: DownloadManagerViewModel, onOpen: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), verticalAlignment = Alignment.CenterVertically) {
            BookCover(task.title, width = 92.dp, height = 132.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.fileName, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(statusLabel(task.status), color = statusColor(task.status), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${bytesLabel(task.downloadedBytes)} / ${bytesLabel(task.totalBytes)}", color = DesignTokens.SoftText)
                    Text("${task.progress}%", fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = DesignTokens.Accent)
                Text(downloadDetail(task, speed), color = DesignTokens.Accent)
                task.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (task.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> IconButton(onClick = { viewModel.pause(task.id) }) { Icon(Icons.Default.Pause, "暂停", tint = DesignTokens.Accent) }
                    DownloadStatus.PAUSED -> IconButton(onClick = { viewModel.resume(task.id) }) { Icon(Icons.Default.PlayArrow, "继续", tint = DesignTokens.Accent) }
                    DownloadStatus.FAILED -> IconButton(onClick = { viewModel.retry(task.id) }) { Icon(Icons.Default.Replay, "重试", tint = DesignTokens.Accent) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun DownloadDetailScreen(
    taskId: String,
    onBack: () -> Unit,
    viewModel: DownloadManagerViewModel = viewModel(factory = DownloadManagerViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val task = state.tasks.firstOrNull { it.id == taskId }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(DesignTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Space16)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("下载详情", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (task == null) {
            SoftCard { Text("下载任务不存在或已被清理", color = DesignTokens.SoftText) }
            return@Column
        }
        SoftCard {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space16), verticalAlignment = Alignment.CenterVertically) {
                BookCover(task.title, width = 94.dp, height = 136.dp)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(task.fileName, color = DesignTokens.SoftText)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.size(190.dp),
                strokeWidth = 12.dp,
                color = DesignTokens.Accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text("${task.progress}%", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        }
        Text(downloadDetail(task, state.speeds[task.id] ?: 0L), modifier = Modifier.align(Alignment.CenterHorizontally), color = DesignTokens.Accent)
        SoftCard {
            DownloadInfoRow("来源", task.connectionId)
            DownloadInfoRow("文件大小", bytesLabel(task.totalBytes))
            DownloadInfoRow("已下载", bytesLabel(task.downloadedBytes))
            DownloadInfoRow("开始时间", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(task.createdAt)))
            val speed = state.speeds[task.id] ?: 0L
            val remainingSeconds = if (speed > 0) task.totalBytes?.minus(task.downloadedBytes)?.coerceAtLeast(0)?.div(speed) else null
            DownloadInfoRow("预计剩余时间", remainingSeconds?.let { "${it / 60} 分 ${it % 60} 秒" } ?: "计算中")
            DownloadInfoRow("存储位置", "手机存储/Books/Downloads", false)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            Button(
                onClick = { if (task.status == DownloadStatus.PAUSED) viewModel.resume(task.id) else viewModel.pause(task.id) },
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = DesignTokens.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
            ) { Text(if (task.status == DownloadStatus.PAUSED) "继续" else "暂停") }
            Button(
                onClick = { viewModel.cancel(task.id); onBack() },
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesignTokens.Accent),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
            ) { Text("取消") }
        }
    }
}

@Composable
private fun DownloadInfoRow(label: String, value: String, divider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space12), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DesignTokens.SoftText)
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (divider) androidx.compose.material3.HorizontalDivider(color = DesignTokens.Hairline)
}

private fun statusLabel(status: DownloadStatus) = when (status) {
    DownloadStatus.QUEUED -> "等待中"; DownloadStatus.RUNNING -> "下载中"; DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.COMPLETED -> "已完成"; DownloadStatus.FAILED -> "失败"; DownloadStatus.CANCELLED -> "已取消"
}
private fun statusColor(status: DownloadStatus) = when (status) {
    DownloadStatus.COMPLETED -> DesignTokens.Success
    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Color(0xFFB44A35)
    else -> DesignTokens.Accent
}
private fun bytesLabel(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "未知"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
}
private fun downloadDetail(task: DownloadTask, speed: Long): String {
    if (task.status == DownloadStatus.RUNNING && speed > 0) {
        val remaining = task.totalBytes?.minus(task.downloadedBytes)?.coerceAtLeast(0)
        val seconds = remaining?.div(speed)
        return "${bytesLabel(speed)}/s${seconds?.let { " · 剩余 ${it / 60}分${it % 60}秒" }.orEmpty()}"
    }
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(task.updatedAt))
}
