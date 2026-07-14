package com.aibook.android.core.model

enum class ShelfSortOption(val label: String) {
    RECENT_READ("最近阅读"),
    IMPORTED_AT("添加时间"),
    TITLE("书名"),
    FAVORITE_FIRST("收藏优先")
}

object ShelfBookSorter {
    fun sort(books: List<LocalBook>, option: ShelfSortOption): List<LocalBook> {
        return when (option) {
            ShelfSortOption.RECENT_READ -> books.sortedWith(
                compareByDescending<LocalBook> { it.lastReadAt ?: it.importedAt }
                    .thenBy { it.title.lowercase() }
            )
            ShelfSortOption.IMPORTED_AT -> books.sortedWith(
                compareByDescending<LocalBook> { it.importedAt }
                    .thenBy { it.title.lowercase() }
            )
            ShelfSortOption.TITLE -> books.sortedBy { it.title.lowercase() }
            ShelfSortOption.FAVORITE_FIRST -> books.sortedWith(
                compareByDescending<LocalBook> { it.favorite }
                    .thenByDescending { it.lastReadAt ?: it.importedAt }
                    .thenBy { it.title.lowercase() }
            )
        }
    }
}
