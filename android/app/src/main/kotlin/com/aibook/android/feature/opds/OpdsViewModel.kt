package com.aibook.android.feature.opds

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.ImportResult
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsDownloadNamer
import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsSyncState
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
    val statusMessage: String? = null,
    val downloadingTitle: String? = null,
    val showConnectionForm: Boolean = false,
    val editingConnectionId: String? = null,
    val formName: String = "汗牛充栋",
    val formBaseUrl: String = "",
    val formUsername: String = "",
    val formPassword: String = ""
)

class OpdsViewModel(
    private val connectionRepository: OpdsConnectionRepository,
    private val bookRepository: BookRepository,
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
        _state.value = if (show) {
            _state.value.copy(showConnectionForm = true)
        } else {
            _state.value.copy(
                showConnectionForm = false,
                editingConnectionId = null,
                formName = "汗牛充栋",
                formBaseUrl = "",
                formUsername = "",
                formPassword = ""
            )
        }
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

    fun saveConnection(
        browseAfterSave: Boolean = true,
        onSaved: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val state = _state.value
            if (state.formBaseUrl.isBlank()) {
                _state.value = state.copy(errorMessage = "请填写 OPDS 地址")
                return@launch
            }
            val connection = connectionRepository.saveConnection(
                name = state.formName,
                baseUrl = state.formBaseUrl,
                username = state.formUsername.ifBlank { null },
                password = state.formPassword.ifBlank { null },
                id = state.editingConnectionId
            )
            _state.value = _state.value.copy(
                showConnectionForm = false,
                editingConnectionId = null,
                formName = "汗牛充栋",
                formBaseUrl = "",
                formUsername = "",
                formPassword = "",
                statusMessage = "已保存数据源：${connection.name}"
            )
            if (browseAfterSave) {
                browse(connection)
            }
            onSaved?.invoke()
        }
    }

    fun selectConnection(connection: OpdsConnection) {
        if (!connection.enabled) {
            _state.value = _state.value.copy(statusMessage = "数据源已停用：${connection.name}")
            return
        }
        browse(connection)
    }

    fun editConnection(connection: OpdsConnection) {
        _state.value = _state.value.copy(
            showConnectionForm = true,
            editingConnectionId = connection.id,
            formName = connection.name,
            formBaseUrl = connection.baseUrl,
            formUsername = connection.username.orEmpty(),
            formPassword = connection.password.orEmpty(),
            currentFeed = null,
            navigationStack = emptyList()
        )
    }

    fun toggleConnectionEnabled(connection: OpdsConnection, enabled: Boolean) {
        viewModelScope.launch {
            connectionRepository.updateEnabled(connection.id, enabled)
            if (!enabled && _state.value.activeConnection?.id == connection.id) {
                _state.value = _state.value.copy(
                    activeConnection = null,
                    currentFeed = null,
                    navigationStack = emptyList(),
                    statusMessage = "已停用数据源：${connection.name}"
                )
            } else {
                _state.value = _state.value.copy(
                    statusMessage = if (enabled) "已启用数据源：${connection.name}" else "已停用数据源：${connection.name}"
                )
            }
        }
    }

    fun syncConnection(connection: OpdsConnection) {
        if (!connection.enabled) {
            _state.value = _state.value.copy(statusMessage = "请先启用数据源：${connection.name}")
            return
        }
        viewModelScope.launch {
            connectionRepository.updateSyncState(connection.id, OpdsSyncState.SYNCING, errorMessage = null)
            try {
                val feed = connectionRepository.browse(catalogService, connection)
                val bookCount = feed.entries.count { it.acquisitionLink != null }
                connectionRepository.updateSyncState(
                    id = connection.id,
                    syncState = OpdsSyncState.SUCCESS,
                    lastSyncedAt = System.currentTimeMillis(),
                    bookCount = bookCount,
                    errorMessage = null
                )
                _state.value = _state.value.copy(statusMessage = "同步完成：发现 $bookCount 本可下载书籍")
            } catch (e: Exception) {
                val message = e.message ?: "未知错误"
                connectionRepository.updateSyncState(
                    id = connection.id,
                    syncState = OpdsSyncState.FAILED,
                    errorMessage = message
                )
                _state.value = _state.value.copy(errorMessage = "同步失败：$message")
            }
        }
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
                connectionRepository.updateSyncState(
                    id = connection.id,
                    syncState = OpdsSyncState.FAILED,
                    errorMessage = e.message ?: "未知错误"
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "连接失败：${e.message}"
                )
            }
        }
    }

    fun browseLink(href: String) {
        val connection = _state.value.activeConnection ?: return
        browse(connection, href)
    }

    fun downloadEntry(entry: OpdsEntry) {
        val connection = _state.value.activeConnection ?: return
        val link = entry.acquisitionLink ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                downloadingTitle = entry.title,
                errorMessage = null,
                statusMessage = null
            )
            try {
                val bytes = catalogService.download(connection, link.href)
                val result = bookRepository.importDownloadedBook(
                    fileName = OpdsDownloadNamer.fileName(entry),
                    bytes = bytes,
                    fallbackTitle = entry.title
                )
                val message = when (result) {
                    is ImportResult.Added -> "已下载到书架：${result.book.title}"
                    is ImportResult.Duplicate -> "书架中已存在：${result.existingBook.title}"
                    is ImportResult.UnsupportedFormat -> "暂不支持该格式：${result.fileName}"
                    is ImportResult.Failed -> "下载失败：${result.message}"
                }
                _state.value = _state.value.copy(
                    downloadingTitle = null,
                    statusMessage = message
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    downloadingTitle = null,
                    errorMessage = "下载失败：${e.message}"
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

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                OpdsViewModel(
                    locator.opdsConnectionRepository,
                    locator.bookRepository,
                    locator.opdsCatalogService
                )
            }
        }
    }
}
