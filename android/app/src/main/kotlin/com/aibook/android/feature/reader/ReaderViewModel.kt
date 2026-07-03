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
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.PageTurnMode
import com.aibook.android.core.model.ParagraphSpacing
import com.aibook.android.core.model.ReaderSettings
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.core.model.TextAlignment
import com.aibook.android.core.reader.EpubBookContent
import com.aibook.android.core.reader.EpubContentParser
import com.aibook.android.core.reader.ReaderChapter
import com.aibook.android.core.reader.ReaderChapterSelection
import com.aibook.android.core.reader.TextChapterParser
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.Charset

data class ReaderUiState(
    val book: LocalBook? = null,
    val content: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val scrollProgress: Float = 0f,
    val chapters: List<ReaderChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val isRemote: Boolean = false,
    val remoteBookId: Long? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val isBookSpecific: Boolean = false,
    val hasSettingsDraft: Boolean = false
)

class ReaderViewModel(
    private val appContext: android.content.Context,
    private val bookRepository: BookRepository,
    private val readerSettingsStore: ReaderSettingsStore,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    private val loadingChapterIndexes = mutableSetOf<Int>()
    private var pendingChapterIndex: Int? = null
    private var settingsSnapshot: ReaderSettings? = null

    val uiState: StateFlow<ReaderUiState> = _state
        .asStateFlow()

    init {
        viewModelScope.launch { readerSettingsStore.fontScale.collect { v -> _state.update { it.copy(settings = it.settings.copy(fontScale = v)) } } }
        viewModelScope.launch { readerSettingsStore.lineHeight.collect { v -> _state.update { it.copy(settings = it.settings.copy(lineHeight = v)) } } }
        viewModelScope.launch { readerSettingsStore.theme.collect { v -> _state.update { it.copy(settings = it.settings.copy(theme = v)) } } }
        viewModelScope.launch { readerSettingsStore.paragraphSpacing.collect { v -> _state.update { it.copy(settings = it.settings.copy(paragraphSpacing = v)) } } }
        viewModelScope.launch { readerSettingsStore.textAlignment.collect { v -> _state.update { it.copy(settings = it.settings.copy(textAlignment = v)) } } }
        viewModelScope.launch { readerSettingsStore.pageTurnMode.collect { v -> _state.update { it.copy(settings = it.settings.copy(pageTurnMode = v)) } } }
        viewModelScope.launch { readerSettingsStore.autoBrightness.collect { v -> _state.update { it.copy(settings = it.settings.copy(autoBrightness = v)) } } }
        viewModelScope.launch { readerSettingsStore.screenAlwaysOn.collect { v -> _state.update { it.copy(settings = it.settings.copy(screenAlwaysOn = v)) } } }
    }

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
                when (book.format) {
                    BookFormat.EPUB -> {
                        var epub = runCatching {
                            ReadiumEpubReader(appContext).parse(file, book.progress.chapterHref)
                        }.getOrNull()

                        // Readium 返回空或初始章节内容为空时，降级到 EpubContentParser
                        if (epub == null || epub.chapters.isEmpty() ||
                            epub.chapters.firstOrNull { it.content.isNotBlank() } == null
                        ) {
                            epub = runCatching {
                                EpubContentParser.parse(file.readBytes())
                            }.getOrNull() ?: epub ?: EpubBookContent()
                        }

                        val chapters = epub.chapters
                        val fallback = if (chapters.isEmpty()) {
                            epub.fullText.ifBlank { readTxtFile(file) }
                        } else null
                        applyChapters(chapters, fallbackText = fallback)
                        if (chapters.isNotEmpty()) prefetchNextEpubChapter()
                    }
                    BookFormat.PDF -> _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "PDF 阅读器正在开发中，当前请先导入 EPUB 或 TXT 阅读"
                    )
                    BookFormat.TXT,
                    BookFormat.MARKDOWN,
                    BookFormat.HTML,
                    BookFormat.HTM -> applyContent(readTxtFile(file))
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "读取失败：${e.message ?: e::class.java.simpleName}"
                )
            }
        }
    }

    fun loadRemoteBook(bookId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, isRemote = true, remoteBookId = bookId)
            try {
                val result = serverRepository.getProcessedContent(bookId)
                result.onSuccess { response ->
                    applyContent(response.text)
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

    fun toggleFavorite() {
        val book = _state.value.book ?: return
        viewModelScope.launch {
            val newFavorite = !book.favorite
            bookRepository.setFavorite(book.id, newFavorite)
            _state.update { it.copy(book = it.book?.copy(favorite = newFavorite)) }
        }
    }

    fun selectChapter(index: Int) {
        val chapters = _state.value.chapters
        val chapter = chapters.getOrNull(index) ?: return
        val book = _state.value.book
        if (book?.format == BookFormat.EPUB && chapter.content.isBlank()) {
            pendingChapterIndex = index
            if (!loadingChapterIndexes.add(index)) return
            viewModelScope.launch {
                val loadedChapter = runCatching {
                    ReadiumEpubReader(appContext).parseChapter(File(book.uri), index)
                }.getOrElse { error ->
                    loadingChapterIndexes.remove(index)
                    if (pendingChapterIndex == index) pendingChapterIndex = null
                    _state.value = _state.value.copy(
                        errorMessage = "章节读取失败：${error.message ?: error::class.java.simpleName}"
                    )
                    return@launch
                }
                val updatedChapters = _state.value.chapters.map { current ->
                    if (current.index == loadedChapter.index) loadedChapter else current
                }
                _state.value = _state.value.copy(
                    chapters = updatedChapters,
                    currentChapterIndex = index,
                    content = loadedChapter.content,
                    scrollProgress = loadedChapter.index.toFloat() / updatedChapters.size.coerceAtLeast(1),
                    errorMessage = null
                )
                loadingChapterIndexes.remove(index)
                if (pendingChapterIndex == index) pendingChapterIndex = null
                prefetchNextEpubChapter()
            }
            return
        }
        _state.value = _state.value.copy(
            currentChapterIndex = index,
            content = chapter.content,
            scrollProgress = chapter.index.toFloat() / chapters.size.coerceAtLeast(1)
        )
        prefetchNextEpubChapter()
    }

    fun loadNextChapter() {
        val state = _state.value
        if (state.isLoading) return
        val nextIndex = state.currentChapterIndex + 1
        if (nextIndex >= state.chapters.size) return
        selectChapter(nextIndex)
    }

    fun saveProgress() {
        val state = _state.value
        val percent = state.scrollProgress
        val chapter = state.chapters.getOrNull(state.currentChapterIndex)
        viewModelScope.launch {
            state.book?.let { book ->
                bookRepository.updateProgress(book.id, chapter?.href, chapter?.title, percent)
            }
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

    fun setParagraphSpacing(spacing: ParagraphSpacing) {
        viewModelScope.launch { readerSettingsStore.setParagraphSpacing(spacing) }
    }

    fun setTextAlignment(alignment: TextAlignment) {
        viewModelScope.launch { readerSettingsStore.setTextAlignment(alignment) }
    }

    fun setPageTurnMode(mode: PageTurnMode) {
        viewModelScope.launch { readerSettingsStore.setPageTurnMode(mode) }
    }

    fun setAutoBrightness(enabled: Boolean) {
        viewModelScope.launch { readerSettingsStore.setAutoBrightness(enabled) }
    }

    fun setScreenAlwaysOn(enabled: Boolean) {
        viewModelScope.launch { readerSettingsStore.setScreenAlwaysOn(enabled) }
    }

    fun setBookSpecific(bookSpecific: Boolean) {
        _state.update { it.copy(isBookSpecific = bookSpecific) }
    }

    fun enterSettingsPage() {
        settingsSnapshot = _state.value.settings
        _state.update { it.copy(hasSettingsDraft = true) }
    }

    fun confirmSettings() {
        settingsSnapshot = null
        _state.update { it.copy(hasSettingsDraft = false) }
    }

    fun cancelSettings() {
        val snapshot = settingsSnapshot
        settingsSnapshot = null
        _state.update { it.copy(hasSettingsDraft = false) }
        if (snapshot != null) {
            viewModelScope.launch {
                readerSettingsStore.setFontScale(snapshot.fontScale)
                readerSettingsStore.setLineHeight(snapshot.lineHeight)
                readerSettingsStore.setTheme(snapshot.theme)
                readerSettingsStore.setParagraphSpacing(snapshot.paragraphSpacing)
                readerSettingsStore.setTextAlignment(snapshot.textAlignment)
                readerSettingsStore.setPageTurnMode(snapshot.pageTurnMode)
                readerSettingsStore.setAutoBrightness(snapshot.autoBrightness)
                readerSettingsStore.setScreenAlwaysOn(snapshot.screenAlwaysOn)
            }
        }
    }

    fun resetSettings() {
        val defaults = ReaderSettings()
        viewModelScope.launch {
            readerSettingsStore.setFontScale(defaults.fontScale)
            readerSettingsStore.setLineHeight(defaults.lineHeight)
            readerSettingsStore.setTheme(defaults.theme)
            readerSettingsStore.setParagraphSpacing(defaults.paragraphSpacing)
            readerSettingsStore.setTextAlignment(defaults.textAlignment)
            readerSettingsStore.setPageTurnMode(defaults.pageTurnMode)
            readerSettingsStore.setAutoBrightness(defaults.autoBrightness)
            readerSettingsStore.setScreenAlwaysOn(defaults.screenAlwaysOn)
        }
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

    private fun applyContent(text: String) {
        val chapters = TextChapterParser.parse(text)
        applyChapters(chapters, fallbackText = text)
    }

    private fun applyChapters(chapters: List<ReaderChapter>, fallbackText: String?) {
        if (chapters.isEmpty()) {
            _state.value = _state.value.copy(
                content = fallbackText.orEmpty(),
                chapters = emptyList(),
                currentChapterIndex = 0,
                scrollProgress = _state.value.book?.progress?.percent ?: 0f,
                isLoading = false,
                errorMessage = if (fallbackText.isNullOrBlank()) "未解析到可阅读内容" else null
            )
            return
        }
        val initialIndex = ReaderChapterSelection.selectInitialIndex(
            chapters = chapters,
            preferredHref = _state.value.book?.progress?.chapterHref
        )
        val chapter = chapters.getOrNull(initialIndex)

        _state.value = _state.value.copy(
            content = chapter?.content ?: fallbackText.orEmpty(),
            chapters = chapters,
            currentChapterIndex = initialIndex,
            scrollProgress = _state.value.book?.progress?.percent ?: 0f,
            isLoading = false
        )
    }

    private fun prefetchNextEpubChapter() {
        val state = _state.value
        val book = state.book ?: return
        if (book.format != BookFormat.EPUB) return
        val nextIndex = state.currentChapterIndex + 1
        val nextChapter = state.chapters.getOrNull(nextIndex) ?: return
        if (nextChapter.content.isNotBlank()) return
        if (!loadingChapterIndexes.add(nextIndex)) return

        viewModelScope.launch {
            val loadedChapter = runCatching {
                ReadiumEpubReader(appContext).parseChapter(File(book.uri), nextIndex)
            }.getOrNull()
            if (loadedChapter != null) {
                val updatedChapters = _state.value.chapters.map { current ->
                    if (current.index == loadedChapter.index) loadedChapter else current
                }
                if (pendingChapterIndex == loadedChapter.index) {
                    _state.value = _state.value.copy(
                        chapters = updatedChapters,
                        currentChapterIndex = loadedChapter.index,
                        content = loadedChapter.content,
                        scrollProgress = loadedChapter.index.toFloat() / updatedChapters.size.coerceAtLeast(1),
                        errorMessage = null
                    )
                    pendingChapterIndex = null
                    prefetchNextEpubChapter()
                } else {
                    _state.value = _state.value.copy(chapters = updatedChapters)
                }
            }
            loadingChapterIndexes.remove(nextIndex)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                ReaderViewModel(app, locator.bookRepository, locator.readerSettingsStore, locator.serverRepository)
            }
        }
    }
}
