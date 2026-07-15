package com.aibook.android.feature.opds

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsSyncMode
import com.aibook.android.core.network.opds.OpdsUrlValidator
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OpdsAddSourceState(
    val formName: String = "汗牛充栋",
    val formBaseUrl: String = "",
    val formUsername: String = "",
    val formPassword: String = "",
    val syncMode: OpdsSyncMode = OpdsSyncMode.FULL,
    val editingConnectionId: String? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testedFingerprint: String? = null,
    val message: String? = null,
    val saved: Boolean = false
) {
    val canSave: Boolean get() = testedFingerprint == fingerprint() && !isSaving && !isTesting
    fun fingerprint(): String = "$formBaseUrl\u0000$formUsername\u0000$formPassword"
}

class OpdsAddSourceViewModel(
    private val connectionRepository: OpdsConnectionRepository,
    private val catalogService: OpdsCatalogService
) : ViewModel() {
    private val _state = MutableStateFlow(OpdsAddSourceState())
    val uiState: StateFlow<OpdsAddSourceState> = _state.asStateFlow()

    fun loadConnection(connectionId: String) {
        viewModelScope.launch {
            val connection = connectionRepository.getById(connectionId) ?: return@launch
            _state.update {
                it.copy(
                    editingConnectionId = connection.id,
                    formName = connection.name,
                    formBaseUrl = connection.baseUrl,
                    formUsername = connection.username.orEmpty(),
                    formPassword = connection.password.orEmpty(),
                    syncMode = connection.syncMode,
                    testedFingerprint = null
                )
            }
        }
    }

    fun updateFormField(field: String, value: String) {
        _state.update { current ->
            val next = when (field) {
                "name" -> current.copy(formName = value)
                "baseUrl" -> current.copy(formBaseUrl = value)
                "username" -> current.copy(formUsername = value)
                "password" -> current.copy(formPassword = value)
                else -> current
            }
            if (field == "name") next else next.copy(testedFingerprint = null)
        }
    }

    fun setSyncMode(mode: OpdsSyncMode) = _state.update { it.copy(syncMode = mode) }

    fun testConnection() {
        val snapshot = _state.value
        val normalized = OpdsUrlValidator.normalize(snapshot.formBaseUrl).getOrElse {
            _state.update { state -> state.copy(message = it.message ?: "URL 无效") }
            return
        }
        _state.update { it.copy(isTesting = true, message = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    catalogService.load(
                        OpdsConnection(
                            id = snapshot.editingConnectionId ?: "connection-test",
                            name = snapshot.formName,
                            baseUrl = normalized,
                            username = snapshot.formUsername.ifBlank { null },
                            password = snapshot.formPassword.ifBlank { null }
                        )
                    )
                }
            }.onSuccess { feed ->
                _state.update {
                    it.copy(
                        formBaseUrl = normalized,
                        isTesting = false,
                        testedFingerprint = it.copy(formBaseUrl = normalized).fingerprint(),
                        message = "连接成功：${feed.title.ifBlank { "OPDS 目录" }}"
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isTesting = false, testedFingerprint = null, message = "连接失败：${error.message ?: "无法读取目录"}") }
            }
        }
    }

    fun save() {
        val snapshot = _state.value
        if (!snapshot.canSave) {
            _state.update { it.copy(message = "请先测试当前连接信息") }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                connectionRepository.saveConnection(
                    name = snapshot.formName,
                    baseUrl = snapshot.formBaseUrl,
                    username = snapshot.formUsername.ifBlank { null },
                    password = snapshot.formPassword.ifBlank { null },
                    id = snapshot.editingConnectionId,
                    syncMode = snapshot.syncMode
                )
            }.onSuccess { connection ->
                _state.update { it.copy(isSaving = false, saved = true, message = "已保存数据源：${connection.name}") }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, message = "保存失败：${error.message}") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val locator = ServiceLocator.get(this[APPLICATION_KEY] as Application)
                OpdsAddSourceViewModel(locator.opdsConnectionRepository, locator.opdsCatalogService)
            }
        }
    }
}
