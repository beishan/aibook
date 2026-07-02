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
    val query: String = "",
    val importMessage: String = "支持 EPUB、TXT、PDF、Markdown、HTML",
    val isLoading: Boolean = false
) {
    val filteredBooks: List<LocalBook>
        get() = books.filter {
            query.isBlank() ||
                it.title.contains(query, ignoreCase = true) ||
                it.author?.contains(query, ignoreCase = true) == true
        }
}

class ShelfViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _importMessage = MutableStateFlow("支持 EPUB、TXT、PDF、Markdown、HTML")
    private val _isLoading = MutableStateFlow(false)

    val uiState: StateFlow<ShelfUiState> = combine(
        bookRepository.observeBooks(),
        _query,
        _importMessage,
        _isLoading
    ) { books, query, message, loading ->
        ShelfUiState(
            books = books,
            query = query,
            importMessage = message,
            isLoading = loading
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
