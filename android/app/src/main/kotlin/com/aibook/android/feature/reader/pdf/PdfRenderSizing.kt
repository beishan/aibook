package com.aibook.android.feature.reader.pdf

import kotlin.math.sqrt

object PdfRenderSizing {

    private const val ABSOLUTE_CACHE_CAP_BYTES = 32L * 1024 * 1024
    private const val MAX_RENDER_DIMENSION_PX = 4096

    fun heightFor(pageWidth: Int, pageHeight: Int, targetWidth: Int): Int {
        val safePageWidth = pageWidth.coerceAtLeast(1)
        val safePageHeight = pageHeight.coerceAtLeast(1)
        val safeTargetWidth = targetWidth.coerceAtLeast(1)
        return ((safeTargetWidth.toLong() * safePageHeight) / safePageWidth)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun cacheBudget(maxHeapBytes: Long): Int =
        (maxHeapBytes.coerceAtLeast(8L) / 8L)
            .coerceAtMost(ABSOLUTE_CACHE_CAP_BYTES)
            .coerceAtLeast(1L)
            .toInt()

    fun targetWidthForZoom(viewportWidth: Int, zoom: Float): Int =
        (viewportWidth.coerceAtLeast(1) * zoom.coerceIn(1f, 4f))
            .toInt()
            .coerceIn(1, MAX_RENDER_DIMENSION_PX)

    fun displayHeightFor(
        bitmapWidth: Int,
        bitmapHeight: Int,
        viewportWidth: Int,
        zoom: Float
    ): Int {
        val baseHeight = heightFor(bitmapWidth, bitmapHeight, viewportWidth)
        return (baseHeight * zoom.coerceIn(1f, 4f))
            .toInt()
            .coerceAtLeast(1)
    }

    fun widthWithinBitmapBudget(
        pageWidth: Int,
        pageHeight: Int,
        requestedWidth: Int,
        maxBitmapBytes: Int
    ): Int {
        val safePageWidth = pageWidth.coerceAtLeast(1)
        val safePageHeight = pageHeight.coerceAtLeast(1)
        val aspectHeightPerWidth = safePageHeight.toDouble() / safePageWidth
        val maximumWidth = sqrt(maxBitmapBytes.coerceAtLeast(1) / (4.0 * aspectHeightPerWidth))
            .toInt()
            .coerceAtLeast(1)
        return requestedWidth.coerceAtLeast(1).coerceAtMost(maximumWidth)
    }

    fun singleBitmapBudget(totalCacheBudgetBytes: Int): Int =
        (totalCacheBudgetBytes.coerceAtLeast(1) / PdfRenderWindow.MAX_PAGE_COUNT)
            .coerceAtLeast(1)
}
