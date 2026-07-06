package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfFolderCatalogTest {

    @Test
    fun filterBooksByAllUnfiledAndFolder() {
        val books = listOf(
            shelfBook("book-1", "未分组", folderId = null),
            shelfBook("book-2", "科幻", folderId = "folder-sci-fi"),
            shelfBook("book-3", "历史", folderId = "folder-history")
        )

        assertEquals(listOf("未分组", "科幻", "历史"), ShelfFolderCatalog.filterBooks(books, ShelfFolderSelection.All).map { it.title })
        assertEquals(listOf("未分组"), ShelfFolderCatalog.filterBooks(books, ShelfFolderSelection.Unfiled).map { it.title })
        assertEquals(listOf("科幻"), ShelfFolderCatalog.filterBooks(books, ShelfFolderSelection.Folder("folder-sci-fi")).map { it.title })
    }

    @Test
    fun folderBookCountsIncludeOnlyBooksInMatchingFolder() {
        val counts = ShelfFolderCatalog.folderCounts(
            listOf(
                shelfBook("book-1", "A", folderId = "folder-a"),
                shelfBook("book-2", "B", folderId = "folder-a"),
                shelfBook("book-3", "C", folderId = null)
            )
        )

        assertEquals(2, counts["folder-a"])
        assertEquals(null, counts["folder-missing"])
    }

    private fun shelfBook(id: String, title: String, folderId: String?) = LocalBook(
        id = id,
        title = title,
        format = BookFormat.EPUB,
        uri = "/tmp/$id.epub",
        folderId = folderId
    )
}
