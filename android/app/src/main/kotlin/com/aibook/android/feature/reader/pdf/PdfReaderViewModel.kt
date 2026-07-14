package com.aibook.android.feature.reader.pdf

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aibook.android.core.data.repository.ReaderBookmarkRepository
import com.aibook.android.core.model.PdfReadingPosition
import com.aibook.android.core.reader.ReaderBookmark
import com.aibook.android.core.reader.BookContentError
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class PdfReaderViewModel(
    private val coordinator: PdfReaderCoordinator,
    private val bookmarkRepository: ReaderBookmarkRepository,
    private val bitmapCache: PdfPageBitmapCache
) : ViewModel() {

    val state: StateFlow<PdfReaderState> = coordinator.state

    private val _renderedPages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val renderedPages: StateFlow<Map<Int, Bitmap>> = _renderedPages.asStateFlow()
    private val _pageErrors = MutableStateFlow<Map<Int, BookContentError>>(emptyMap())
    val pageErrors: StateFlow<Map<Int, BookContentError>> = _pageErrors.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<ReaderBookmark>>(emptyList())
    val bookmarks: StateFlow<List<ReaderBookmark>> = _bookmarks.asStateFlow()

    private val renderRequests = PdfRenderRequestTracker()
    private val pageOffsets = PdfPageOffsetStore()
    private var visibleCenterPage = 0
    private var bookmarkJob: Job? = null
    private var progressSaveJob: Job? = null

    fun open(bookId: String) {
        viewModelScope.launch {
            coordinator.open(bookId)
            state.value.book?.let { book ->
                visibleCenterPage = state.value.currentPage
                pageOffsets.restore(state.value.currentPage, state.value.scrollOffset)
                bookmarkJob?.cancel()
                bookmarkJob = launch {
                    bookmarkRepository.observeForBook(book.id).collect { _bookmarks.value = it }
                }
            }
        }
    }

    fun requestPage(pageIndex: Int, targetWidthPx: Int) {
        if (pageIndex !in 0 until state.value.pageCount || targetWidthPx <= 0) return
        val key = PdfPageBitmapCache.Key(pageIndex, targetWidthPx)
        if (!renderRequests.begin(key)) return
        bitmapCache.get(pageIndex, targetWidthPx)?.let { cached ->
            if (PdfRenderWindow.contains(pageIndex, visibleCenterPage)) {
                _renderedPages.value = _renderedPages.value + (pageIndex to cached)
            }
            renderRequests.complete(key)
            return
        }
        viewModelScope.launch {
            try {
                when (val result = coordinator.render(pageIndex, targetWidthPx)) {
                    is PdfDocumentResult.Success -> {
                        val isVisible = PdfRenderWindow.contains(pageIndex, visibleCenterPage)
                        val shouldPublish = isVisible && renderRequests.isLatest(key)
                        if (shouldPublish) {
                            bitmapCache.put(pageIndex, targetWidthPx, result.value.bitmap)
                            _renderedPages.value = _renderedPages.value + (pageIndex to result.value.bitmap)
                            _pageErrors.value = _pageErrors.value - pageIndex
                        } else result.value.bitmap.recycle()
                    }
                    is PdfDocumentResult.Failure -> if (renderRequests.isLatest(key)) {
                        _pageErrors.value = _pageErrors.value + (pageIndex to result.error)
                    }
                }
            } finally {
                renderRequests.complete(key)
            }
        }
    }

    fun onPageVisible(pageIndex: Int, scrollOffset: Int) {
        visibleCenterPage = pageIndex
        val retainedPages = PdfRenderWindow.pageIndexes(pageIndex, state.value.pageCount)
        _renderedPages.value = PdfRenderWindow.retain(_renderedPages.value, pageIndex, state.value.pageCount)
        _pageErrors.value = PdfRenderWindow.retain(_pageErrors.value, pageIndex, state.value.pageCount)
        bitmapCache.retainPages(retainedPages)
        pageOffsets.update(pageIndex, scrollOffset)
        viewModelScope.launch {
            coordinator.onPageVisible(pageIndex, pageOffsets.offsetFor(pageIndex), persist = false)
            scheduleProgressSave()
        }
    }

    fun setZoom(zoom: Float) {
        viewModelScope.launch {
            coordinator.setZoom(zoom, persist = false)
            scheduleProgressSave()
        }
    }

    fun navigationTarget(pageIndex: Int): PdfPageNavigationTarget {
        val current = state.value
        if (pageIndex == current.currentPage) {
            pageOffsets.restoreIfAbsent(pageIndex, current.scrollOffset)
        }
        return pageOffsets.navigationTarget(pageIndex, current.pageCount)
    }

    fun toggleBookmark() {
        val current = state.value
        val book = current.book ?: return
        val existing = _bookmarks.value.firstOrNull { it.chapterIndex == current.currentPage }
        viewModelScope.launch {
            if (existing != null) {
                bookmarkRepository.remove(existing.id)
            } else {
                bookmarkRepository.add(
                    ReaderBookmark(
                        bookId = book.id,
                        chapterHref = "pdf:${current.currentPage}",
                        chapterTitle = "第 ${current.currentPage + 1} 页",
                        progress = PdfReadingPosition.progress(current.currentPage, current.pageCount),
                        chapterIndex = current.currentPage,
                        scrollOffset = current.scrollOffset
                    )
                )
            }
        }
    }

    fun close() {
        progressSaveJob?.cancel()
        renderRequests.clear()
        viewModelScope.launch {
            coordinator.saveCurrentPosition()
            coordinator.close()
        }
    }

    private fun scheduleProgressSave() {
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(400)
            coordinator.saveCurrentPosition()
        }
    }

    override fun onCleared() {
        bookmarkJob?.cancel()
        progressSaveJob?.cancel()
        renderRequests.clear()
        runBlocking {
            coordinator.saveCurrentPosition()
            coordinator.close()
        }
        bitmapCache.clear()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val locator = ServiceLocator.get(app)
                val budget = PdfRenderSizing.cacheBudget(Runtime.getRuntime().maxMemory())
                val singleBitmapBudget = PdfRenderSizing.singleBitmapBudget(budget)
                PdfReaderViewModel(
                    coordinator = PdfReaderCoordinator(
                        gateway = RepositoryPdfBookGateway(locator.bookRepository),
                        documentController = AndroidPdfDocumentController(singleBitmapBudget)
                    ),
                    bookmarkRepository = locator.readerBookmarkRepository,
                    bitmapCache = PdfPageBitmapCache(budget)
                )
            }
        }
    }
}
