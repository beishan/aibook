package com.aibook.android.core.model

enum class ShelfSortOption(val label: String) {
    RECENT_READ("最近阅读"),
    IMPORTED_AT("最近加入"),
    TITLE("书名 A-Z"),
    AUTHOR("作者 A-Z"),
    PROGRESS("阅读进度")
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
            ShelfSortOption.AUTHOR -> books.sortedWith(compareBy<LocalBook> { it.author.orEmpty().lowercase() }.thenBy { it.title.lowercase() })
            ShelfSortOption.PROGRESS -> books.sortedByDescending { it.progress.percent }
        }
    }
}
