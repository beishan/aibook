package com.aibook.android.feature.server

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.core.network.api.dto.BookDTO
import com.aibook.android.core.network.api.dto.BookListDTO
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ServerLibrarySection(val label: String) {
    ALL("书籍列表"),
    FAVORITES("收藏"),
    SHELF("书架"),
    LISTS("书单")
}

data class ServerLibraryUiState(
    val section: ServerLibrarySection = ServerLibrarySection.ALL,
    val books: List<BookDTO> = emptyList(),
    val overviewBooks: List<BookDTO> = emptyList(),
    val favoritePreview: List<BookDTO> = emptyList(),
    val shelfBookIds: Set<Long> = emptySet(),
    val bookLists: List<BookListDTO> = emptyList(),
    val selectedListId: Long? = null,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

class ServerLibraryViewModel(
    private val serverRepository: ServerRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ServerLibraryUiState())
    val uiState: StateFlow<ServerLibraryUiState> = _uiState.asStateFlow()

    private fun verifyLogin(onLoggedIn: () -> Unit) {
        if (CloudMockData.enabled) {
            _uiState.update { it.copy(isLoggedIn = true, errorMessage = null) }
            onLoggedIn()
            return
        }
        viewModelScope.launch {
            val loggedIn = serverRepository.isLoggedIn.first()
            _uiState.update { it.copy(isLoggedIn = loggedIn) }
            if (loggedIn) onLoggedIn() else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "请先在设置中连接并登录云端")
                }
            }
        }
    }

    fun selectSection(section: ServerLibrarySection) {
        _uiState.update { it.copy(section = section, actionMessage = null) }
        verifyLogin(::refresh)
    }

    fun loadOverview() {
        if (CloudMockData.enabled) {
            _uiState.update {
                it.copy(
                    overviewBooks = CloudMockData.books.take(4),
                    favoritePreview = CloudMockData.favorites().take(4),
                    bookLists = CloudMockData.bookLists,
                    shelfBookIds = CloudMockData.shelfBookIds,
                    isLoggedIn = true,
                    isLoading = false,
                    errorMessage = null,
                    actionMessage = "当前展示 Mock 演示数据"
                )
            }
            return
        }
        viewModelScope.launch {
            val loggedIn = serverRepository.isLoggedIn.first()
            if (!loggedIn) {
                _uiState.update {
                    it.copy(isLoggedIn = false, isLoading = false, errorMessage = "请先在设置中连接并登录云端")
                }
                return@launch
            }
            _uiState.update { it.copy(isLoggedIn = true, isLoading = true, errorMessage = null) }
            val shelfResult = serverRepository.getShelf()
            val allResult = serverRepository.getBooks(size = 6)
            val favoriteResult = serverRepository.getFavoriteBooks()
            val listResult = serverRepository.getBookLists()
            val firstError = listOf(
                shelfResult.exceptionOrNull(),
                allResult.exceptionOrNull(),
                favoriteResult.exceptionOrNull(),
                listResult.exceptionOrNull()
            ).firstOrNull { it != null }
            if (firstError != null && allResult.isFailure && favoriteResult.isFailure && listResult.isFailure) {
                _uiState.update { it.copy(isLoading = false, errorMessage = firstError.readableMessage()) }
                return@launch
            }
            val shelfIds = shelfResult.getOrNull()
                ?.let { shelf -> shelf.ungroupedBooks + shelf.groups.flatMap { it.books } }
                ?.mapNotNull { it.id }
                ?.toSet()
                .orEmpty()
            _uiState.update {
                it.copy(
                    overviewBooks = allResult.getOrNull()?.content.orEmpty().take(4),
                    favoritePreview = favoriteResult.getOrNull()?.content.orEmpty().take(4),
                    bookLists = listResult.getOrNull().orEmpty().take(4),
                    shelfBookIds = shelfIds,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    fun selectBookList(listId: Long) {
        _uiState.update { it.copy(selectedListId = listId) }
        if (CloudMockData.enabled) {
            val list = CloudMockData.bookList(listId)
            _uiState.update {
                it.copy(
                    bookLists = CloudMockData.bookLists,
                    books = list?.books.orEmpty(),
                    shelfBookIds = CloudMockData.shelfBookIds,
                    isLoading = false,
                    errorMessage = null
                )
            }
            return
        }
        refresh()
    }

    fun refresh() {
        if (CloudMockData.enabled) {
            val state = _uiState.value
            val selectedList = CloudMockData.bookList(state.selectedListId ?: CloudMockData.bookLists.first().id)
            val books = when (state.section) {
                ServerLibrarySection.ALL -> CloudMockData.books
                ServerLibrarySection.FAVORITES -> CloudMockData.favorites()
                ServerLibrarySection.SHELF -> CloudMockData.books.filter { it.id in CloudMockData.shelfBookIds }
                ServerLibrarySection.LISTS -> selectedList?.books.orEmpty()
            }
            _uiState.update {
                it.copy(
                    books = books,
                    overviewBooks = CloudMockData.books.take(4),
                    favoritePreview = CloudMockData.favorites().take(4),
                    bookLists = CloudMockData.bookLists,
                    selectedListId = if (state.section == ServerLibrarySection.LISTS) selectedList?.id else state.selectedListId,
                    shelfBookIds = CloudMockData.shelfBookIds,
                    isLoggedIn = true,
                    isLoading = false,
                    errorMessage = null,
                    actionMessage = "当前展示 Mock 演示数据"
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val shelfResult = serverRepository.getShelf()
            val shelfIds = shelfResult.getOrNull()
                ?.let { shelf -> shelf.ungroupedBooks + shelf.groups.flatMap { it.books } }
                ?.mapNotNull { it.id }
                ?.toSet()
                .orEmpty()

            when (_uiState.value.section) {
                ServerLibrarySection.ALL -> loadBooks(
                    serverRepository.getBooks(size = 100).map { it.content },
                    shelfIds
                )
                ServerLibrarySection.FAVORITES -> loadBooks(
                    serverRepository.getFavoriteBooks().map { it.content },
                    shelfIds
                )
                ServerLibrarySection.SHELF -> loadBooks(
                    shelfResult.map { shelf ->
                        (shelf.ungroupedBooks + shelf.groups.flatMap { it.books })
                            .distinctBy { it.id }
                    },
                    shelfIds
                )
                ServerLibrarySection.LISTS -> loadBookLists(shelfIds)
            }
        }
    }

    fun toggleShelf(book: BookDTO) {
        val id = book.id ?: return
        if (CloudMockData.enabled) {
            _uiState.update { state ->
                val alreadyOnShelf = id in state.shelfBookIds
                state.copy(
                    shelfBookIds = if (alreadyOnShelf) state.shelfBookIds - id else state.shelfBookIds + id,
                    actionMessage = if (alreadyOnShelf) "已从 Mock 书架移出" else "已加入 Mock 书架"
                )
            }
            return
        }
        viewModelScope.launch {
            val alreadyOnShelf = id in _uiState.value.shelfBookIds
            val result = if (alreadyOnShelf) {
                serverRepository.removeFromShelf(id)
            } else {
                serverRepository.addToShelf(id)
            }
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(actionMessage = if (alreadyOnShelf) "已移出云端书架" else "已加入云端书架")
                }
                refresh()
            }.onFailure(::showActionError)
        }
    }

    fun toggleFavorite(book: BookDTO) {
        val id = book.id ?: return
        if (CloudMockData.enabled) {
            val favorite = book.isFavorite != true
            _uiState.update { state ->
                val updateBook: (BookDTO) -> BookDTO = { item -> if (item.id == id) item.copy(isFavorite = favorite) else item }
                state.copy(
                    books = state.books.map(updateBook).filter { state.section != ServerLibrarySection.FAVORITES || it.isFavorite == true },
                    overviewBooks = state.overviewBooks.map(updateBook),
                    favoritePreview = if (favorite) (state.favoritePreview + book.copy(isFavorite = true)).distinctBy { it.id }.take(4)
                    else state.favoritePreview.filterNot { it.id == id },
                    bookLists = state.bookLists.map { list -> list.copy(books = list.books.map(updateBook)) },
                    actionMessage = if (favorite) "已加入 Mock 收藏" else "已取消 Mock 收藏"
                )
            }
            return
        }
        viewModelScope.launch {
            serverRepository.toggleFavorite(id)
                .onSuccess {
                    _uiState.update { state -> state.copy(actionMessage = "收藏状态已更新") }
                    refresh()
                }
                .onFailure(::showActionError)
        }
    }

    fun coverUrl(book: BookDTO): String? = if (CloudMockData.enabled) book.coverUrl else serverRepository.resolveCoverUrl(book.coverUrl)

    fun clearMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun loadBooks(result: Result<List<BookDTO>>, shelfIds: Set<Long>) {
        result.onSuccess { books ->
            _uiState.update {
                it.copy(books = books, shelfBookIds = shelfIds, isLoading = false, errorMessage = null)
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(isLoading = false, shelfBookIds = shelfIds, errorMessage = error.readableMessage())
            }
        }
    }

    private suspend fun loadBookLists(shelfIds: Set<Long>) {
        serverRepository.getBookLists().onSuccess { lists ->
            val selectedId = _uiState.value.selectedListId?.takeIf { id -> lists.any { it.id == id } }
                ?: lists.firstOrNull()?.id
            if (selectedId == null) {
                _uiState.update {
                    it.copy(bookLists = lists, selectedListId = null, books = emptyList(), shelfBookIds = shelfIds, isLoading = false)
                }
                return@onSuccess
            }
            serverRepository.getBookList(selectedId).onSuccess { list ->
                _uiState.update {
                    it.copy(
                        bookLists = lists,
                        selectedListId = selectedId,
                        books = list.books,
                        shelfBookIds = shelfIds,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.readableMessage()) }
            }
        }.onFailure { error ->
            _uiState.update { it.copy(isLoading = false, errorMessage = error.readableMessage()) }
        }
    }

    private fun showActionError(error: Throwable) {
        _uiState.update { it.copy(actionMessage = "操作失败：${error.readableMessage()}") }
    }

    private fun Throwable.readableMessage(): String = message ?: "服务暂时不可用"

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ServerLibraryViewModel(ServiceLocator.get(app).serverRepository)
            }
        }
    }
}
