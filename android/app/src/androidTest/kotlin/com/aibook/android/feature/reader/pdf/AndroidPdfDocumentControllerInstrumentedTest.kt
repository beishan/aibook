package com.aibook.android.feature.reader.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPdfDocumentControllerInstrumentedTest {

    @Test
    fun opensAndRendersRuntimeGeneratedOnePagePdf() = withTempDirectory { directory ->
        val pdf = File(directory, "one-page.pdf")
        createOnePagePdf(pdf, width = 300, height = 500)
        val controller = AndroidPdfDocumentController()

        try {
            val opened = runBlocking { controller.open(pdf) }
            assertTrue(opened is PdfDocumentResult.Success)
            assertEquals(1, (opened as PdfDocumentResult.Success).value.pageCount)

            val rendered = runBlocking { controller.render(pageIndex = 0, targetWidthPx = 360) }
            assertTrue(rendered is PdfDocumentResult.Success)
            val page = (rendered as PdfDocumentResult.Success).value
            assertEquals(0, page.pageIndex)
            assertEquals(360, page.bitmap.width)
            assertEquals(600, page.bitmap.height)
            page.bitmap.recycle()
        } finally {
            runBlocking { controller.close() }
        }
    }

    @Test
    fun corruptBytesReturnFailure() = withTempDirectory { directory ->
        val corrupt = File(directory, "corrupt.pdf").apply {
            writeBytes("not a pdf".encodeToByteArray())
        }
        val controller = AndroidPdfDocumentController()

        try {
            val result = runBlocking { controller.open(corrupt) }
            assertTrue(result is PdfDocumentResult.Failure)
        } finally {
            runBlocking { controller.close() }
        }
    }

    private fun createOnePagePdf(file: File, width: Int, height: Int) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("AiBook PDF", 24f, 48f, Paint().apply { color = Color.BLACK })
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "pdf-controller-test-${System.nanoTime()}")
        try {
            assertTrue(directory.mkdirs())
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
