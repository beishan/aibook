package com.aibook.android.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderRepositoryTest {
    @Test
    fun `adds supported imported books and rejects duplicate sha256`() {
        val repository = ReaderRepository()

        val first = repository.addImportedBook(
            fileName = "三体.epub",
            uri = "content://books/three-body",
            sha256 = "same-hash"
        )
        val duplicate = repository.addImportedBook(
            fileName = "三体-copy.epub",
            uri = "content://books/three-body-copy",
            sha256 = "same-hash"
        )

        assertIs<ImportResult.Added>(first)
        assertIs<ImportResult.Duplicate>(duplicate)
        assertEquals("三体", first.book.title)
        assertEquals(1, repository.listBooks().size)
    }

    @Test
    fun `rejects unsupported imported books`() {
        val repository = ReaderRepository()

        val result = repository.addImportedBook(
            fileName = "cover.jpg",
            uri = "content://books/cover",
            sha256 = "image-hash"
        )

        assertIs<ImportResult.UnsupportedFormat>(result)
        assertEquals(0, repository.listBooks().size)
    }
}
