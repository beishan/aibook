package com.aibook.android.feature.opds

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.OpdsCatalogCacheRepository
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.core.network.opds.OpdsConnection
import com.aibook.android.core.network.opds.OpdsDownloadNamer
import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsFeed
import com.aibook.android.core.network.opds.OpdsSyncState
import com.aibook.android.core.network.opds.OpdsSyncMode
import com.aibook.android.di.ServiceLocator
import com.aibook.android.core.data.prefs.BackgroundTaskStore
import com.aibook.android.background.BackgroundWorkScheduler
import com.aibook.android.background.DownloadQueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OpdsUiState(
    val connections: List<OpdsConnection> = emptyList(),
    val activeConnection: OpdsConnection? = null,
    val currentFeed: OpdsFeed? = null,
    val navigationStack: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val downloadingTitle: String? = null,
    val errorDialogConnection: OpdsConnection? = null,
    val showConnectionForm: Boolean = false,
    val editingConnectionId: String? = null,
    val formName: String = "汗牛充栋",
    val formBaseUrl: String = "",
    val formUsername: String = "",
    val formPassword: String = "",
    val opdsIntervalHours: Int = 0
)

class OpdsViewModel(
    private val app: Application,
    private val connectionRepository: OpdsConnectionRepository,
    private val catalogCacheRepository: OpdsCatalogCacheRepository,
    private val catalogService: OpdsCatalogService,
    private val backgroundTaskStore: BackgroundTaskStore,
    private val downloadQueueManager: DownloadQueueManager
) : ViewModel() {

    private val _state = MutableStateFlow(OpdsUiState())
    val uiState: StateFlow<OpdsUiState> = connectionRepository.observeConnections()
        .let { connectionsFlow ->
            combine(connectionsFlow, _state) { connections, state ->
                state.copy(connections = connections)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OpdsUiState())

    init {
        viewModelScope.launch {
            backgroundTaskStore.opdsIntervalHours.collect { hours ->
                _state.value = _state.value.copy(opdsIntervalHours = hours)
            }
        }
    }

    fun setOpdsIntervalHours(hours: Int) {
        viewModelScope.launch {
            backgroundTaskStore.setOpdsIntervalHours(hours)
            BackgroundWorkScheduler.configureOpds(app, hours, ServiceLocator.get(app).serverConfigStore.wifiOnlySync.first())
        }
    }

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
            val connection = try {
                val candidate = OpdsConnection(
                    id = state.editingConnectionId ?: "connection-test",
                    name = state.formName,
                    baseUrl = state.formBaseUrl,
                    username = state.formUsername.ifBlank { null },
                    password = state.formPassword.ifBlank { null }
                )
                withContext(Dispatchers.IO) { catalogService.load(candidate) }
                connectionRepository.saveConnection(
                    name = state.formName,
                    baseUrl = state.formBaseUrl,
                    username = state.formUsername.ifBlank { null },
                    password = state.formPassword.ifBlank { null },
                    id = state.editingConnectionId
                )
            } catch (error: Exception) {
                _state.value = state.copy(errorMessage = "连接测试失败，未保存：${error.message ?: "无法读取目录"}")
                return@launch
            }
            val wasEditing = state.editingConnectionId != null
            _state.value = _state.value.copy(
                showConnectionForm = false,
                editingConnectionId = null,
                formName = "汗牛充栋",
                formBaseUrl = "",
                formUsername = "",
                formPassword = "",
                statusMessage = if (wasEditing) "已保存修改：${connection.name}" else "已保存数据源：${connection.name}"
            )
            if (browseAfterSave && !wasEditing) {
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

    fun loadConnectionForEdit(connectionId: String) {
        viewModelScope.launch {
            val connection = connectionRepository.getById(connectionId) ?: return@launch
            _state.value = _state.value.copy(
                editingConnectionId = connection.id,
                formName = connection.name,
                formBaseUrl = connection.baseUrl,
                formUsername = connection.username.orEmpty(),
                formPassword = connection.password.orEmpty()
            )
        }
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
            connectionRepository.markSyncing(connection.id)
            try {
                val collection = OpdsSyncCollector { href ->
                    connectionRepository.browse(catalogService, connection, href)
                }.collect()
                val feed = OpdsFeed(
                    title = connection.name,
                    entries = collection.acquisitionEntries
                )
                val discoveredCount = collection.acquisitionEntries.size
                val categoryCount = collection.catalogCount
                val syncSummary = if (connection.syncMode == OpdsSyncMode.INCREMENTAL) {
                    val result = catalogCacheRepository.mergeConnectionEntries(connection, feed)
                    "新增 ${result.added} 本，更新 ${result.updated} 本"
                } else {
                    catalogCacheRepository.replaceConnectionEntries(connection, feed)
                    "更新 $discoveredCount 本"
                }
                val bookCount = catalogCacheRepository.countByConnection(connection.id)
                connectionRepository.markSyncSuccess(
                    connection.id,
                    lastSyncedAt = System.currentTimeMillis(),
                    bookCount = bookCount
                )
                _state.value = _state.value.copy(statusMessage = "同步完成：$syncSummary，共 $bookCount 本，$categoryCount 个目录")
            } catch (e: Exception) {
                val message = e.message ?: "未知错误"
                connectionRepository.markSyncFailed(connection.id, message)
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
                connectionRepository.markSyncFailed(connection.id, e.message ?: "未知错误")
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

    fun loadNextPage() {
        val state = _state.value
        val connection = state.activeConnection ?: return
        val current = state.currentFeed ?: return
        val next = current.nextLink ?: return
        if (state.isLoading || state.isLoadingNextPage) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingNextPage = true)
            try {
                val page = connectionRepository.browse(catalogService, connection, next.href)
                val known = current.entries.mapNotNull { it.acquisitionLink?.href ?: it.alternateLink?.href }.toSet()
                _state.value = _state.value.copy(
                    currentFeed = current.copy(
                        entries = current.entries + page.entries.filter {
                            (it.acquisitionLink?.href ?: it.alternateLink?.href) !in known
                        },
                        nextLink = page.nextLink
                    ),
                    isLoadingNextPage = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingNextPage = false,
                    errorMessage = "加载下一页失败：${e.message ?: "未知错误"}"
                )
            }
        }
    }

    fun downloadEntry(entry: OpdsEntry) {
        val connection = _state.value.activeConnection ?: return
        val link = entry.acquisitionLink ?: return

        viewModelScope.launch {
            downloadQueueManager.enqueue(
                remoteId = entry.identifier ?: "${connection.id}|${link.href}",
                connectionId = connection.id,
                title = entry.title,
                href = link.href,
                fileName = OpdsDownloadNamer.fileName(entry)
            )
            _state.value = _state.value.copy(statusMessage = "已加入后台下载队列：${entry.title}")
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
            catalogCacheRepository.deleteByConnection(id)
            if (_state.value.activeConnection?.id == id) {
                _state.value = _state.value.copy(
                    activeConnection = null,
                    currentFeed = null,
                    navigationStack = emptyList()
                )
            }
        }
    }

    fun showErrorDetails(connection: OpdsConnection) {
        _state.value = _state.value.copy(errorDialogConnection = connection)
    }

    fun dismissErrorDetails() {
        _state.value = _state.value.copy(errorDialogConnection = null)
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
                    app,
                    locator.opdsConnectionRepository,
                    locator.opdsCatalogCacheRepository,
                    locator.opdsCatalogService,
                    locator.backgroundTaskStore,
                    DownloadQueueManager(app)
                )
            }
        }
    }
}
