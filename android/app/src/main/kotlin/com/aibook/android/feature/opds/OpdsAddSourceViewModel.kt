package com.aibook.android.feature.opds

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpdsAddSourceState(
    val formName: String = "汗牛充栋",
    val formBaseUrl: String = "",
    val formUsername: String = "",
    val formPassword: String = "",
    val editingConnectionId: String? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false
)

class OpdsAddSourceViewModel(
    private val connectionRepository: OpdsConnectionRepository
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
                    formPassword = connection.password.orEmpty()
                )
            }
        }
    }

    fun updateFormField(field: String, value: String) {
        _state.update {
            when (field) {
                "name" -> it.copy(formName = value)
                "baseUrl" -> it.copy(formBaseUrl = value)
                "username" -> it.copy(formUsername = value)
                "password" -> it.copy(formPassword = value)
                else -> it
            }
        }
    }

    fun save() {
        val state = _state.value
        if (state.formBaseUrl.isBlank()) {
            _state.update { it.copy(message = "请填写 OPDS 地址") }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val connection = connectionRepository.saveConnection(
                    name = state.formName,
                    baseUrl = state.formBaseUrl,
                    username = state.formUsername.ifBlank { null },
                    password = state.formPassword.ifBlank { null },
                    id = state.editingConnectionId
                )
                val wasEditing = state.editingConnectionId != null
                _state.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        message = if (wasEditing) "已保存修改：${connection.name}" else "已保存数据源：${connection.name}"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        message = "保存失败：${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                OpdsAddSourceViewModel(locator.opdsConnectionRepository)
            }
        }
    }
}
