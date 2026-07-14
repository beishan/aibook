package com.aibook.android.feature.reader.pdf

import com.aibook.android.core.data.repository.BookRepository
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.PdfReadingPosition
import com.aibook.android.core.reader.BookContentError
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PdfReaderState(
    val book: LocalBook? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val scrollOffset: Int = 0,
    val zoom: Float = 1f,
    val isLoading: Boolean = false,
    val error: BookContentError? = null
)

data class PdfProgressUpdate(
    val bookId: String,
    val pageIndex: Int,
    val pageCount: Int,
    val scrollOffset: Int,
    val zoom: Float
)

interface PdfBookGateway {
    suspend fun getBook(bookId: String): LocalBook?
    suspend fun saveProgress(update: PdfProgressUpdate)
}

class RepositoryPdfBookGateway(
    private val repository: BookRepository
) : PdfBookGateway {
    override suspend fun getBook(bookId: String): LocalBook? = repository.getBook(bookId)

    override suspend fun saveProgress(update: PdfProgressUpdate) {
        repository.updateProgress(
            bookId = update.bookId,
            chapterHref = "pdf:${update.pageIndex}",
            chapterTitle = "第 ${update.pageIndex + 1} 页",
            percent = PdfReadingPosition.progress(update.pageIndex, update.pageCount),
            chapterIndex = update.pageIndex,
            lineIndex = 0,
            scrollOffset = update.scrollOffset,
            pdfZoom = update.zoom
        )
    }
}

class PdfReaderCoordinator(
    private val gateway: PdfBookGateway,
    private val documentController: PdfDocumentController
) {
    private val _state = MutableStateFlow(PdfReaderState())
    val state: StateFlow<PdfReaderState> = _state.asStateFlow()

    suspend fun open(bookId: String) {
        _state.value = PdfReaderState(isLoading = true)
        val book = gateway.getBook(bookId)
        if (book == null) {
            _state.value = PdfReaderState(error = BookContentError.FileMissing)
            return
        }
        when (val result = documentController.open(File(book.uri))) {
            is PdfDocumentResult.Success -> {
                val pageCount = result.value.pageCount
                _state.value = PdfReaderState(
                    book = book,
                    pageCount = pageCount,
                    currentPage = PdfReadingPosition.clampPage(book.progress.chapterIndex ?: 0, pageCount),
                    scrollOffset = book.progress.scrollOffset.coerceAtLeast(0),
                    zoom = (book.progress.pdfZoom ?: 1f).coerceIn(1f, 4f)
                )
            }
            is PdfDocumentResult.Failure -> {
                _state.value = PdfReaderState(book = book, error = result.error)
            }
        }
    }

    suspend fun onPageVisible(pageIndex: Int, scrollOffset: Int, persist: Boolean = true) {
        val current = _state.value
        val book = current.book ?: return
        val clampedPage = PdfReadingPosition.clampPage(pageIndex, current.pageCount)
        _state.value = current.copy(
            currentPage = clampedPage,
            scrollOffset = scrollOffset.coerceAtLeast(0)
        )
        if (persist) gateway.saveProgress(
            PdfProgressUpdate(
                bookId = book.id,
                pageIndex = clampedPage,
                pageCount = current.pageCount,
                scrollOffset = scrollOffset.coerceAtLeast(0),
                zoom = current.zoom
            )
        )
    }

    suspend fun setZoom(zoom: Float, persist: Boolean = true) {
        val current = _state.value
        val clamped = zoom.coerceIn(1f, 4f)
        _state.value = current.copy(zoom = clamped)
        current.book?.takeIf { persist }?.let {
            gateway.saveProgress(
                PdfProgressUpdate(
                    bookId = it.id,
                    pageIndex = current.currentPage,
                    pageCount = current.pageCount,
                    scrollOffset = current.scrollOffset,
                    zoom = clamped
                )
            )
        }
    }

    suspend fun render(pageIndex: Int, targetWidthPx: Int): PdfDocumentResult<PdfRenderedPage> =
        documentController.render(pageIndex, targetWidthPx)

    suspend fun saveCurrentPosition() {
        val current = _state.value
        val book = current.book ?: return
        gateway.saveProgress(
            PdfProgressUpdate(
                bookId = book.id,
                pageIndex = current.currentPage,
                pageCount = current.pageCount,
                scrollOffset = current.scrollOffset,
                zoom = current.zoom
            )
        )
    }

    suspend fun close() {
        documentController.close()
    }
}
