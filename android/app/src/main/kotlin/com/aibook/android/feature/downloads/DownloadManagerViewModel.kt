package com.aibook.android.feature.downloads

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.background.DownloadQueueManager
import com.aibook.android.core.data.repository.DownloadStatus
import com.aibook.android.core.data.repository.DownloadTask
import com.aibook.android.core.data.repository.DownloadTaskRepository
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadFilter(val label: String) {
    ALL("全部"), ACTIVE("下载中"), PAUSED("已暂停"), FAILED("失败"), COMPLETED("已完成")
}

data class DownloadManagerState(
    val tasks: List<DownloadTask> = emptyList(),
    val visibleTasks: List<DownloadTask> = emptyList(),
    val filter: DownloadFilter = DownloadFilter.ALL,
    val selectedIds: Set<String> = emptySet(),
    val speeds: Map<String, Long> = emptyMap()
) {
    val activeCount get() = tasks.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
    val failedCount get() = tasks.count { it.status == DownloadStatus.FAILED }
    val completedCount get() = tasks.count { it.status == DownloadStatus.COMPLETED }
}

class DownloadManagerViewModel(
    private val repository: DownloadTaskRepository,
    private val queue: DownloadQueueManager
) : ViewModel() {
    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val filter = MutableStateFlow(DownloadFilter.ALL)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val speeds = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val samples = mutableMapOf<String, Pair<Long, Long>>()

    val state: StateFlow<DownloadManagerState> = combine(tasks, filter, selectedIds, speeds) { rows, selectedFilter, selected, currentSpeeds ->
        DownloadManagerState(
            tasks = rows,
            visibleTasks = rows.filter { selectedFilter.matchesStatus(it.status) },
            filter = selectedFilter,
            selectedIds = selected.intersect(rows.map { it.id }.toSet()),
            speeds = currentSpeeds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadManagerState())

    init {
        viewModelScope.launch {
            repository.observeAll().collect { rows ->
                val now = SystemClock.elapsedRealtime()
                speeds.value = rows.associate { task ->
                    val previous = samples[task.id]
                    val speed = if (task.status == DownloadStatus.RUNNING && previous != null && now > previous.second && task.downloadedBytes >= previous.first) {
                        ((task.downloadedBytes - previous.first) * 1000L / (now - previous.second)).coerceAtLeast(0)
                    } else 0L
                    samples[task.id] = task.downloadedBytes to now
                    task.id to speed
                }
                samples.keys.retainAll(rows.map { it.id }.toSet())
                tasks.value = rows
            }
        }
    }

    fun setFilter(value: DownloadFilter) { filter.value = value; selectedIds.value = emptySet() }
    fun toggleSelection(id: String) = selectedIds.update { if (id in it) it - id else it + id }
    fun selectAllVisible() { selectedIds.value = state.value.visibleTasks.map { it.id }.toSet() }
    fun clearSelection() { selectedIds.value = emptySet() }

    fun pause(id: String) = perform { queue.pause(id) }
    fun resume(id: String) = perform { queue.resume(id) }
    fun retry(id: String) = perform { queue.retry(id) }
    fun cancel(id: String) = perform { queue.cancel(id) }
    fun remove(id: String) = perform { queue.remove(id) }

    fun pauseSelected() = performSelected({ it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }) { queue.pause(it.id) }
    fun resumeSelected() = performSelected({ it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }) { task ->
        if (task.status == DownloadStatus.FAILED) queue.retry(task.id) else queue.resume(task.id)
    }
    fun cancelSelected() = performSelected({ it.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED) }) { queue.cancel(it.id) }
    fun removeSelected() = performSelected({ it.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.FAILED) }) { queue.remove(it.id) }
    fun clearFinished() = viewModelScope.launch {
        state.value.tasks.filter { it.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED) }.forEach { queue.remove(it.id) }
    }

    private fun perform(action: suspend () -> Unit) { viewModelScope.launch { action() } }
    private fun performSelected(predicate: (DownloadTask) -> Boolean, action: suspend (DownloadTask) -> Unit) {
        val selected = state.value.tasks.filter { it.id in state.value.selectedIds && predicate(it) }
        viewModelScope.launch {
            selected.forEach { action(it) }
            selectedIds.value = emptySet()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                DownloadManagerViewModel(locator.downloadTaskRepository, DownloadQueueManager(app))
            }
        }
    }
}

internal fun DownloadFilter.matchesStatus(status: DownloadStatus): Boolean = when (this) {
    DownloadFilter.ALL -> true
    DownloadFilter.ACTIVE -> status == DownloadStatus.RUNNING || status == DownloadStatus.QUEUED
    DownloadFilter.PAUSED -> status == DownloadStatus.PAUSED
    DownloadFilter.FAILED -> status == DownloadStatus.FAILED
    DownloadFilter.COMPLETED -> status == DownloadStatus.COMPLETED
}
