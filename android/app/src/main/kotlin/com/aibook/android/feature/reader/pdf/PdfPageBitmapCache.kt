package com.aibook.android.feature.reader.pdf

import android.graphics.Bitmap
import android.util.LruCache

class PdfPageBitmapCache(maxBytes: Int) {

    data class Key(val pageIndex: Int, val targetWidthPx: Int)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(pageIndex: Int, targetWidthPx: Int): Bitmap? = cache.get(Key(pageIndex, targetWidthPx))

    fun put(pageIndex: Int, targetWidthPx: Int, bitmap: Bitmap) {
        val key = Key(pageIndex, targetWidthPx)
        cache.snapshot().keys
            .filter { it.pageIndex == pageIndex && it != key }
            .forEach { cache.remove(it) }
        cache.put(key, bitmap)
    }

    fun retainPages(pageIndexes: Set<Int>) {
        cache.snapshot().keys
            .filterNot { it.pageIndex in pageIndexes }
            .forEach { cache.remove(it) }
    }

    fun clear() {
        cache.snapshot().values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        cache.evictAll()
    }
}
