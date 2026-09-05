package com.aibook.android.feature.shelf

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.model.ShelfFolderSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfUiStateTest {
    private val localBook = LocalBook(
        id = "local-1",
        title = "本地书",
        format = BookFormat.EPUB,
        uri = "/books/local.epub",
        shelved = true
    )
    private val remoteBook = LocalBook(
        id = "server:7",
        title = "远程书",
        format = BookFormat.TXT,
        uri = "",
        favorite = true,
        shelved = true
    )

    @Test
    fun `all shelf merges local and remote books`() {
        val state = ShelfUiState(
            books = listOf(localBook),
            visibleBooks = listOf(localBook),
            remoteBooks = listOf(remoteBook)
        )

        assertEquals(setOf("local-1", "server:7"), state.filteredBooks.map { it.id }.toSet())
    }

    @Test
    fun `favorites filter includes favorite remote books`() {
        val state = ShelfUiState(
            books = listOf(localBook),
            visibleBooks = emptyList(),
            remoteBooks = listOf(remoteBook),
            folderSelection = ShelfFolderSelection.Favorites
        )

        assertEquals(listOf("server:7"), state.filteredBooks.map { it.id })
    }
}
