package com.aibook.android.feature.reader

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.prefs.ReaderSettingsStore
import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.data.repository.ServerRepository
import com.aibook.android.core.data.repository.ReaderBookmarkRepository
import com.aibook.android.core.data.repository.ReaderHighlightRepository
import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.PageTurnMode
import com.aibook.android.core.model.ParagraphSpacing
import com.aibook.android.core.model.ReaderContentsStyle
import com.aibook.android.core.model.ReaderAutoScrollSpeed
import com.aibook.android.core.model.ReaderFontCatalog
import com.aibook.android.core.model.ReaderFontType
import com.aibook.android.core.model.ReaderImportedFont
import com.aibook.android.core.model.ReaderOrientationMode
import com.aibook.android.core.model.ReaderSettings
import com.aibook.android.core.model.ReaderTheme
import com.aibook.android.core.model.ReadingProgress
import com.aibook.android.core.model.TextAlignment
import com.aibook.android.core.reader.EpubBookContent
import com.aibook.android.core.reader.EpubContentParser
import com.aibook.android.core.reader.BookContentError
import com.aibook.android.core.reader.BookContentLoaderRegistry
import com.aibook.android.core.reader.BookContentRequest
import com.aibook.android.core.reader.BookContentResult
import com.aibook.android.core.reader.ReaderChapter
import com.aibook.android.core.reader.ReaderChapterSelection
import com.aibook.android.core.reader.ReaderBookmark
import com.aibook.android.core.reader.ReaderHighlight
import com.aibook.android.core.reader.ReaderProgressCalculator
import com.aibook.android.core.reader.TextChapterParser
import com.aibook.android.core.reader.TextFileDecoder
import com.aibook.android.core.network.api.dto.structuredChapters
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import org.json.JSONObject

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
    val remoteVersionId: Long? = null,
    val remoteFormat: String? = null,
    val structuredChapterIds: Map<Int, Long> = emptyMap(),
    val remoteProgress: ReadingProgress? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val importedFonts: List<ReaderImportedFont> = emptyList(),
    val isBookSpecific: Boolean = false,
    val hasSettingsDraft: Boolean = false,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val highlights: List<ReaderHighlight> = emptyList(),
    val bookmarkNavigation: BookmarkNavigation? = null,
    val chapterWindowNavigation: ChapterWindowNavigation? = null
) {
    val content: String get() = loadedChapters.joinToString("\n") { it.content }
    val hasReadableContent: Boolean get() =
        loadedChapters.any { it.content.isNotBlank() || !it.imageUri.isNullOrBlank() }
    val currentChapterTitle: String get() =
        loadedChapters.lastOrNull()?.title
            ?: chapters.getOrNull(currentChapterIndex)?.title
            ?: book?.progress?.chapterTitle
            ?: remoteProgress?.chapterTitle
            ?: ""
    val isCurrentPositionBookmarked: Boolean get() = bookmarks.any {
        it.chapterIndex == currentChapterIndex && it.lineIndex == currentLineIndex
    }
}

data class BookmarkNavigation(
    val requestId: Long,
    val chapterIndex: Int,
    val lineIndex: Int,
    val scrollOffset: Int
)

data class ChapterWindowNavigation(
    val requestId: Long,
    val chapterIndex: Int,
    val lineIndex: Int
)

class ReaderViewModel(
    private val appContext: android.content.Context,
    private val bookRepository: BookRepository,
    private val readerSettingsStore: ReaderSettingsStore,
    private val serverRepository: ServerRepository,
    private val readerBookmarkRepository: ReaderBookmarkRepository
    , private val readerHighlightRepository: ReaderHighlightRepository,
    private val contentLoaderRegistry: BookContentLoaderRegistry
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    private val loadingChapterIndexes = mutableSetOf<Int>()
    private val structuredChapterCache = StructuredChapterCache(File(appContext.cacheDir, "structured-chapters"))
    private var structuredCacheNamespace: String = "default"
    private var settingsSnapshot: ReaderSettings? = null
    private var bookmarkObservationJob: Job? = null
    private var highlightObservationJob: Job? = null
    private var readingStartedAtMillis: Long = 0L

    val uiState: StateFlow<ReaderUiState> = _state
        .asStateFlow()

    init {
        viewModelScope.launch { readerSettingsStore.fontScale.collect { v -> _state.update { it.copy(settings = it.settings.copy(fontScale = v)) } } }
        viewModelScope.launch { readerSettingsStore.fontType.collect { v -> _state.update { it.copy(settings = it.settings.copy(fontType = v)) } } }
        viewModelScope.launch { readerSettingsStore.customFontName.collect { v -> _state.update { it.copy(settings = it.settings.copy(customFontName = v)) } } }
        viewModelScope.launch { readerSettingsStore.customFontPath.collect { v -> _state.update { it.copy(settings = it.settings.copy(customFontPath = v)) } } }
        viewModelScope.launch { readerSettingsStore.importedFonts.collect { v -> _state.update { it.copy(importedFonts = v) } } }
        viewModelScope.launch { readerSettingsStore.lineHeight.collect { v -> _state.update { it.copy(settings = it.settings.copy(lineHeight = v)) } } }
        viewModelScope.launch { readerSettingsStore.theme.collect { v -> _state.update { it.copy(settings = it.settings.copy(theme = v)) } } }
        viewModelScope.launch { readerSettingsStore.paragraphSpacing.collect { v -> _state.update { it.copy(settings = it.settings.copy(paragraphSpacing = v)) } } }
        viewModelScope.launch { readerSettingsStore.textAlignment.collect { v -> _state.update { it.copy(settings = it.settings.copy(textAlignment = v)) } } }
        viewModelScope.launch { readerSettingsStore.pageTurnMode.collect { v -> _state.update { it.copy(settings = it.settings.copy(pageTurnMode = v)) } } }
        viewModelScope.launch { readerSettingsStore.autoBrightness.collect { v -> _state.update { it.copy(settings = it.settings.copy(autoBrightness = v)) } } }
        viewModelScope.launch { readerSettingsStore.brightness.collect { v -> _state.update { it.copy(settings = it.settings.copy(brightness = v)) } } }
        viewModelScope.launch { readerSettingsStore.orientationMode.collect { v -> _state.update { it.copy(settings = it.settings.copy(orientationMode = v)) } } }
        viewModelScope.launch { readerSettingsStore.autoPageIntervalSeconds.collect { v -> _state.update { it.copy(settings = it.settings.copy(autoPageIntervalSeconds = v)) } } }
        viewModelScope.launch { readerSettingsStore.autoScrollSpeed.collect { v -> _state.update { it.copy(settings = it.settings.copy(autoScrollSpeed = v)) } } }
        viewModelScope.launch { readerSettingsStore.screenAlwaysOn.collect { v -> _state.update { it.copy(settings = it.settings.copy(screenAlwaysOn = v)) } } }
        viewModelScope.launch { readerSettingsStore.compressTxtBlankLines.collect { v -> _state.update { it.copy(settings = it.settings.copy(compressTxtBlankLines = v)) } } }
        viewModelScope.launch { readerSettingsStore.mergeTxtShortLines.collect { v -> _state.update { it.copy(settings = it.settings.copy(mergeTxtShortLines = v)) } } }
        viewModelScope.launch { readerSettingsStore.indentTxtParagraphs.collect { v -> _state.update { it.copy(settings = it.settings.copy(indentTxtParagraphs = v)) } } }
        viewModelScope.launch { readerSettingsStore.contentsStyle.collect { v -> _state.update { it.copy(settings = it.settings.copy(contentsStyle = v)) } } }
        viewModelScope.launch { readerSettingsStore.showContentsProgress.collect { v -> _state.update { it.copy(settings = it.settings.copy(showContentsProgress = v)) } } }
    }

    fun loadLocalBook(bookId: String) {
        viewModelScope.launch {
            structuredCacheNamespace = "default"
            readingStartedAtMillis = System.currentTimeMillis()
            _state.value = _state.value.copy(
                isLoading = true,
                errorMessage = null,
                isRemote = false,
                remoteBookId = null,
                remoteVersionId = null,
                remoteFormat = null,
                structuredChapterIds = emptyMap(),
                remoteProgress = null
            )

            val book = bookRepository.getBook(bookId)
            if (book == null) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "书籍不存在")
                return@launch
            }

            _state.value = _state.value.copy(
                book = book,
                isRemote = book.remoteBookId != null,
                remoteBookId = book.remoteBookId,
                currentChapterIndex = book.progress.chapterIndex ?: 0,
                currentLineIndex = book.progress.lineIndex ?: 0,
                currentScrollOffset = book.progress.scrollOffset,
                scrollProgress = book.progress.percent
            )
            val linkedRemoteBookId = book.remoteBookId
            if (linkedRemoteBookId != null) {
                serverRepository.getReadingProgress(linkedRemoteBookId).getOrNull()?.let { saved ->
                    val locator = saved.locator?.let(::decodeRemoteLocator)
                    _state.update {
                        it.copy(
                            remoteVersionId = saved.versionId,
                            remoteProgress = ReadingProgress(
                                chapterHref = saved.currentChapter,
                                chapterTitle = saved.currentChapterTitle,
                                chapterIndex = locator?.chapterIndex,
                                lineIndex = locator?.lineIndex,
                                scrollOffset = locator?.scrollOffset ?: 0,
                                percent = (saved.totalProgress / 100f).coerceIn(0f, 1f)
                            ),
                            currentChapterIndex = locator?.chapterIndex ?: book.progress.chapterIndex ?: 0,
                            currentLineIndex = locator?.lineIndex ?: book.progress.lineIndex ?: 0,
                            currentScrollOffset = locator?.scrollOffset ?: book.progress.scrollOffset,
                            scrollProgress = (saved.totalProgress / 100f).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            if (linkedRemoteBookId != null) {
                loadCloudAnnotations(linkedRemoteBookId)
            } else {
                observeBookmarks(book.id)
                observeHighlights(book.id)
            }

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
                            epub.chapters.firstOrNull()?.let { it.content.isBlank() && it.imageUri.isNullOrBlank() } == true
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
                    BookFormat.MARKDOWN,
                    BookFormat.MOBI,
                    BookFormat.AZW3 -> {
                        val loader = contentLoaderRegistry.loaderFor(book.format)
                        val result = loader?.load(
                            BookContentRequest(
                                bookId = book.id,
                                file = file,
                                format = book.format,
                                contentHash = book.sha256,
                                preferredChapterHref = book.progress.chapterHref,
                                cacheDirectory = bookRepository.parsedBookDirectory(book.id)
                            )
                        ) ?: BookContentResult.Failure(BookContentError.UnsupportedVariant)
                        when (result) {
                            is BookContentResult.Success -> applyChapters(result.content.chapters, fallbackText = null)
                            is BookContentResult.Failure -> {
                                val error = result.error
                                _state.value = _state.value.copy(
                                isLoading = false,
                                errorMessage = BookContentErrorText.forError(error)
                            )
                            }
                        }
                    }
                    BookFormat.TXT,
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
            structuredCacheNamespace = serverRepository.structuredCacheNamespace()
            readingStartedAtMillis = System.currentTimeMillis()
            bookmarkObservationJob?.cancel()
            highlightObservationJob?.cancel()
            _state.value = _state.value.copy(
                book = null,
                bookmarks = emptyList(),
                isLoading = true,
                errorMessage = null,
                isRemote = true,
                remoteBookId = bookId,
                remoteVersionId = null,
                remoteFormat = null,
                structuredChapterIds = emptyMap(),
                remoteProgress = null
            )
            try {
                serverRepository.getReadingProgress(bookId).getOrNull()?.let { saved ->
                    val locator = saved.locator?.let(::decodeRemoteLocator)
                    _state.update {
                        it.copy(
                            remoteVersionId = saved.versionId,
                            remoteProgress = ReadingProgress(
                                chapterHref = saved.currentChapter,
                                chapterTitle = saved.currentChapterTitle,
                                chapterIndex = locator?.chapterIndex,
                                lineIndex = locator?.lineIndex,
                                scrollOffset = locator?.scrollOffset ?: 0,
                                percent = (saved.totalProgress / 100f).coerceIn(0f, 1f)
                            ),
                            scrollProgress = (saved.totalProgress / 100f).coerceIn(0f, 1f)
                        )
                    }
                }
                loadCloudAnnotations(bookId)
                val metadata = serverRepository.getBookById(bookId).getOrThrow()
                _state.update { it.copy(remoteFormat = metadata.format?.lowercase()) }
                val versionId = _state.value.remoteVersionId
                serverRepository.recordBookOpen(bookId, versionId)
                if (metadata.format.equals("structured", ignoreCase = true)) {
                    loadRemoteStructuredBook(bookId, versionId)
                    return@launch
                }
                val format = metadata.format
                    ?.let { BookFormat.fromFileName("book.${it.lowercase()}") }
                    ?: throw IllegalArgumentException("暂不支持该书籍格式")
                val cacheFile = File(appContext.cacheDir, "server-books/$bookId.${format.extension}")
                val downloaded = serverRepository.downloadBookContent(bookId, cacheFile, versionId)
                if (downloaded.isFailure && (!cacheFile.exists() || cacheFile.length() == 0L)) {
                    throw downloaded.exceptionOrNull() ?: IllegalStateException("下载书籍失败")
                }
                loadRemoteFile(bookId, cacheFile, format)
            } catch (e: Exception) {
                val cachedPublication = withContext(Dispatchers.IO) {
                    structuredChapterCache.readManifest(
                        structuredCacheNamespace,
                        bookId,
                        _state.value.remoteVersionId
                    )
                }
                if (cachedPublication != null) {
                    _state.update {
                        it.copy(
                            remoteFormat = STRUCTURED_FORMAT,
                            remoteVersionId = cachedPublication.versionId
                        )
                    }
                    loadStructuredPublication(bookId, cachedPublication, allowWholeBookFallback = false)
                } else {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "加载失败：${e.message}")
                }
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

    fun toggleBookmark() {
        val state = _state.value
        val existing = currentBookmark(state)
        val remoteBookId = state.remoteBookId
        viewModelScope.launch {
            if (remoteBookId != null) {
                if (existing != null) {
                    serverRepository.deleteBookmark(remoteBookId, existing.id)
                        .onSuccess { loadCloudAnnotations(remoteBookId) }
                } else {
                    val chapter = state.chapters.getOrNull(state.currentChapterIndex)
                    serverRepository.createBookmark(
                        remoteBookId,
                        ReaderBookmark(
                            bookId = "server:$remoteBookId",
                            chapterHref = chapter?.href,
                            chapterTitle = chapter?.title,
                            progress = state.scrollProgress,
                            chapterIndex = state.currentChapterIndex,
                            lineIndex = state.currentLineIndex,
                            scrollOffset = state.currentScrollOffset
                        )
                    ).onSuccess { loadCloudAnnotations(remoteBookId) }
                }
            } else {
                val book = state.book ?: return@launch
                val chapter = state.chapters.getOrNull(state.currentChapterIndex)
                if (existing != null) readerBookmarkRepository.remove(existing.id)
                else readerBookmarkRepository.add(ReaderBookmark(
                    bookId = book.id,
                    chapterHref = chapter?.href,
                    chapterTitle = chapter?.title,
                    progress = state.scrollProgress,
                    chapterIndex = state.currentChapterIndex,
                    lineIndex = state.currentLineIndex,
                    scrollOffset = state.currentScrollOffset
                ))
            }
        }
    }

    fun removeBookmark(bookmark: ReaderBookmark) {
        val remoteBookId = _state.value.remoteBookId
        viewModelScope.launch {
            if (remoteBookId != null) {
                serverRepository.deleteBookmark(remoteBookId, bookmark.id)
                    .onSuccess { loadCloudAnnotations(remoteBookId) }
            } else readerBookmarkRepository.remove(bookmark.id)
        }
    }

    fun addHighlight(chapter: ReaderChapter, lineIndex: Int, text: String, note: String?, color: Long) {
        val state = _state.value
        val remoteBookId = state.remoteBookId
        val highlight = ReaderHighlight.create(
            bookId = remoteBookId?.let { "server:$it" } ?: state.book?.id ?: return,
            chapterHref = chapter.href,
            text = text,
            startOffset = 0,
            endOffset = text.length,
            note = note,
            chapterIndex = chapter.index,
            lineIndex = lineIndex,
            color = color
        )
        viewModelScope.launch {
            if (remoteBookId != null) {
                serverRepository.createHighlight(remoteBookId, highlight)
                    .onSuccess { loadCloudAnnotations(remoteBookId) }
            } else readerHighlightRepository.add(highlight)
        }
    }

    fun removeHighlight(highlight: ReaderHighlight) {
        val remoteBookId = _state.value.remoteBookId
        viewModelScope.launch {
            if (remoteBookId != null) {
                serverRepository.deleteHighlight(remoteBookId, highlight.id)
                    .onSuccess { loadCloudAnnotations(remoteBookId) }
            } else readerHighlightRepository.remove(highlight.id)
        }
    }

    fun openBookmark(bookmark: ReaderBookmark) {
        val targetIndex = bookmark.chapterIndex
            ?: _state.value.chapters.indexOfFirst { it.href == bookmark.chapterHref }.takeIf { it >= 0 }
            ?: return
        val chapter = _state.value.chapters.getOrNull(targetIndex) ?: return
        val book = _state.value.book

        viewModelScope.launch {
            val loaded = if (
                isStructuredChapterOnDemand(_state.value, targetIndex) &&
                chapter.content.isBlank() && chapter.imageUri.isNullOrBlank()
            ) {
                loadStructuredChapter(targetIndex) ?: run {
                    _state.update { it.copy(errorMessage = "书签所在章节加载失败，请检查网络后重试") }
                    return@launch
                }
            } else if (
                book != null && book.format == BookFormat.EPUB &&
                chapter.content.isBlank() && chapter.imageUri.isNullOrBlank()
            ) {
                runCatching { ReadiumEpubReader(appContext).parseChapter(File(book.uri), targetIndex) }.getOrNull()
                    ?: chapter
            } else chapter
            val updatedChapters = _state.value.chapters.map { if (it.index == targetIndex) loaded else it }
            _state.update {
                it.copy(
                    chapters = updatedChapters,
                    loadedChapters = listOf(loaded),
                    currentChapterIndex = targetIndex,
                    currentLineIndex = bookmark.lineIndex,
                    currentScrollOffset = bookmark.scrollOffset,
                    scrollProgress = bookmark.progress,
                    bookmarkNavigation = BookmarkNavigation(
                        requestId = System.nanoTime(),
                        chapterIndex = targetIndex,
                        lineIndex = bookmark.lineIndex,
                        scrollOffset = bookmark.scrollOffset
                    )
                )
            }
            if (isStructuredChapterOnDemand(_state.value, targetIndex)) {
                prefetchStructuredChapters(targetIndex)
            }
        }
    }

    fun openSearchMatch(match: ReaderSearchMatch) {
        val state = _state.value
        val chapter = state.chapters.getOrNull(match.chapterIndex) ?: return
        val book = state.book

        fun publish(loaded: ReaderChapter) {
            _state.update { current ->
                current.copy(
                    chapters = current.chapters.map { if (it.index == match.chapterIndex) loaded else it },
                    loadedChapters = listOf(loaded),
                    currentChapterIndex = match.chapterIndex,
                    currentLineIndex = match.lineIndex,
                    currentScrollOffset = 0,
                    scrollProgress = ReaderProgressCalculator.chapterProgress(match.chapterIndex, current.chapters.size),
                    bookmarkNavigation = BookmarkNavigation(
                        requestId = System.nanoTime(),
                        chapterIndex = match.chapterIndex,
                        lineIndex = match.lineIndex,
                        scrollOffset = 0
                    ),
                    chapterWindowNavigation = ChapterWindowNavigation(
                        requestId = System.nanoTime(),
                        chapterIndex = match.chapterIndex,
                        lineIndex = match.lineIndex
                    )
                )
            }
            saveProgress()
        }

        if (book?.format == BookFormat.EPUB && chapter.content.isBlank() && chapter.imageUri.isNullOrBlank()) {
            if (!loadingChapterIndexes.add(match.chapterIndex)) return
            viewModelScope.launch {
                val loaded = runCatching {
                    ReadiumEpubReader(appContext).parseChapter(File(book.uri), match.chapterIndex)
                }.getOrNull()
                loadingChapterIndexes.remove(match.chapterIndex)
                if (loaded != null) publish(loaded)
            }
        } else {
            publish(chapter)
        }
    }

    private fun observeBookmarks(bookId: String) {
        bookmarkObservationJob?.cancel()
        bookmarkObservationJob = viewModelScope.launch {
            readerBookmarkRepository.observeForBook(bookId).collect { bookmarks ->
                _state.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    private fun observeHighlights(bookId: String) {
        highlightObservationJob?.cancel()
        highlightObservationJob = viewModelScope.launch {
            readerHighlightRepository.observeForBook(bookId).collect { highlights ->
                _state.update { it.copy(highlights = highlights) }
            }
        }
    }

    private suspend fun loadCloudAnnotations(bookId: Long) {
        val bookmarks = serverRepository.getBookmarks(bookId)
        val highlights = serverRepository.getHighlights(bookId)
        _state.update { state ->
            state.copy(
                bookmarks = bookmarks.getOrNull() ?: state.bookmarks,
                highlights = highlights.getOrNull() ?: state.highlights
            )
        }
    }

    private fun currentBookmark(state: ReaderUiState): ReaderBookmark? =
        state.bookmarks.firstOrNull {
            it.chapterIndex == state.currentChapterIndex && it.lineIndex == state.currentLineIndex
        }

    fun selectChapter(index: Int) {
        val chapters = _state.value.chapters
        val chapter = chapters.getOrNull(index) ?: return
        val book = _state.value.book

        if (isStructuredChapterOnDemand(_state.value, index) &&
            chapter.content.isBlank() && chapter.imageUri.isNullOrBlank()
        ) {
            if (!loadingChapterIndexes.add(index)) return
            viewModelScope.launch {
                val loaded = loadStructuredChapter(index)
                loadingChapterIndexes.remove(index)
                if (loaded == null) {
                    _state.update { it.copy(errorMessage = "章节加载失败，请检查网络后重试") }
                    return@launch
                }
                publishSelectedChapter(index, loaded)
                prefetchStructuredChapters(index)
            }
        } else if (book != null && book.format == BookFormat.EPUB && chapter.content.isBlank() && chapter.imageUri.isNullOrBlank()) {
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
                publishSelectedChapter(index, loaded, updatedChapters)
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
            if (_state.value.remoteFormat == STRUCTURED_FORMAT) prefetchStructuredChapters(index)
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

        if (isStructuredChapterOnDemand(state, nextIndex) &&
            nextChapter.content.isBlank() && nextChapter.imageUri.isNullOrBlank()
        ) {
            if (!loadingChapterIndexes.add(nextIndex)) return
            viewModelScope.launch {
                val loaded = loadStructuredChapter(nextIndex)
                loadingChapterIndexes.remove(nextIndex)
                if (loaded == null) {
                    _state.update { it.copy(errorMessage = "下一章加载失败，请检查网络后重试") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        chapters = it.chapters.map { item -> if (item.index == nextIndex) loaded else item },
                        loadedChapters = it.loadedChapters + loaded,
                        errorMessage = null
                    )
                }
                prefetchStructuredChapters(nextIndex)
            }
        } else if (book?.format == BookFormat.EPUB && nextChapter.content.isBlank() && nextChapter.imageUri.isNullOrBlank()) {
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

    /**
     * 滑动到已加载内容顶部时，在章节窗口前插入上一章。
     */
    fun prependPreviousChapter() {
        val state = _state.value
        if (state.isLoading) return
        val firstLoaded = state.loadedChapters.firstOrNull() ?: return
        val previousIndex = firstLoaded.index - 1
        if (previousIndex < 0) return
        val previousChapter = state.chapters.getOrNull(previousIndex) ?: return
        val book = state.book

        fun prepend(loaded: ReaderChapter) {
            _state.update { current ->
                current.copy(
                    chapters = current.chapters.map {
                        if (it.index == previousIndex) loaded else it
                    },
                    loadedChapters = ReaderChapterWindow.prepend(current.loadedChapters, loaded),
                    chapterWindowNavigation = ChapterWindowNavigation(
                        requestId = System.nanoTime(),
                        chapterIndex = current.currentChapterIndex,
                        lineIndex = current.currentLineIndex
                    )
                )
            }
        }

        if (isStructuredChapterOnDemand(state, previousIndex) &&
            previousChapter.content.isBlank() && previousChapter.imageUri.isNullOrBlank()
        ) {
            if (!loadingChapterIndexes.add(previousIndex)) return
            viewModelScope.launch {
                val loaded = loadStructuredChapter(previousIndex)
                loadingChapterIndexes.remove(previousIndex)
                if (loaded == null) {
                    _state.update { it.copy(errorMessage = "上一章加载失败，请检查网络后重试") }
                    return@launch
                }
                prepend(loaded)
                prefetchStructuredChapters(previousIndex)
            }
        } else if (book?.format == BookFormat.EPUB &&
            previousChapter.content.isBlank() && previousChapter.imageUri.isNullOrBlank()
        ) {
            if (!loadingChapterIndexes.add(previousIndex)) return
            viewModelScope.launch {
                val loaded = runCatching {
                    ReadiumEpubReader(appContext).parseChapter(File(book.uri), previousIndex)
                }.getOrNull()
                loadingChapterIndexes.remove(previousIndex)
                if (loaded != null) prepend(loaded)
            }
        } else {
            prepend(previousChapter)
        }
    }

    fun saveProgress() {
        viewModelScope.launch {
            persistProgress(_state.value)
            persistReadingDuration()
        }
    }

    fun saveProgressThen(afterSave: () -> Unit) {
        viewModelScope.launch {
            persistProgress(_state.value)
            persistReadingDuration()
            afterSave()
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { readerSettingsStore.setFontScale(value) }
    }

    fun setFontType(type: ReaderFontType) {
        viewModelScope.launch { readerSettingsStore.setFontType(type) }
    }

    fun importFonts(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { uris.map(::copyFontToPrivateStorage) }
            val imported = results.mapNotNull { it.getOrNull() }
            if (imported.isNotEmpty()) {
                readerSettingsStore.addImportedFonts(imported)
            }
            val failedCount = results.count { it.isFailure }
            if (failedCount > 0) {
                _state.update {
                    it.copy(errorMessage = "字体导入完成：成功 ${imported.size} 个，失败 $failedCount 个")
                }
            }
        }
    }

    fun selectImportedFont(font: ReaderImportedFont) {
        viewModelScope.launch { readerSettingsStore.setCustomFont(font.name, font.path) }
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

    fun setBrightness(value: Float) {
        viewModelScope.launch { readerSettingsStore.setBrightness(value) }
    }

    fun setOrientationMode(mode: ReaderOrientationMode) {
        viewModelScope.launch { readerSettingsStore.setOrientationMode(mode) }
    }

    fun setAutoPageIntervalSeconds(seconds: Int) {
        viewModelScope.launch { readerSettingsStore.setAutoPageIntervalSeconds(seconds) }
    }

    fun setAutoScrollSpeed(speed: ReaderAutoScrollSpeed) {
        viewModelScope.launch { readerSettingsStore.setAutoScrollSpeed(speed) }
    }

    fun setScreenAlwaysOn(enabled: Boolean) {
        viewModelScope.launch { readerSettingsStore.setScreenAlwaysOn(enabled) }
    }

    fun setCompressTxtBlankLines(enabled: Boolean) {
        viewModelScope.launch { readerSettingsStore.setCompressTxtBlankLines(enabled) }
    }
    fun setMergeTxtShortLines(enabled: Boolean) { viewModelScope.launch { readerSettingsStore.setMergeTxtShortLines(enabled) } }
    fun setIndentTxtParagraphs(enabled: Boolean) { viewModelScope.launch { readerSettingsStore.setIndentTxtParagraphs(enabled) } }

    fun setContentsStyle(style: ReaderContentsStyle) {
        viewModelScope.launch { readerSettingsStore.setContentsStyle(style) }
    }

    fun setShowContentsProgress(show: Boolean) {
        viewModelScope.launch { readerSettingsStore.setShowContentsProgress(show) }
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
                readerSettingsStore.setFontType(snapshot.fontType)
                val customFontName = snapshot.customFontName
                val customFontPath = snapshot.customFontPath
                if (customFontName != null && customFontPath != null) {
                    readerSettingsStore.setCustomFont(customFontName, customFontPath)
                }
                readerSettingsStore.setLineHeight(snapshot.lineHeight)
                readerSettingsStore.setTheme(snapshot.theme)
                readerSettingsStore.setParagraphSpacing(snapshot.paragraphSpacing)
                readerSettingsStore.setTextAlignment(snapshot.textAlignment)
                readerSettingsStore.setPageTurnMode(snapshot.pageTurnMode)
                readerSettingsStore.setAutoBrightness(snapshot.autoBrightness)
                readerSettingsStore.setBrightness(snapshot.brightness)
                readerSettingsStore.setOrientationMode(snapshot.orientationMode)
                readerSettingsStore.setAutoPageIntervalSeconds(snapshot.autoPageIntervalSeconds)
                readerSettingsStore.setAutoScrollSpeed(snapshot.autoScrollSpeed)
                readerSettingsStore.setScreenAlwaysOn(snapshot.screenAlwaysOn)
                readerSettingsStore.setCompressTxtBlankLines(snapshot.compressTxtBlankLines)
                readerSettingsStore.setMergeTxtShortLines(snapshot.mergeTxtShortLines)
                readerSettingsStore.setIndentTxtParagraphs(snapshot.indentTxtParagraphs)
                readerSettingsStore.setContentsStyle(snapshot.contentsStyle)
                readerSettingsStore.setShowContentsProgress(snapshot.showContentsProgress)
            }
        }
    }

    fun resetSettings() {
        val defaults = ReaderSettings()
        viewModelScope.launch {
            readerSettingsStore.setFontScale(defaults.fontScale)
            readerSettingsStore.setFontType(defaults.fontType)
            readerSettingsStore.setLineHeight(defaults.lineHeight)
            readerSettingsStore.setTheme(defaults.theme)
            readerSettingsStore.setParagraphSpacing(defaults.paragraphSpacing)
            readerSettingsStore.setTextAlignment(defaults.textAlignment)
            readerSettingsStore.setPageTurnMode(defaults.pageTurnMode)
            readerSettingsStore.setAutoBrightness(defaults.autoBrightness)
            readerSettingsStore.setBrightness(defaults.brightness)
            readerSettingsStore.setOrientationMode(defaults.orientationMode)
            readerSettingsStore.setAutoPageIntervalSeconds(defaults.autoPageIntervalSeconds)
            readerSettingsStore.setAutoScrollSpeed(defaults.autoScrollSpeed)
            readerSettingsStore.setScreenAlwaysOn(defaults.screenAlwaysOn)
            readerSettingsStore.setCompressTxtBlankLines(defaults.compressTxtBlankLines)
            readerSettingsStore.setMergeTxtShortLines(defaults.mergeTxtShortLines)
            readerSettingsStore.setIndentTxtParagraphs(defaults.indentTxtParagraphs)
            readerSettingsStore.setContentsStyle(defaults.contentsStyle)
            readerSettingsStore.setShowContentsProgress(defaults.showContentsProgress)
        }
    }

    private fun readTxtFile(file: File): String {
        return TextFileDecoder.decode(file.readBytes())
    }

    private fun copyFontToPrivateStorage(uri: Uri): Result<ReaderImportedFont> {
        return runCatching {
            val displayName = fontDisplayName(uri)
            require(ReaderFontCatalog.isSupportedFontFile(displayName)) {
                "请选择 .ttf 或 .otf 字体文件"
            }
            val extension = displayName.substringAfterLast('.', "ttf").lowercase()
            val targetDir = File(appContext.filesDir, "reader_fonts").apply { mkdirs() }
            val target = File(targetDir, "${UUID.randomUUID()}.$extension")
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取字体文件" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            ReaderImportedFont(
                name = displayName.substringBeforeLast('.').ifBlank { "本地导入字体" },
                path = target.absolutePath
            )
        }
    }

    private fun fontDisplayName(uri: Uri): String {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported-font.ttf"
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
            val chapterLineCount = chapter?.content?.lineSequence()?.count()?.coerceAtLeast(1) ?: 1
            val chapterProgress = ((state.currentLineIndex.toFloat() / chapterLineCount) * 100)
                .toInt()
                .coerceIn(0, 100)
            serverRepository.saveReadingProgress(
                bookId = state.remoteBookId,
                versionId = state.remoteVersionId,
                chapter = chapter?.href,
                chapterTitle = chapter?.title,
                chapterProgress = chapterProgress,
                totalProgress = (percent * 100).toInt(),
                locator = encodeRemoteLocator(state)
            )
        }
    }

    private suspend fun persistReadingDuration() {
        val startedAt = readingStartedAtMillis
        val state = _state.value
        if (startedAt == 0L) return
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
        state.book?.id?.let { bookRepository.addReadingDuration(it, seconds) }
        state.remoteBookId?.takeIf { state.isRemote }?.let {
            serverRepository.addReadingTime(it, seconds, state.remoteVersionId)
        }
        readingStartedAtMillis = System.currentTimeMillis()
    }

    private fun applyContent(text: String) {
        val chapters = TextChapterParser.parse(text)
        applyChapters(chapters, fallbackText = text)
    }

    private suspend fun loadRemoteFile(bookId: Long, file: File, format: BookFormat) {
        val preferredHref = _state.value.remoteProgress?.chapterHref
        when (format) {
            BookFormat.EPUB -> {
                var epub = runCatching { ReadiumEpubReader(appContext).parse(file, preferredHref) }.getOrNull()
                if (epub == null || epub.chapters.isEmpty()) {
                    epub = runCatching { EpubContentParser.parse(file.readBytes()) }.getOrNull()
                        ?: epub
                        ?: EpubBookContent()
                }
                applyChapters(epub.chapters, epub.fullText.takeIf { epub.chapters.isEmpty() })
            }
            BookFormat.PDF -> _state.update {
                it.copy(isLoading = false, errorMessage = "PDF 阅读器正在开发中，当前支持 EPUB、TXT、MOBI、AZW3、Markdown 和 HTML")
            }
            BookFormat.MARKDOWN,
            BookFormat.MOBI,
            BookFormat.AZW3 -> {
                val loader = contentLoaderRegistry.loaderFor(format)
                val result = loader?.load(
                    BookContentRequest(
                        bookId = "remote-$bookId",
                        file = file,
                        format = format,
                        preferredChapterHref = preferredHref,
                        cacheDirectory = bookRepository.parsedBookDirectory("remote-$bookId")
                    )
                ) ?: BookContentResult.Failure(BookContentError.UnsupportedVariant)
                when (result) {
                    is BookContentResult.Success -> applyChapters(result.content.chapters, fallbackText = null)
                    is BookContentResult.Failure -> _state.update {
                        it.copy(isLoading = false, errorMessage = BookContentErrorText.forError(result.error))
                    }
                }
            }
            BookFormat.TXT,
            BookFormat.HTML,
            BookFormat.HTM -> {
                val text = withContext(Dispatchers.IO) { readTxtFile(file) }
                val chapters = withContext(Dispatchers.Default) { TextChapterParser.parse(text) }
                applyChapters(chapters, fallbackText = text)
            }
        }
    }

    private suspend fun loadRemoteStructuredBook(bookId: Long, versionId: Long?) {
        val manifest = serverRepository.getStructuredManifest(bookId, versionId).getOrNull()
        val manifestLinks = manifest?.readingOrder.orEmpty()
        val networkEntries = manifestLinks.mapNotNull { link ->
            link.chapterId()?.let { chapterId ->
                CachedStructuredChapter(
                    chapterId = chapterId,
                    key = link.properties.chapterKey,
                    title = link.title
                )
            }
        }
        val networkVersionId = manifestLinks.firstNotNullOfOrNull { it.versionId() } ?: versionId
        val networkPublication = networkVersionId?.let { resolvedVersionId ->
            CachedStructuredManifest(resolvedVersionId, networkEntries)
                .takeIf { networkEntries.isNotEmpty() && networkEntries.size == manifestLinks.size }
        }
        if (networkPublication != null) {
            withContext(Dispatchers.IO) {
                structuredChapterCache.writeManifest(
                    structuredCacheNamespace,
                    bookId,
                    networkPublication,
                    markActive = true
                )
            }
        }
        val publication = networkPublication ?: withContext(Dispatchers.IO) {
            structuredChapterCache.readManifest(structuredCacheNamespace, bookId, versionId)
        }
        if (publication == null) {
            loadRemoteStructuredBookFallback(bookId, versionId)
            return
        }
        loadStructuredPublication(bookId, publication, allowWholeBookFallback = true)
    }

    private suspend fun loadStructuredPublication(
        bookId: Long,
        publication: CachedStructuredManifest,
        allowWholeBookFallback: Boolean
    ) {
        val chapters = publication.chapters.mapIndexed { index, entry ->
            val chapterKey = entry.key.ifBlank { "chapter-$index" }
            ReaderChapter(
                index = index,
                title = entry.title.ifBlank { "第 ${index + 1} 章" },
                href = "structured:$chapterKey",
                content = ""
            )
        }
        val chapterIds = publication.chapters.mapIndexed { index, entry -> index to entry.chapterId }.toMap()
        val progress = _state.value.remoteProgress
        val preferredIndex = progress?.chapterIndex?.takeIf { it in chapters.indices }
            ?: chapters.indexOfFirst { it.href == progress?.chapterHref }.takeIf { it >= 0 }
            ?: 0

        _state.update {
            it.copy(
                chapters = chapters,
                loadedChapters = emptyList(),
                structuredChapterIds = chapterIds,
                remoteVersionId = publication.versionId,
                currentChapterIndex = preferredIndex
            )
        }
        val loaded = loadStructuredChapter(preferredIndex)
        if (loaded == null) {
            if (allowWholeBookFallback) {
                loadRemoteStructuredBookFallback(bookId, publication.versionId)
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "当前章节尚未缓存，请联网后重试"
                    )
                }
            }
            return
        }
        val updatedChapters = chapters.map { if (it.index == preferredIndex) loaded else it }
        val restorePosition = progress?.chapterIndex == preferredIndex || progress?.chapterHref == loaded.href
        _state.update {
            it.copy(
                chapters = updatedChapters,
                loadedChapters = listOf(loaded),
                currentChapterIndex = preferredIndex,
                currentLineIndex = if (restorePosition) progress?.lineIndex ?: 0 else 0,
                currentScrollOffset = if (restorePosition) progress?.scrollOffset ?: 0 else 0,
                scrollProgress = progress?.percent ?: 0f,
                isLoading = false,
                errorMessage = null
            )
        }
        prefetchStructuredChapters(preferredIndex)
    }

    private suspend fun loadRemoteStructuredBookFallback(bookId: Long, versionId: Long?) {
        val response = serverRepository.getProcessedContent(bookId, versionId).getOrThrow()
        val chapters = response.structuredChapters().mapIndexed { index, info ->
            ReaderChapter(
                index = index,
                title = info.title,
                href = "structured:${info.key}",
                content = response.text.substring(info.startIndex, info.endIndex)
                    .removePrefix(info.title)
                    .trim()
            )
        }
        _state.update { it.copy(structuredChapterIds = emptyMap()) }
        applyChapters(chapters, response.text)
    }

    private suspend fun loadStructuredChapter(index: Int): ReaderChapter? {
        val state = _state.value
        val bookId = state.remoteBookId ?: return null
        val versionId = state.remoteVersionId ?: return null
        val chapterId = state.structuredChapterIds[index] ?: return null
        val placeholder = state.chapters.getOrNull(index) ?: return null
        val cachedContent = withContext(Dispatchers.IO) {
            structuredChapterCache.readChapter(
                structuredCacheNamespace,
                bookId,
                versionId,
                chapterId
            )
        }
        if (cachedContent != null) return placeholder.copy(content = cachedContent)

        val response = serverRepository.getStructuredChapter(bookId, chapterId, versionId)
            .getOrNull() ?: return null
        val content = response.content.replace("\r\n", "\n").replace('\r', '\n').trim()
        withContext(Dispatchers.IO) {
            structuredChapterCache.writeChapter(
                structuredCacheNamespace,
                bookId,
                versionId,
                chapterId,
                content
            )
        }
        return placeholder.copy(
            title = response.title.ifBlank { placeholder.title },
            href = "structured:${response.key.ifBlank { placeholder.href.removePrefix("structured:") }}",
            content = content
        )
    }

    private fun isStructuredChapterOnDemand(state: ReaderUiState, index: Int): Boolean =
        state.remoteFormat == STRUCTURED_FORMAT && state.structuredChapterIds.containsKey(index)

    private fun publishSelectedChapter(
        index: Int,
        loaded: ReaderChapter,
        chapters: List<ReaderChapter> = _state.value.chapters.map {
            if (it.index == index) loaded else it
        }
    ) {
        _state.update {
            it.copy(
                chapters = chapters,
                loadedChapters = listOf(loaded),
                currentChapterIndex = index,
                currentLineIndex = 0,
                currentScrollOffset = 0,
                scrollProgress = ReaderProgressCalculator.chapterProgress(index, chapters.size),
                errorMessage = null
            )
        }
        saveProgress()
    }

    private fun prefetchStructuredChapters(aroundIndex: Int) {
        if (_state.value.remoteFormat != STRUCTURED_FORMAT || _state.value.structuredChapterIds.isEmpty()) return
        listOf(aroundIndex - 1, aroundIndex + 1).forEach { index ->
            val state = _state.value
            val chapter = state.chapters.getOrNull(index) ?: return@forEach
            if (chapter.content.isNotBlank() || !chapter.imageUri.isNullOrBlank()) return@forEach
            if (!loadingChapterIndexes.add(index)) return@forEach
            viewModelScope.launch {
                val loaded = loadStructuredChapter(index)
                if (loaded != null) {
                    _state.update { current ->
                        current.copy(chapters = current.chapters.map {
                            if (it.index == index) loaded else it
                        })
                    }
                }
                loadingChapterIndexes.remove(index)
            }
        }
    }

    private data class RemoteLocator(
        val chapterIndex: Int?,
        val lineIndex: Int?,
        val scrollOffset: Int
    )

    private fun decodeRemoteLocator(value: String): RemoteLocator? = runCatching {
        val json = JSONObject(value)
        RemoteLocator(
            chapterIndex = json.optInt("chapterIndex").takeIf { json.has("chapterIndex") },
            lineIndex = json.optInt("lineIndex").takeIf { json.has("lineIndex") },
            scrollOffset = json.optInt("scrollOffset", 0).coerceAtLeast(0)
        )
    }.getOrNull()

    private fun encodeRemoteLocator(state: ReaderUiState): String = JSONObject()
        .put("type", "aibook-android")
        .put("chapterIndex", state.currentChapterIndex)
        .put("lineIndex", state.currentLineIndex)
        .put("scrollOffset", state.currentScrollOffset)
        .toString()

    private fun applyChapters(chapters: List<ReaderChapter>, fallbackText: String?) {
        val activeProgress = if (_state.value.remoteBookId != null) {
            _state.value.remoteProgress ?: _state.value.book?.progress
        } else {
            _state.value.book?.progress
        }
        if (chapters.isEmpty()) {
            val loaded = fallbackText?.takeIf { it.isNotBlank() }?.let { text ->
                listOf(ReaderChapter(0, "正文", "fallback", text))
            } ?: emptyList()
            _state.value = _state.value.copy(
                loadedChapters = loaded,
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentLineIndex = activeProgress?.lineIndex ?: 0,
                currentScrollOffset = activeProgress?.scrollOffset ?: 0,
                scrollProgress = activeProgress?.percent ?: 0f,
                isLoading = false,
                errorMessage = if (loaded.isEmpty()) "未解析到可阅读内容" else null
            )
            return
        }
        val savedProgress = activeProgress
        val initialIndex = ReaderChapterSelection.selectInitialIndex(
            chapters = chapters,
            preferredHref = savedProgress?.chapterHref,
            preferredIndex = savedProgress?.chapterIndex
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
        if (nextChapter.content.isNotBlank() || !nextChapter.imageUri.isNullOrBlank()) return
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
        private const val STRUCTURED_FORMAT = "structured"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                ReaderViewModel(
                    app,
                    locator.bookRepository,
                    locator.readerSettingsStore,
                    locator.serverRepository,
                    locator.readerBookmarkRepository,
                    locator.readerHighlightRepository,
                    locator.bookContentLoaderRegistry
                )
            }
        }
    }
}
