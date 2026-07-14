package com.aibook.android.core.reader

import com.aibook.android.core.model.BookFormat

class BookContentLoaderRegistry(
    private val loaders: List<BookContentLoader>
) {
    fun loaderFor(format: BookFormat): BookContentLoader? =
        loaders.firstOrNull { format in it.supportedFormats }
}
