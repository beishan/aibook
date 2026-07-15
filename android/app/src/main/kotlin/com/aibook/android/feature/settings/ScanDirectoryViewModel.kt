package com.aibook.android.feature.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.ScanDirectory
import com.aibook.android.core.data.repository.DuplicateHandling
import com.aibook.android.core.data.repository.ScanDirectoryRepository
import com.aibook.android.core.data.repository.ScanImportStats
import com.aibook.android.core.data.prefs.BackgroundTaskStore
import com.aibook.android.background.BackgroundWorkScheduler
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScanDirectoryUiState(
    val directories: List<ScanDirectory> = emptyList(),
    val isScanning: Boolean = false,
    val scanningDirectoryId: String? = null,
    val duplicateHandling: DuplicateHandling = DuplicateHandling.CANCEL,
    val autoScanOnStart: Boolean = false,
    val scanIntervalHours: Int = 0,
    val message: String? = null
)

class ScanDirectoryViewModel(
    private val app: Application,
    private val repository: ScanDirectoryRepository,
    private val backgroundTaskStore: BackgroundTaskStore
) : ViewModel() {
    private val _state = MutableStateFlow(ScanDirectoryUiState())
    val state: StateFlow<ScanDirectoryUiState> = repository.observeDirectories()
        .let { directoriesFlow ->
            combine(directoriesFlow, _state) { directories, state ->
                state.copy(directories = directories)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanDirectoryUiState())

    init {
        viewModelScope.launch { backgroundTaskStore.autoScanOnStart.collect { value -> _state.value = _state.value.copy(autoScanOnStart = value) } }
        viewModelScope.launch { backgroundTaskStore.scanIntervalHours.collect { value -> _state.value = _state.value.copy(scanIntervalHours = value) } }
    }

    fun addDirectory(uri: Uri) {
        viewModelScope.launch {
            if (!takePersistableReadPermission(uri)) {
                _state.value = _state.value.copy(message = "无法保存目录读取权限，请重新选择文件夹")
                return@launch
            }
            val directory = repository.addDirectory(uri)
            _state.value = _state.value.copy(message = "已添加目录：${directory.name}")
        }
    }

    fun reauthorizeDirectory(id: String, uri: Uri) {
        viewModelScope.launch {
            if (!takePersistableReadPermission(uri)) {
                _state.value = _state.value.copy(message = "重新授权失败，请确认已允许读取该文件夹")
                return@launch
            }
            val oldUri = _state.value.directories.firstOrNull { it.id == id }?.uri
            val repaired = repository.reauthorizeDirectory(id, uri)
            if (repaired == null) {
                _state.value = _state.value.copy(message = "目录记录不存在")
                return@launch
            }
            if (!oldUri.isNullOrBlank() && oldUri != uri.toString()) {
                runCatching {
                    app.contentResolver.releasePersistableUriPermission(Uri.parse(oldUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            _state.value = _state.value.copy(message = "目录授权已恢复：${repaired.name}")
        }
    }

    fun scanAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, scanningDirectoryId = null, message = null)
            val stats = repository.scanAllEnabled(_state.value.duplicateHandling)
            _state.value = _state.value.copy(
                isScanning = false,
                message = stats.toMessage("扫描完成")
            )
        }
    }

    fun scanDirectory(directory: ScanDirectory) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, scanningDirectoryId = directory.id, message = null)
            val stats = repository.scanDirectory(directory.id, _state.value.duplicateHandling)
            _state.value = _state.value.copy(
                isScanning = false,
                scanningDirectoryId = null,
                message = stats.toMessage("${directory.name} 扫描完成")
            )
        }
    }

    fun setDirectoryEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
        }
    }

    fun deleteDirectory(id: String) {
        viewModelScope.launch {
            repository.deleteDirectory(id)
        }
    }

    fun refreshAuthorizationStates() {
        viewModelScope.launch { repository.refreshAuthorizationStates() }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun setDuplicateHandling(value: DuplicateHandling) {
        if (!_state.value.isScanning) _state.value = _state.value.copy(duplicateHandling = value)
    }

    fun setAutoScanOnStart(enabled: Boolean) {
        viewModelScope.launch { backgroundTaskStore.setAutoScanOnStart(enabled) }
    }

    fun setScanIntervalHours(hours: Int) {
        viewModelScope.launch {
            backgroundTaskStore.setScanIntervalHours(hours)
            BackgroundWorkScheduler.configureScan(app, hours)
        }
    }

    private fun takePersistableReadPermission(uri: Uri): Boolean {
        return runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            app.contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri == uri }
        }.getOrDefault(false)
    }

    private fun ScanImportStats.toMessage(prefix: String): String {
        return "$prefix：扫描 $scanned 个文件，新增 $added 本，恢复 $restored 本，重复 $duplicate 本，不支持 $unsupported 个，失败 $failed 个"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ScanDirectoryViewModel(
                    app = app,
                    repository = ServiceLocator.get(app).scanDirectoryRepository,
                    backgroundTaskStore = ServiceLocator.get(app).backgroundTaskStore
                )
            }
        }
    }
}
