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
import com.aibook.android.core.data.repository.ScanDirectoryRepository
import com.aibook.android.core.data.repository.ScanImportStats
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
    val message: String? = null
)

class ScanDirectoryViewModel(
    private val app: Application,
    private val repository: ScanDirectoryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ScanDirectoryUiState())
    val state: StateFlow<ScanDirectoryUiState> = repository.observeDirectories()
        .let { directoriesFlow ->
            combine(directoriesFlow, _state) { directories, state ->
                state.copy(directories = directories)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanDirectoryUiState())

    fun addDirectory(uri: Uri) {
        viewModelScope.launch {
            takePersistableReadPermission(uri)
            val directory = repository.addDirectory(uri)
            _state.value = _state.value.copy(message = "已添加目录：${directory.name}")
        }
    }

    fun scanAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, scanningDirectoryId = null, message = null)
            val stats = repository.scanAllEnabled()
            _state.value = _state.value.copy(
                isScanning = false,
                message = stats.toMessage("扫描完成")
            )
        }
    }

    fun scanDirectory(directory: ScanDirectory) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, scanningDirectoryId = directory.id, message = null)
            val stats = repository.scanDirectory(directory.id)
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

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun takePersistableReadPermission(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun ScanImportStats.toMessage(prefix: String): String {
        return "$prefix：扫描 $scanned 个文件，新增 $added 本，重复 $duplicate 本，不支持 $unsupported 个，失败 $failed 个"
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ScanDirectoryViewModel(
                    app = app,
                    repository = ServiceLocator.get(app).scanDirectoryRepository
                )
            }
        }
    }
}
