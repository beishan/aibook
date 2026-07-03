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
import com.aibook.android.core.reader.ReaderProgressCalculator
import com.aibook.android.core.reader.TextChapterParser
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

data class ReaderUiState(
    val book: LocalBook? = null,
    val loadedChapters: List<ReaderChapter> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val scrollProgress: Float = 0f,
    val chapters: List<ReaderChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentLineIndex: Int = 0,
    val currentScrollOffset: Int = 0,
    val isRemote: Boolean = false,
    val remoteBookId: Long? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val isBookSpecific: Boolean = false,
    val hasSettingsDraft: Boolean = false
) {
    val content: String get() = loadedChapters.joinToString("\n") { it.content }
    val currentChapterTitle: String get() =
        loadedChapters.lastOrNull()?.title
            ?: chapters.getOrNull(currentChapterIndex)?.title
            ?: book?.progress?.chapterTitle
            ?: ""
}

class ReaderViewModel(
    private val appContext: android.content.Context,
    private val bookRepository: BookRepository,
    private val readerSettingsStore: ReaderSettingsStore,
    private val serverRepository: ServerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    private val loadingChapterIndexes = mutableSetOf<Int>()
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

            _state.value = _state.value.copy(
                book = book,
                currentChapterIndex = book.progress.chapterIndex ?: 0,
                currentLineIndex = book.progress.lineIndex ?: 0,
                currentScrollOffset = book.progress.scrollOffset,
                scrollProgress = book.progress.percent
            )

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
                        if (chapters.isNotEmpty()) prefetchNextEpubChapter(_state.value.currentChapterIndex)
                    }
                    BookFormat.PDF -> _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "PDF 阅读器正在开发中，当前请先导入 EPUB 或 TXT 阅读"
                    )
                    BookFormat.TXT,
                    BookFormat.MARKDOWN,
                    BookFormat.HTML,
                    BookFormat.HTM -> {
                        val text = withContext(Dispatchers.IO) { readTxtFile(file) }
                        val chapters = withContext(Dispatchers.Default) { TextChapterParser.parse(text) }
                        applyChapters(chapters, fallbackText = text)
                    }
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
                    val chapters = withContext(Dispatchers.Default) { TextChapterParser.parse(response.text) }
                    applyChapters(chapters, fallbackText = response.text)
                }.onFailure { e ->
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "加载失败：${e.message}")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "加载失败：${e.message}")
            }
        }
    }

    /**
     * 更新当前视图对应的章节索引（由滚动位置计算得出）
     */
    fun updateCurrentChapterIndex(index: Int) {
        updateReadingPosition(chapterIndex = index, lineIndex = 0, scrollOffset = 0)
    }

    /**
     * 更新当前视图对应的章节、章节内行号与像素偏移。
     */
    fun updateReadingPosition(chapterIndex: Int, lineIndex: Int, scrollOffset: Int) {
        val state = _state.value
        val changedLine = chapterIndex != state.currentChapterIndex || lineIndex != state.currentLineIndex
        if (changedLine || scrollOffset != state.currentScrollOffset) {
            _state.update {
                it.copy(
                    currentChapterIndex = chapterIndex,
                    currentLineIndex = lineIndex.coerceAtLeast(0),
                    currentScrollOffset = scrollOffset.coerceAtLeast(0),
                    scrollProgress = ReaderProgressCalculator.chapterProgress(chapterIndex, it.chapters.size)
                )
            }
        }
        if (changedLine) {
            saveProgress()
        }
    }

    fun updateScrollProgress(percent: Float) {
        _state.update { it.copy(scrollProgress = percent) }
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
        val book = _state.value.book ?: return

        if (book.format == BookFormat.EPUB && chapter.content.isBlank()) {
            // 需要先从 Readium 加载这个章节
            if (!loadingChapterIndexes.add(index)) return
            viewModelScope.launch {
                val loaded = runCatching {
                    ReadiumEpubReader(appContext).parseChapter(File(book.uri), index)
                }.getOrNull()
                loadingChapterIndexes.remove(index)
                if (loaded == null) return@launch
                // 更新 chapters 列表中这个章节的内容
                val updatedChapters = _state.value.chapters.map {
                    if (it.index == index) loaded else it
                }
                _state.update {
                    it.copy(
                        chapters = updatedChapters,
                        loadedChapters = listOf(loaded),
                        currentChapterIndex = index,
                        currentLineIndex = 0,
                        currentScrollOffset = 0,
                        scrollProgress = ReaderProgressCalculator.chapterProgress(index, updatedChapters.size),
                        errorMessage = null
                    )
                }
                saveProgress()
                prefetchNextEpubChapter(index)
            }
        } else {
            // 章节已有内容，直接显示
            _state.update {
                it.copy(
                    loadedChapters = listOf(chapter),
                    currentChapterIndex = index,
                    currentLineIndex = 0,
                    currentScrollOffset = 0,
                    scrollProgress = ReaderProgressCalculator.chapterProgress(index, chapters.size)
                )
            }
            saveProgress()
            prefetchNextEpubChapter(index)
        }
    }

    /**
     * 滚动到已加载内容底部时，追加下一章到 loadedChapters 尾部
     */
    fun appendNextChapter() {
        val state = _state.value
        if (state.isLoading) return
        val lastLoaded = state.loadedChapters.lastOrNull()
        val nextIndex = (lastLoaded?.index ?: state.currentChapterIndex) + 1
        if (nextIndex >= state.chapters.size) return
        val nextChapter = state.chapters[nextIndex]
        val book = state.book

        if (book?.format == BookFormat.EPUB && nextChapter.content.isBlank()) {
            if (!loadingChapterIndexes.add(nextIndex)) return
            viewModelScope.launch {
                val loaded = runCatching {
                    ReadiumEpubReader(appContext).parseChapter(File(book.uri), nextIndex)
                }.getOrNull()
                loadingChapterIndexes.remove(nextIndex)
                if (loaded == null) return@launch
                val updatedChapters = _state.value.chapters.map {
                    if (it.index == nextIndex) loaded else it
                }
                _state.update {
                    it.copy(
                        chapters = updatedChapters,
                        loadedChapters = it.loadedChapters + loaded
                    )
                }
            }
        } else {
            _state.update {
                it.copy(
                    loadedChapters = it.loadedChapters + nextChapter
                )
            }
        }
    }

    fun saveProgress() {
        viewModelScope.launch {
            persistProgress(_state.value)
        }
    }

    fun saveProgressThen(afterSave: () -> Unit) {
        viewModelScope.launch {
            persistProgress(_state.value)
            afterSave()
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

    private suspend fun persistProgress(state: ReaderUiState) {
        val percent = state.scrollProgress
        val chapter = state.chapters.getOrNull(state.currentChapterIndex)
        state.book?.let { book ->
            bookRepository.updateProgress(
                bookId = book.id,
                chapterHref = chapter?.href,
                chapterTitle = chapter?.title,
                percent = percent,
                chapterIndex = state.currentChapterIndex,
                lineIndex = state.currentLineIndex,
                scrollOffset = state.currentScrollOffset
            )
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

    private fun applyContent(text: String) {
        val chapters = TextChapterParser.parse(text)
        applyChapters(chapters, fallbackText = text)
    }

    private fun applyChapters(chapters: List<ReaderChapter>, fallbackText: String?) {
        if (chapters.isEmpty()) {
            val loaded = fallbackText?.takeIf { it.isNotBlank() }?.let { text ->
                listOf(ReaderChapter(0, "正文", "fallback", text))
            } ?: emptyList()
            _state.value = _state.value.copy(
                loadedChapters = loaded,
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentLineIndex = _state.value.book?.progress?.lineIndex ?: 0,
                currentScrollOffset = _state.value.book?.progress?.scrollOffset ?: 0,
                scrollProgress = _state.value.book?.progress?.percent ?: 0f,
                isLoading = false,
                errorMessage = if (loaded.isEmpty()) "未解析到可阅读内容" else null
            )
            return
        }
        val savedProgress = _state.value.book?.progress
        val initialIndex = savedProgress?.chapterIndex?.takeIf { it in chapters.indices }
            ?: ReaderChapterSelection.selectInitialIndex(
            chapters = chapters,
            preferredHref = savedProgress?.chapterHref
        )
        val chapter = chapters.getOrNull(initialIndex)
        val restoredLineIndex = savedProgress
            ?.takeIf { it.chapterIndex == initialIndex || it.chapterHref == chapter?.href }
            ?.lineIndex
            ?: 0
        val restoredScrollOffset = savedProgress
            ?.takeIf { it.chapterIndex == initialIndex || it.chapterHref == chapter?.href }
            ?.scrollOffset
            ?: 0

        _state.value = _state.value.copy(
            loadedChapters = listOfNotNull(chapter),
            chapters = chapters,
            currentChapterIndex = initialIndex,
            currentLineIndex = restoredLineIndex,
            currentScrollOffset = restoredScrollOffset,
            scrollProgress = savedProgress?.percent ?: 0f,
            isLoading = false
        )
    }

    private fun prefetchNextEpubChapter(afterIndex: Int) {
        val state = _state.value
        val book = state.book ?: return
        if (book.format != BookFormat.EPUB) return
        val nextIndex = afterIndex + 1
        val nextChapter = state.chapters.getOrNull(nextIndex) ?: return
        if (nextChapter.content.isNotBlank()) return
        if (!loadingChapterIndexes.add(nextIndex)) return

        viewModelScope.launch {
            val loadedChapter = runCatching {
                ReadiumEpubReader(appContext).parseChapter(File(book.uri), nextIndex)
            }.getOrNull()
            if (loadedChapter != null) {
                val updatedChapters = _state.value.chapters.map {
                    if (it.index == nextIndex) loadedChapter else it
                }
                _state.update { it.copy(chapters = updatedChapters) }
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
