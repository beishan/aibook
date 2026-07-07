package com.aibook.android.feature.store

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.ImportResult
import com.aibook.android.core.data.repository.OpdsCatalogCacheRepository
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.OpdsConnectionRepository
import com.aibook.android.core.network.opds.OpdsCatalogService
import com.aibook.android.di.ServiceLocator
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
    private val opdsCatalogService: OpdsCatalogService
) : ViewModel() {

    private val _actionState = MutableStateFlow(StoreActionState())
    val actionState: StateFlow<StoreActionState> = _actionState.asStateFlow()

    private val _filter = MutableStateFlow(StoreCatalogFilter())
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
        _filter.update { it.copy(sourceId = sourceId) }
    }

    fun setFormatFilter(format: String?) {
        _filter.update { it.copy(format = format) }
    }

    fun setCategoryFilter(category: String?) {
        _filter.update { it.copy(category = category) }
    }

    fun setQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun setSort(sort: StoreSortOption) {
        _filter.update { it.copy(sort = sort) }
    }

    fun resetFilters() {
        _filter.value = StoreCatalogFilter()
    }

    fun clearMessage() {
        _actionState.update { it.copy(message = null) }
    }

    fun downloadRemoteBook(book: StoreBook) {
        if (book.kind != StoreItemKind.OPDS || book.acquisitionHref.isNullOrBlank()) return
        if (book.isDownloaded) {
            _actionState.value = StoreActionState(message = "已在书架中：${book.title}")
            return
        }

        viewModelScope.launch {
            _actionState.value = StoreActionState(downloadingBookId = book.id)
            val connection = opdsConnectionRepository.getById(book.sourceId)
            if (connection == null || !connection.enabled) {
                _actionState.value = StoreActionState(message = "数据源不可用：${book.sourceName}")
                return@launch
            }

            try {
                val bytes = opdsCatalogService.download(connection, book.acquisitionHref)
                val result = bookRepository.importDownloadedBook(
                    fileName = StoreDownloadNamer.fileName(book),
                    bytes = bytes,
                    fallbackTitle = book.title
                )
                val message = when (result) {
                    is ImportResult.Added -> "已下载到书架：${result.book.title}"
                    is ImportResult.Restored -> "已恢复到书城：${result.book.title}"
                    is ImportResult.Duplicate -> "书架中已存在：${result.existingBook.title}"
                    is ImportResult.UnsupportedFormat -> "暂不支持该格式：${result.fileName}"
                    is ImportResult.Failed -> "下载失败：${result.message}"
                }
                _actionState.value = StoreActionState(message = message)
            } catch (e: Exception) {
                _actionState.value = StoreActionState(message = "下载失败：${e.message ?: e::class.java.simpleName}")
            }
        }
    }

    fun removeLocalBookFromStore(book: StoreBook) {
        if (book.kind != StoreItemKind.LOCAL) return
        viewModelScope.launch {
            bookRepository.removeFromStore(book.id)
            _actionState.value = StoreActionState(message = "已从书城移出：${book.title}，文件未删除")
        }
    }

    fun addLocalBookToShelf(book: StoreBook) {
        if (book.kind != StoreItemKind.LOCAL || book.shelved) return
        viewModelScope.launch {
            bookRepository.setShelved(book.id, true)
            _actionState.value = StoreActionState(message = "已加入书架：${book.title}")
        }
    }

    fun removeLocalBooksFromStore(books: Collection<StoreBook>) {
        val localBooks = books.filter { it.kind == StoreItemKind.LOCAL }
        if (localBooks.isEmpty()) return
        viewModelScope.launch {
            localBooks.forEach { bookRepository.removeFromStore(it.id) }
            _actionState.value = StoreActionState(message = "已从书城移出 ${localBooks.size} 本本地书，文件未删除")
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
                    opdsCatalogService = locator.opdsCatalogService
                )
            }
        }
    }
}
