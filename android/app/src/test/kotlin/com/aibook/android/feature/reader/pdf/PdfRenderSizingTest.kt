package com.aibook.android.feature.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfRenderSizingTest {

    @Test
    fun renderHeightPreservesAspectRatio() {
        assertEquals(1500, PdfRenderSizing.heightFor(600, 900, 1000))
    }

    @Test
    fun invalidDimensionsStillProduceRenderableBitmap() {
        assertEquals(1, PdfRenderSizing.heightFor(0, 0, 0))
    }

    @Test
    fun bitmapBudgetUsesHeapFractionWithAbsoluteCap() {
        assertEquals(32 * 1024 * 1024, PdfRenderSizing.cacheBudget(512L * 1024 * 1024))
        assertEquals(8 * 1024 * 1024, PdfRenderSizing.cacheBudget(64L * 1024 * 1024))
    }

    @Test
    fun zoomRequestsMorePixelsWithoutExceedingDimensionCap() {
        assertEquals(2700, PdfRenderSizing.targetWidthForZoom(1080, 2.5f))
        assertEquals(4096, PdfRenderSizing.targetWidthForZoom(1440, 4f))
    }

    @Test
    fun zoomedPageReservesScaledVerticalLayoutSpace() {
        assertEquals(
            3000,
            PdfRenderSizing.displayHeightFor(
                bitmapWidth = 1000,
                bitmapHeight = 1500,
                viewportWidth = 1000,
                zoom = 2f
            )
        )
    }

    @Test
    fun renderWidthIsReducedWhenPageAspectWouldExceedBitmapBudget() {
        val totalBudget = 8 * 1024 * 1024
        val perBitmapBudget = PdfRenderSizing.singleBitmapBudget(totalBudget)
        assertEquals(
            682,
            PdfRenderSizing.widthWithinBitmapBudget(
                pageWidth = 1000,
                pageHeight = 1500,
                requestedWidth = 4096,
                maxBitmapBytes = perBitmapBudget
            )
        )
        assertEquals(2_796_202, perBitmapBudget)
        assertTrue(perBitmapBudget <= totalBudget)
    }

    @Test
    fun visibleRenderWindowNeverKeepsMoreThanThreePages() {
        assertEquals(setOf(4, 5, 6), PdfRenderWindow.pageIndexes(currentPage = 5, pageCount = 20))
        assertEquals(setOf(0, 1), PdfRenderWindow.pageIndexes(currentPage = 0, pageCount = 20))
        assertTrue(PdfRenderWindow.contains(pageIndex = 6, currentPage = 5))
        assertFalse(PdfRenderWindow.contains(pageIndex = 7, currentPage = 5))
        assertEquals(
            setOf(4, 5, 6),
            PdfRenderWindow.retain((0..10).associateWith { "page-$it" }, currentPage = 5, pageCount = 20).keys
        )
    }
}
