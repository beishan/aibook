package com.aibook.android.feature.shelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.ImportResult
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ShelfFolder
import com.aibook.android.core.model.ShelfFolderCatalog
import com.aibook.android.core.model.ShelfFolderSelection
import com.aibook.android.core.model.ShelfBookSorter
import com.aibook.android.core.model.ShelfSortOption
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShelfUiState(
    val books: List<LocalBook> = emptyList(),
    val folders: List<ShelfFolder> = emptyList(),
    val folderSelection: ShelfFolderSelection = ShelfFolderSelection.All,
    val folderCounts: Map<String, Int> = emptyMap(),
    val query: String = "",
    val importMessage: String = "支持 EPUB、TXT、PDF、Markdown、HTML",
    val isLoading: Boolean = false,
    val sortOption: ShelfSortOption = ShelfSortOption.RECENT_READ,
    val managementMode: Boolean = false,
    val selectedIds: Set<String> = emptySet()
) {
    val filteredBooks: List<LocalBook>
        get() = ShelfBookSorter.sort(
            ShelfFolderCatalog.filterBooks(books, folderSelection).filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.author?.contains(query, ignoreCase = true) == true
            },
            sortOption
        )

    val selectedBooks: List<LocalBook>
        get() = books.filter { it.id in selectedIds }
}

private data class ShelfControlState(
    val query: String,
    val importMessage: String,
    val isLoading: Boolean,
    val sortOption: ShelfSortOption,
    val managementMode: Boolean,
    val folderSelection: ShelfFolderSelection
)

class ShelfViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _importMessage = MutableStateFlow("支持 EPUB、TXT、PDF、Markdown、HTML")
    private val _isLoading = MutableStateFlow(false)
    private val _sortOption = MutableStateFlow(ShelfSortOption.RECENT_READ)
    private val _managementMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _folderSelection = MutableStateFlow<ShelfFolderSelection>(ShelfFolderSelection.All)

    private val baseControls = combine(
        _query,
        _importMessage,
        _isLoading,
        _sortOption,
        _managementMode,
    ) { query, message, loading, sortOption, managementMode ->
        ShelfControlState(
            query = query,
            importMessage = message,
            isLoading = loading,
            sortOption = sortOption,
            managementMode = managementMode,
            folderSelection = ShelfFolderSelection.All
        )
    }

    private val controls = combine(baseControls, _folderSelection) { controls, folderSelection ->
        controls.copy(folderSelection = folderSelection)
    }

    val uiState: StateFlow<ShelfUiState> = combine(
        bookRepository.observeShelvedBooks(),
        bookRepository.observeShelfFolders(),
        controls,
        _selectedIds
    ) { books, folders, controls, selectedIds ->
        val visibleIds = books.map { it.id }.toSet()
        val validSelection = when (val selection = controls.folderSelection) {
            is ShelfFolderSelection.Folder -> if (folders.any { it.id == selection.folderId }) selection else ShelfFolderSelection.All
            else -> selection
        }
        ShelfUiState(
            books = books,
            folders = folders,
            folderSelection = validSelection,
            folderCounts = ShelfFolderCatalog.folderCounts(books),
            query = controls.query,
            importMessage = controls.importMessage,
            isLoading = controls.isLoading,
            sortOption = controls.sortOption,
            managementMode = controls.managementMode,
            selectedIds = selectedIds.intersect(visibleIds)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShelfUiState())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun importBook(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = bookRepository.importBook(uri, fileName)
            _importMessage.value = when (result) {
                is ImportResult.Added -> "已加入书架：${result.book.title}"
                is ImportResult.Restored -> "已恢复到书城：${result.book.title}"
                is ImportResult.Duplicate -> "书架中已存在：${result.existingBook.title}"
                is ImportResult.UnsupportedFormat -> "当前文件格式暂不支持：${result.fileName}"
                is ImportResult.Failed -> "导入失败：${result.message}"
            }
            _isLoading.value = false
        }
    }

    fun setFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { bookRepository.setFavorite(id, favorite) }
    }

    fun cycleSortOption() {
        val options = ShelfSortOption.entries
        val currentIndex = options.indexOf(_sortOption.value).takeIf { it >= 0 } ?: 0
        _sortOption.value = options[(currentIndex + 1) % options.size]
    }

    fun setManagementMode(enabled: Boolean) {
        _managementMode.value = enabled
        if (!enabled) {
            _selectedIds.value = emptySet()
        }
    }

    fun toggleBookSelection(id: String) {
        _selectedIds.update { selected ->
            if (id in selected) selected - id else selected + id
        }
    }

    fun selectAllVisible() {
        _selectedIds.value = uiState.value.filteredBooks.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectFolder(selection: ShelfFolderSelection) {
        _folderSelection.value = selection
        _selectedIds.value = emptySet()
    }

    fun setSelectedFavorite(favorite: Boolean) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> bookRepository.setFavorite(id, favorite) }
        }
    }

    fun removeSelectedFromShelf() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> bookRepository.setShelved(id, false) }
            _selectedIds.value = emptySet()
            _managementMode.value = false
        }
    }

    fun createFolderAndMoveSelected(name: String) {
        val ids = _selectedIds.value
        val trimmed = name.trim()
        if (ids.isEmpty() || trimmed.isBlank()) return
        viewModelScope.launch {
            val folder = bookRepository.createShelfFolder(trimmed)
            bookRepository.moveBooksToFolder(ids, folder.id)
            _folderSelection.value = ShelfFolderSelection.Folder(folder.id)
            _selectedIds.value = emptySet()
            _managementMode.value = false
        }
    }

    fun moveSelectedToFolder(folderId: String?) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookRepository.moveBooksToFolder(ids, folderId)
            _folderSelection.value = folderId?.let { ShelfFolderSelection.Folder(it) } ?: ShelfFolderSelection.Unfiled
            _selectedIds.value = emptySet()
            _managementMode.value = false
        }
    }

    fun toggleShelved(id: String, shelved: Boolean) {
        viewModelScope.launch { bookRepository.setShelved(id, shelved) }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch { bookRepository.deleteBook(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                ShelfViewModel(ServiceLocator.get(app).bookRepository)
            }
        }
    }
}
