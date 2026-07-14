package com.aibook.android.core.model

object PdfReadingPosition {

    fun progress(pageIndex: Int, pageCount: Int): Float =
        if (pageCount <= 1) {
            0f
        } else {
            pageIndex.coerceIn(0, pageCount - 1).toFloat() / (pageCount - 1)
        }

    fun clampPage(pageIndex: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else pageIndex.coerceIn(0, pageCount - 1)
}
