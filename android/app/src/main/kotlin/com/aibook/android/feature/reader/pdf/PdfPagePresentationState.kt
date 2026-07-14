package com.aibook.android.feature.reader.pdf

data class PdfPageNavigationTarget(
    val pageIndex: Int,
    val scrollOffset: Int
)

class PdfPageOffsetStore {
    private val offsets = mutableMapOf<Int, Int>()

    fun restore(pageIndex: Int, offsetY: Int) {
        offsets[pageIndex] = offsetY.coerceAtLeast(0)
    }

    fun update(pageIndex: Int, offsetY: Int) {
        offsets[pageIndex] = offsetY.coerceAtLeast(0)
    }

    fun offsetFor(pageIndex: Int): Int = offsets[pageIndex] ?: 0

    fun restoreIfAbsent(pageIndex: Int, offsetY: Int) {
        offsets.putIfAbsent(pageIndex, offsetY.coerceAtLeast(0))
    }

    fun navigationTarget(requestedPage: Int, pageCount: Int): PdfPageNavigationTarget {
        val page = if (pageCount <= 0) 0 else requestedPage.coerceIn(0, pageCount - 1)
        return PdfPageNavigationTarget(page, offsetFor(page))
    }
}

object PdfRenderWindow {
    private const val RADIUS = 1
    const val MAX_PAGE_COUNT = RADIUS * 2 + 1

    fun contains(pageIndex: Int, currentPage: Int): Boolean =
        kotlin.math.abs(pageIndex - currentPage) <= RADIUS

    fun pageIndexes(currentPage: Int, pageCount: Int): Set<Int> {
        if (pageCount <= 0) return emptySet()
        val center = currentPage.coerceIn(0, pageCount - 1)
        return ((center - RADIUS).coerceAtLeast(0)..(center + RADIUS).coerceAtMost(pageCount - 1)).toSet()
    }

    fun <T> retain(values: Map<Int, T>, currentPage: Int, pageCount: Int): Map<Int, T> {
        val retainedPages = pageIndexes(currentPage, pageCount)
        return values.filterKeys { it in retainedPages }
    }
}

class PdfRenderRequestTracker {
    private val inFlight = mutableSetOf<PdfPageBitmapCache.Key>()
    private val latestByPage = mutableMapOf<Int, PdfPageBitmapCache.Key>()

    fun begin(key: PdfPageBitmapCache.Key): Boolean {
        latestByPage[key.pageIndex] = key
        return inFlight.add(key)
    }

    fun isLatest(key: PdfPageBitmapCache.Key): Boolean = latestByPage[key.pageIndex] == key

    fun complete(key: PdfPageBitmapCache.Key) {
        inFlight.remove(key)
    }

    fun clear() {
        inFlight.clear()
        latestByPage.clear()
    }
}
