package com.aibook.android.feature.store

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.OpdsCatalogCacheRepository
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.di.ServiceLocator
import com.aibook.android.core.data.repository.DownloadTask
import com.aibook.android.core.data.repository.DownloadTaskRepository
import com.aibook.android.core.data.repository.DownloadStatus
import com.aibook.android.background.DownloadQueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreActionState(
    val downloadingBookId: String? = null,
    val downloadTasks: Map<String, DownloadTask> = emptyMap(),
    val message: String? = null
)

data class StoreUiState(
    val books: List<StoreBook> = emptyList(),
    val filteredBooks: List<StoreBook> = emptyList(),
    val filter: StoreCatalogFilter = StoreCatalogFilter(),
    val options: StoreCatalogOptions = StoreCatalogOptions()
)

class StoreViewModel(
    private val bookRepository: BookRepository,
    private val opdsConnectionRepository: OpdsConnectionRepository,
    private val opdsCatalogCacheRepository: OpdsCatalogCacheRepository,
    private val downloadTaskRepository: DownloadTaskRepository,
    private val downloadQueueManager: DownloadQueueManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _actionState = MutableStateFlow(StoreActionState())
    val actionState: StateFlow<StoreActionState> = _actionState.asStateFlow()

    init {
        viewModelScope.launch {
            downloadTaskRepository.observeAll().collect { tasks ->
                val latest = tasks.distinctBy { it.remoteEntryId }.associateBy { it.remoteEntryId }
                val active = tasks.firstOrNull { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
                _actionState.update { it.copy(downloadingBookId = active?.remoteEntryId, downloadTasks = latest) }
            }
        }
    }

    private val _filter = MutableStateFlow(
        StoreCatalogFilter(
            sourceId = savedStateHandle["store.source"],
            format = savedStateHandle["store.format"],
            category = savedStateHandle["store.category"],
            query = savedStateHandle["store.query"] ?: "",
            sort = savedStateHandle.get<String>("store.sort")
                ?.let { saved -> StoreSortOption.entries.firstOrNull { it.name == saved } }
                ?: StoreSortOption.RECENT
        )
    )
    val filter: StateFlow<StoreCatalogFilter> = _filter.asStateFlow()

    val books: StateFlow<List<StoreBook>> = combine(
        bookRepository.observeBooks(),
        opdsConnectionRepository.observeConnections(),
        opdsCatalogCacheRepository.observeEntries()
    ) { localBooks, connections, opdsEntries ->
        StoreCatalog.aggregate(
            localBooks = localBooks.map {
                StoreCatalog.LocalInput(
                    id = it.id,
                    title = it.title,
                    author = it.author,
                    format = it.format.displayName,
                    importedAtEpochSeconds = (it.lastReadAt ?: it.importedAt).epochSecond,
                    visibleInStore = it.visibleInStore,
                    coverUri = it.coverUri,
                    shelved = it.shelved
                )
            },
            opdsEntries = opdsEntries.map {
                StoreCatalog.OpdsInput(
                    id = it.id,
                    connectionId = it.connectionId,
                    sourceName = it.sourceName,
                    title = it.title,
                    author = it.author,
                    summary = it.summary,
                    format = it.format,
                    categories = it.categories,
                    syncedAt = it.syncedAt / 1000,
                    acquisitionHref = it.acquisitionHref,
                    acquisitionType = it.acquisitionType
                )
            },
            enabledConnectionIds = connections.filter { it.enabled }.map { it.id }.toSet()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<StoreUiState> = combine(books, filter) { books, filter ->
        StoreUiState(
            books = books,
            filteredBooks = StoreCatalog.filterAndSort(books, filter),
            filter = filter,
            options = StoreCatalog.optionsFor(books)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StoreUiState())

    fun setSourceFilter(sourceId: String?) {
        savedStateHandle["store.source"] = sourceId
        _filter.update { it.copy(sourceId = sourceId) }
    }

    fun setFormatFilter(format: String?) {
        savedStateHandle["store.format"] = format
        _filter.update { it.copy(format = format) }
    }

    fun setCategoryFilter(category: String?) {
        savedStateHandle["store.category"] = category
        _filter.update { it.copy(category = category) }
    }

    fun setQuery(query: String) {
        savedStateHandle["store.query"] = query
        _filter.update { it.copy(query = query) }
    }

    fun setSort(sort: StoreSortOption) {
        savedStateHandle["store.sort"] = sort.name
        _filter.update { it.copy(sort = sort) }
    }

    fun resetFilters() {
        listOf("store.source", "store.format", "store.category", "store.query", "store.sort")
            .forEach { key -> savedStateHandle.remove<Any>(key) }
        _filter.value = StoreCatalogFilter()
    }

    fun clearMessage() {
        _actionState.update { it.copy(message = null) }
    }

    fun downloadRemoteBook(book: StoreBook) {
        if (book.kind != StoreItemKind.OPDS || book.acquisitionHref.isNullOrBlank()) return
        if (book.isDownloaded) {
            _actionState.update { it.copy(message = "已在书架中：${book.title}") }
            return
        }

        viewModelScope.launch {
            val connection = opdsConnectionRepository.getById(book.sourceId)
            if (connection == null || !connection.enabled) {
                _actionState.update { it.copy(message = "数据源不可用：${book.sourceName}") }
                return@launch
            }
            downloadQueueManager.enqueue(book.id, connection.id, book.title, book.acquisitionHref, StoreDownloadNamer.fileName(book))
            _actionState.update { it.copy(message = "已加入下载队列：${book.title}") }
        }
    }

    fun pauseDownload(remoteEntryId: String) = controlDownload(remoteEntryId, downloadQueueManager::pause)
    fun resumeDownload(remoteEntryId: String) = controlDownload(remoteEntryId, downloadQueueManager::resume)
    fun cancelDownload(remoteEntryId: String) = controlDownload(remoteEntryId, downloadQueueManager::cancel)
    fun retryDownload(remoteEntryId: String) = controlDownload(remoteEntryId, downloadQueueManager::retry)

    private fun controlDownload(remoteEntryId: String, action: suspend (String) -> Unit) {
        val task = _actionState.value.downloadTasks[remoteEntryId] ?: return
        viewModelScope.launch { action(task.id) }
    }

    fun removeLocalBookFromStore(book: StoreBook) {
        if (book.kind != StoreItemKind.LOCAL) return
        viewModelScope.launch {
            bookRepository.removeFromStore(book.id)
            _actionState.update { it.copy(message = "已从书城移出：${book.title}，文件未删除") }
        }
    }

    fun addLocalBookToShelf(book: StoreBook) {
        if (book.kind != StoreItemKind.LOCAL || book.shelved) return
        viewModelScope.launch {
            bookRepository.setShelved(book.id, true)
        }
    }

    fun removeLocalBookFromShelf(book: StoreBook) {
        if (book.kind != StoreItemKind.LOCAL || !book.shelved) return
        viewModelScope.launch {
            bookRepository.setShelved(book.id, false)
        }
    }

    fun removeLocalBooksFromStore(books: Collection<StoreBook>) {
        val localBooks = books.filter { it.kind == StoreItemKind.LOCAL }
        if (localBooks.isEmpty()) return
        viewModelScope.launch {
            localBooks.forEach { bookRepository.removeFromStore(it.id) }
            _actionState.update { it.copy(message = "已从书城移出 ${localBooks.size} 本本地书，文件未删除") }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                StoreViewModel(
                    bookRepository = locator.bookRepository,
                    opdsConnectionRepository = locator.opdsConnectionRepository,
                    opdsCatalogCacheRepository = locator.opdsCatalogCacheRepository,
                    downloadTaskRepository = locator.downloadTaskRepository,
                    downloadQueueManager = DownloadQueueManager(app),
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
