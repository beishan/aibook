package com.aibook.android.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfBookSorterTest {

    @Test
    fun favoriteFirstKeepsFavoritesBeforeOtherBooks() {
        val books = listOf(
            book("b", "普通书", favorite = false),
            book("a", "收藏书", favorite = true)
        )

        val sorted = ShelfBookSorter.sort(books, ShelfSortOption.FAVORITE_FIRST)

        assertEquals(listOf("收藏书", "普通书"), sorted.map { it.title })
    }

    @Test
    fun titleSortUsesBookTitleAscending() {
        val books = listOf(
            book("b", "Zoo"),
            book("a", "Alpha")
        )

        val sorted = ShelfBookSorter.sort(books, ShelfSortOption.TITLE)

        assertEquals(listOf("Alpha", "Zoo"), sorted.map { it.title })
    }

    private fun book(
        id: String,
        title: String,
        favorite: Boolean = false
    ): LocalBook {
        return LocalBook(
            id = id,
            title = title,
            format = BookFormat.EPUB,
            uri = "/tmp/$id.epub",
            favorite = favorite,
            importedAt = Instant.ofEpochMilli(id.first().code.toLong())
        )
    }
}
