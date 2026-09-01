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
    ALL("全部"),
    FAVORITES("收藏"),
    SHELF("书架"),
    LISTS("书单")
}

data class ServerLibraryUiState(
    val section: ServerLibrarySection = ServerLibrarySection.ALL,
    val books: List<BookDTO> = emptyList(),
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

    init {
        viewModelScope.launch {
            val loggedIn = serverRepository.isLoggedIn.first()
            _uiState.update { it.copy(isLoggedIn = loggedIn) }
            if (loggedIn) refresh() else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "请先在设置中连接并登录后端服务")
                }
            }
        }
    }

    fun selectSection(section: ServerLibrarySection) {
        if (_uiState.value.section == section) return
        _uiState.update { it.copy(section = section, actionMessage = null) }
        if (_uiState.value.isLoggedIn) refresh()
    }

    fun selectBookList(listId: Long) {
        _uiState.update { it.copy(selectedListId = listId) }
        refresh()
    }

    fun refresh() {
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
        viewModelScope.launch {
            val alreadyOnShelf = id in _uiState.value.shelfBookIds
            val result = if (alreadyOnShelf) {
                serverRepository.removeFromShelf(id)
            } else {
                serverRepository.addToShelf(id)
            }
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(actionMessage = if (alreadyOnShelf) "已移出服务端书架" else "已加入服务端书架")
                }
                refresh()
            }.onFailure(::showActionError)
        }
    }

    fun toggleFavorite(book: BookDTO) {
        val id = book.id ?: return
        viewModelScope.launch {
            serverRepository.toggleFavorite(id)
                .onSuccess {
                    _uiState.update { state -> state.copy(actionMessage = "收藏状态已更新") }
                    refresh()
                }
                .onFailure(::showActionError)
        }
    }

    fun coverUrl(book: BookDTO): String? = serverRepository.resolveCoverUrl(book.coverUrl)

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
