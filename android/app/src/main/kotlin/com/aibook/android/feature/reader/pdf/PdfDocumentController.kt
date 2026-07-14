package com.aibook.android.feature.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aibook.android.core.reader.BookContentError
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfDocumentInfo(val pageCount: Int)

data class PdfRenderedPage(
    val pageIndex: Int,
    val bitmap: Bitmap
)

sealed interface PdfDocumentResult<out T> {
    data class Success<T>(val value: T) : PdfDocumentResult<T>
    data class Failure(val error: BookContentError) : PdfDocumentResult<Nothing>
}

interface PdfDocumentController {
    suspend fun open(file: File): PdfDocumentResult<PdfDocumentInfo>
    suspend fun render(pageIndex: Int, targetWidthPx: Int): PdfDocumentResult<PdfRenderedPage>
    suspend fun close()
}

class AndroidPdfDocumentController(
    private val maxBitmapBytes: Int = PdfRenderSizing.singleBitmapBudget(
        PdfRenderSizing.cacheBudget(Runtime.getRuntime().maxMemory())
    )
) : PdfDocumentController {

    private val lock = Any()
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    override suspend fun open(file: File): PdfDocumentResult<PdfDocumentInfo> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext PdfDocumentResult.Failure(BookContentError.FileMissing)
        synchronized(lock) {
            closeLocked()
            try {
                val openedDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val openedRenderer = PdfRenderer(openedDescriptor)
                descriptor = openedDescriptor
                renderer = openedRenderer
                PdfDocumentResult.Success(PdfDocumentInfo(openedRenderer.pageCount))
            } catch (_: SecurityException) {
                closeLocked()
                PdfDocumentResult.Failure(BookContentError.PasswordProtected)
            } catch (_: IOException) {
                closeLocked()
                PdfDocumentResult.Failure(BookContentError.CorruptedFile)
            } catch (_: IllegalArgumentException) {
                closeLocked()
                PdfDocumentResult.Failure(BookContentError.CorruptedFile)
            }
        }
    }

    override suspend fun render(
        pageIndex: Int,
        targetWidthPx: Int
    ): PdfDocumentResult<PdfRenderedPage> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val activeRenderer = renderer
                ?: return@synchronized PdfDocumentResult.Failure(BookContentError.ParseFailed("PDF 尚未打开"))
            if (pageIndex !in 0 until activeRenderer.pageCount) {
                return@synchronized PdfDocumentResult.Failure(BookContentError.ParseFailed("PDF 页码无效"))
            }
            try {
                activeRenderer.openPage(pageIndex).use { page ->
                    val width = PdfRenderSizing.widthWithinBitmapBudget(
                        pageWidth = page.width,
                        pageHeight = page.height,
                        requestedWidth = targetWidthPx,
                        maxBitmapBytes = maxBitmapBytes
                    )
                    val height = PdfRenderSizing.heightFor(page.width, page.height, width)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    PdfDocumentResult.Success(PdfRenderedPage(pageIndex, bitmap))
                }
            } catch (_: OutOfMemoryError) {
                PdfDocumentResult.Failure(BookContentError.InsufficientStorage)
            } catch (_: Exception) {
                PdfDocumentResult.Failure(BookContentError.CorruptedFile)
            }
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        synchronized(lock) { closeLocked() }
    }

    private fun closeLocked() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { descriptor?.close() }
        descriptor = null
    }
}
