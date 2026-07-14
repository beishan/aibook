package com.aibook.android.feature.reader.pdf

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ReadingProgress
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PdfReaderCoordinatorTest {

    @Test
    fun openingRestoresClampedPageAndZoom() = runTest {
        val gateway = FakeGateway(book(page = 50, zoom = 2f, offset = 180))
        val coordinator = PdfReaderCoordinator(gateway, FakeController(pageCount = 10))

        coordinator.open("pdf-book")

        assertEquals(9, coordinator.state.value.currentPage)
        assertEquals(2f, coordinator.state.value.zoom)
        assertEquals(180, coordinator.state.value.scrollOffset)
        assertEquals(10, coordinator.state.value.pageCount)
    }

    @Test
    fun changingPagePersistsPdfProgress() = runTest {
        val gateway = FakeGateway(book(page = 0, zoom = 1f))
        val coordinator = PdfReaderCoordinator(gateway, FakeController(pageCount = 10))
        coordinator.open("pdf-book")

        coordinator.onPageVisible(pageIndex = 4, scrollOffset = 120)

        assertEquals(PdfProgressUpdate("pdf-book", 4, 10, 120, 1f), gateway.lastUpdate)
        assertEquals(4, coordinator.state.value.currentPage)
    }

    private fun book(page: Int, zoom: Float, offset: Int = 0): LocalBook = LocalBook(
        id = "pdf-book",
        title = "Test PDF",
        format = BookFormat.PDF,
        uri = File("build/test.pdf").absolutePath,
        progress = ReadingProgress(chapterIndex = page, pdfZoom = zoom, scrollOffset = offset)
    )

    private class FakeGateway(private val book: LocalBook) : PdfBookGateway {
        var lastUpdate: PdfProgressUpdate? = null

        override suspend fun getBook(bookId: String): LocalBook = book

        override suspend fun saveProgress(update: PdfProgressUpdate) {
            lastUpdate = update
        }
    }

    private class FakeController(private val pageCount: Int) : PdfDocumentController {
        override suspend fun open(file: File): PdfDocumentResult<PdfDocumentInfo> =
            PdfDocumentResult.Success(PdfDocumentInfo(pageCount))

        override suspend fun render(
            pageIndex: Int,
            targetWidthPx: Int
        ): PdfDocumentResult<PdfRenderedPage> = error("not used")

        override suspend fun close() = Unit
    }
}
