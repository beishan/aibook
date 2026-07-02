package com.aibook.android.feature.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.prefs.ReaderSettingsStore
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReaderSettings
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset

data class ReaderUiState(
    val book: LocalBook? = null,
    val content: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val scrollProgress: Float = 0f,
    val isRemote: Boolean = false,
    val remoteBookId: Long? = null,
    val settings: ReaderSettings = ReaderSettings()
)

class ReaderViewModel(
    private val bookRepository: BookRepository,
    private val readerSettingsStore: ReaderSettingsStore,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())

    val uiState: StateFlow<ReaderUiState> = combine(
        readerSettingsStore.fontScale,
        readerSettingsStore.lineHeight,
        readerSettingsStore.theme,
        _state
    ) { fontScale, lineHeight, theme, state ->
        state.copy(settings = ReaderSettings(fontScale, lineHeight, theme))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderUiState())

    fun loadLocalBook(bookId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, isRemote = false, remoteBookId = null)

            val book = bookRepository.getBook(bookId)
            if (book == null) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "书籍不存在")
                return@launch
            }

            _state.value = _state.value.copy(book = book, scrollProgress = book.progress.percent)

            try {
                val file = File(book.uri)
                if (!file.exists()) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "文件不存在")
                    return@launch
                }
                val content = readTxtFile(file)
                _state.value = _state.value.copy(content = content, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "读取失败：${e.message}")
            }
        }
    }

    fun loadRemoteBook(bookId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, isRemote = true, remoteBookId = bookId)
            try {
                val result = serverRepository.getProcessedContent(bookId)
                result.onSuccess { response ->
                    _state.value = _state.value.copy(content = response.text, scrollProgress = 0f, isLoading = false)
                }.onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "加载失败：${e.message}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "加载失败：${e.message}")
            }
        }
    }

    fun updateScrollProgress(percent: Float) {
        _state.value = _state.value.copy(scrollProgress = percent)
    }

    fun saveProgress() {
        val state = _state.value
        val book = state.book ?: return
        val percent = state.scrollProgress
        viewModelScope.launch {
            bookRepository.updateProgress(book.id, null, null, percent)
            if (state.isRemote && state.remoteBookId != null) {
                serverRepository.saveReadingProgress(
                    state.remoteBookId,
                    null,
                    (percent * 100).toInt(),
                    (percent * 100).toInt()
                )
            }
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { readerSettingsStore.setFontScale(value) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { readerSettingsStore.setLineHeight(value) }
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch { readerSettingsStore.setTheme(theme) }
    }

    private fun readTxtFile(file: File): String {
        val bytes = file.readBytes()
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (hasUtf8Bom(bytes) || looksValidUtf8(utf8)) utf8
        else String(bytes, Charset.forName("GBK"))
    }

    private fun hasUtf8Bom(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()

    private fun looksValidUtf8(text: String): Boolean {
        val replacementCount = text.count { it == '\uFFFD' }
        return replacementCount < text.length / 100
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                ReaderViewModel(locator.bookRepository, locator.readerSettingsStore, locator.serverRepository)
            }
        }
    }
}
