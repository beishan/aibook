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
import com.aibook.android.feature.server.CloudMockData
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
    private val mockMode = CloudMockData.enabled
    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val filter = MutableStateFlow(DownloadFilter.ACTIVE)
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
        if (mockMode) {
            tasks.value = mockDownloadTasks()
            speeds.value = mapOf("mock-download-running" to 1_835_008L)
        } else {
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
    }

    fun setFilter(value: DownloadFilter) { filter.value = value; selectedIds.value = emptySet() }
    fun toggleSelection(id: String) = selectedIds.update { if (id in it) it - id else it + id }
    fun selectAllVisible() { selectedIds.value = state.value.visibleTasks.map { it.id }.toSet() }
    fun clearSelection() { selectedIds.value = emptySet() }

    fun pause(id: String) = performOrMock(id, DownloadStatus.PAUSED) { queue.pause(id) }
    fun resume(id: String) = performOrMock(id, DownloadStatus.RUNNING) { queue.resume(id) }
    fun retry(id: String) = performOrMock(id, DownloadStatus.RUNNING) { queue.retry(id) }
    fun cancel(id: String) = performOrMock(id, DownloadStatus.CANCELLED) { queue.cancel(id) }
    fun remove(id: String) {
        if (mockMode) tasks.update { rows -> rows.filterNot { it.id == id } } else perform { queue.remove(id) }
    }

    fun pauseSelected() = performSelected({ it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }, DownloadStatus.PAUSED) { queue.pause(it.id) }
    fun pauseAll() = viewModelScope.launch {
        val active = state.value.tasks.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
        if (mockMode) updateMockTasks(active.map { it.id }.toSet(), DownloadStatus.PAUSED) else active.forEach { queue.pause(it.id) }
    }
    fun resumeSelected() = performSelected({ it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }, DownloadStatus.RUNNING) { task ->
        if (task.status == DownloadStatus.FAILED) queue.retry(task.id) else queue.resume(task.id)
    }
    fun cancelSelected() = performSelected({ it.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED) }, DownloadStatus.CANCELLED) { queue.cancel(it.id) }
    fun removeSelected() {
        val selected = state.value.tasks.filter { it.id in state.value.selectedIds && it.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED, DownloadStatus.FAILED) }
        if (mockMode) {
            val ids = selected.map { it.id }.toSet()
            tasks.update { rows -> rows.filterNot { it.id in ids } }
            selectedIds.value = emptySet()
        } else viewModelScope.launch { selected.forEach { queue.remove(it.id) }; selectedIds.value = emptySet() }
    }
    fun clearFinished() = viewModelScope.launch {
        val finished = state.value.tasks.filter { it.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED) }
        if (mockMode) {
            val ids = finished.map { it.id }.toSet()
            tasks.update { rows -> rows.filterNot { it.id in ids } }
        } else finished.forEach { queue.remove(it.id) }
    }

    private fun perform(action: suspend () -> Unit) { viewModelScope.launch { action() } }
    private fun performOrMock(id: String, status: DownloadStatus, action: suspend () -> Unit) {
        if (mockMode) updateMockTasks(setOf(id), status) else perform(action)
    }
    private fun updateMockTasks(ids: Set<String>, status: DownloadStatus) {
        tasks.update { rows -> rows.map { if (it.id in ids) it.copy(status = status, errorMessage = null, updatedAt = System.currentTimeMillis()) else it } }
    }
    private fun performSelected(predicate: (DownloadTask) -> Boolean, mockStatus: DownloadStatus, action: suspend (DownloadTask) -> Unit) {
        val selected = state.value.tasks.filter { it.id in state.value.selectedIds && predicate(it) }
        if (mockMode) {
            updateMockTasks(selected.map { it.id }.toSet(), mockStatus)
            selectedIds.value = emptySet()
        } else viewModelScope.launch {
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

private fun mockDownloadTasks(): List<DownloadTask> {
    val now = System.currentTimeMillis()
    return listOf(
        DownloadTask("mock-download-running", "cloud-9001", "云端书库", "三体Ⅱ：黑暗森林", "mock://cloud/9001", "三体Ⅱ：黑暗森林.epub", DownloadStatus.RUNNING, 68, 12_478_464, 18_350_080, null, now - 420_000, now),
        DownloadTask("mock-download-paused", "cloud-9002", "云端书库", "活着", "mock://cloud/9002", "活着.epub", DownloadStatus.PAUSED, 42, 3_145_728, 7_497_728, null, now - 720_000, now - 180_000),
        DownloadTask("mock-download-failed", "cloud-9003", "云端书库", "百年孤独", "mock://cloud/9003", "百年孤独.pdf", DownloadStatus.FAILED, 19, 2_097_152, 11_010_048, "网络连接已中断", now - 900_000, now - 240_000),
        DownloadTask("mock-download-completed", "cloud-9004", "云端书库", "小王子", "mock://cloud/9004", "小王子.epub", DownloadStatus.COMPLETED, 100, 5_242_880, 5_242_880, null, now - 1_800_000, now - 1_200_000)
    )
}

internal fun DownloadFilter.matchesStatus(status: DownloadStatus): Boolean = when (this) {
    DownloadFilter.ALL -> true
    DownloadFilter.ACTIVE -> status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED)
    DownloadFilter.PAUSED -> status == DownloadStatus.PAUSED
    DownloadFilter.FAILED -> status == DownloadStatus.FAILED
    DownloadFilter.COMPLETED -> status == DownloadStatus.COMPLETED
}
