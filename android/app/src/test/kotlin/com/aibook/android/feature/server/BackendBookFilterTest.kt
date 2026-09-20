package com.aibook.android.feature.server

import com.aibook.android.core.network.api.dto.BookDTO
import kotlin.test.Test
import kotlin.test.assertEquals

class BackendBookFilterTest {
    private val books = listOf(
        BookDTO(id = 1, title = "三体", author = "刘慈欣", format = "EPUB", tagNames = listOf("科幻")),
        BookDTO(id = 2, title = "活着", author = "余华", format = "TXT", categoryName = "文学"),
        BookDTO(id = 3, title = "连载小说", author = "作者甲", format = "structured", tagNames = listOf("追更"))
    )

    @Test
    fun `searches title author category and tags`() {
        assertEquals(listOf(1L), filterBackendBooks(books, "科幻", BackendFormatFilter.ALL, 0, emptySet()).map { it.id })
        assertEquals(listOf(2L), filterBackendBooks(books, "余华", BackendFormatFilter.ALL, 0, emptySet()).map { it.id })
        assertEquals(listOf(3L), filterBackendBooks(books, "追更", BackendFormatFilter.ALL, 0, emptySet()).map { it.id })
    }

    @Test
    fun `combines format and shelf filters`() {
        assertEquals(
            listOf(1L),
            filterBackendBooks(books, "", BackendFormatFilter.EPUB, 1, setOf(1L, 2L)).map { it.id }
        )
        assertEquals(
            listOf(3L),
            filterBackendBooks(books, "", BackendFormatFilter.STRUCTURED, 2, setOf(1L, 2L)).map { it.id }
        )
    }
}
