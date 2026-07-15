package com.aibook.android.feature.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Checkbox
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
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
            if (state.tasks.any { it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.CANCELLED }) {
                TextButton(onClick = viewModel::clearFinished) { Text("清理记录") }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DownloadFilter.entries) { filter ->
                val selected = state.filter == filter
                Text(
                    filter.label,
                    modifier = Modifier.background(if (selected) DesignTokens.Accent else DesignTokens.Accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.setFilter(filter) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (selected) Color.White else DesignTokens.Accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

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
                    DownloadTaskCard(task, state.speeds[task.id] ?: 0L, task.id in state.selectedIds, viewModel)
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: DownloadTask, speed: Long, selected: Boolean, viewModel: DownloadManagerViewModel) {
    SoftCard(color = Color.White, modifier = Modifier.clickable { viewModel.toggleSelection(task.id) }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { viewModel.toggleSelection(task.id) })
                Column(Modifier.weight(1f)) {
                    Text(task.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.fileName, color = DesignTokens.SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(statusLabel(task.status), color = statusColor(task.status), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${task.progress}% · ${bytesLabel(task.downloadedBytes)} / ${bytesLabel(task.totalBytes)}", color = DesignTokens.SoftText)
                Text(downloadDetail(task, speed), color = DesignTokens.SoftText)
            }
            task.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (task.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED -> TextButton(onClick = { viewModel.pause(task.id) }) { Icon(Icons.Default.Pause, null); Text("暂停") }
                    DownloadStatus.PAUSED -> TextButton(onClick = { viewModel.resume(task.id) }) { Icon(Icons.Default.PlayArrow, null); Text("继续") }
                    DownloadStatus.FAILED -> TextButton(onClick = { viewModel.retry(task.id) }) { Icon(Icons.Default.Replay, null); Text("重试") }
                    else -> Unit
                }
                if (task.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED)) {
                    TextButton(onClick = { viewModel.cancel(task.id) }) { Icon(Icons.Default.Cancel, null); Text("取消") }
                }
                if (task.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.FAILED)) {
                    TextButton(onClick = { viewModel.remove(task.id) }) { Icon(Icons.Default.Delete, null); Text("删除记录") }
                }
            }
        }
    }
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
