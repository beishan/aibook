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
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OpdsUiState(
    val connections: List<OpdsConnection> = emptyList(),
    val activeConnection: OpdsConnection? = null,
    val currentFeed: OpdsFeed? = null,
    val navigationStack: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showConnectionForm: Boolean = false,
    val formName: String = "汗牛充栋",
    val formBaseUrl: String = "",
    val formUsername: String = "",
    val formPassword: String = ""
)

class OpdsViewModel(
    private val connectionRepository: OpdsConnectionRepository,
    private val catalogService: OpdsCatalogService
) : ViewModel() {

    private val _state = MutableStateFlow(OpdsUiState())
    val uiState: StateFlow<OpdsUiState> = connectionRepository.observeConnections()
        .let { connectionsFlow ->
            combine(connectionsFlow, _state) { connections, state ->
                state.copy(connections = connections)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OpdsUiState())

    fun showConnectionForm(show: Boolean) {
        _state.value = _state.value.copy(showConnectionForm = show)
    }

    fun updateFormField(field: String, value: String) {
        _state.value = when (field) {
            "name" -> _state.value.copy(formName = value)
            "baseUrl" -> _state.value.copy(formBaseUrl = value)
            "username" -> _state.value.copy(formUsername = value)
            "password" -> _state.value.copy(formPassword = value)
            else -> _state.value
        }
    }

    fun saveConnection() {
        viewModelScope.launch {
            val state = _state.value
            val connection = connectionRepository.saveConnection(
                name = state.formName,
                baseUrl = state.formBaseUrl,
                username = state.formUsername.ifBlank { null },
                password = state.formPassword.ifBlank { null }
            )
            _state.value = _state.value.copy(showConnectionForm = false)
            browse(connection)
        }
    }

    fun selectConnection(connection: OpdsConnection) {
        browse(connection)
    }

    fun browse(connection: OpdsConnection, href: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                activeConnection = connection
            )
            try {
                val feed = connectionRepository.browse(catalogService, connection, href)
                _state.value = _state.value.copy(
                    currentFeed = feed,
                    isLoading = false,
                    navigationStack = if (href != null) _state.value.navigationStack + href else emptyList()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "连接失败：${e.message}"
                )
            }
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            val stack = _state.value.navigationStack
            if (stack.isEmpty()) return@launch

            val newStack = stack.dropLast(1)
            val connection = _state.value.activeConnection ?: return@launch
            _state.value = _state.value.copy(
                isLoading = true,
                navigationStack = newStack
            )
            try {
                val href = newStack.lastOrNull()
                val feed = connectionRepository.browse(catalogService, connection, href)
                _state.value = _state.value.copy(currentFeed = feed, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "导航失败：${e.message}"
                )
            }
        }
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            connectionRepository.deleteConnection(id)
            if (_state.value.activeConnection?.id == id) {
                _state.value = _state.value.copy(
                    activeConnection = null,
                    currentFeed = null,
                    navigationStack = emptyList()
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                OpdsViewModel(
                    locator.opdsConnectionRepository,
                    locator.opdsCatalogService
                )
            }
        }
    }
}
