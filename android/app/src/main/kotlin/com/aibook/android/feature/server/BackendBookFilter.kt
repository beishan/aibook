package com.aibook.android.feature.server

import com.aibook.android.core.network.api.dto.BookDTO

enum class BackendFormatFilter(val label: String) {
    ALL("全部"),
    EPUB("EPUB"),
    TXT("TXT"),
    STRUCTURED("结构化")
}

internal fun filterBackendBooks(
    books: List<BookDTO>,
    query: String,
    format: BackendFormatFilter,
    shelfFilter: Int,
    shelfBookIds: Set<Long>
): List<BookDTO> {
    val keyword = query.trim()
    return books.filter { book ->
        val matchesShelf = when (shelfFilter) {
            1 -> book.id in shelfBookIds
            2 -> book.id !in shelfBookIds
            else -> true
        }
        val matchesFormat = format == BackendFormatFilter.ALL ||
            book.format.equals(format.name, ignoreCase = true)
        val matchesKeyword = keyword.isBlank() || sequenceOf(
            book.title,
            book.author.orEmpty(),
            book.categoryName.orEmpty(),
            book.description.orEmpty()
        ).any { it.contains(keyword, ignoreCase = true) } ||
            book.tagNames.orEmpty().any { it.contains(keyword, ignoreCase = true) }
        matchesShelf && matchesFormat && matchesKeyword
    }
}
