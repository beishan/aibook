package com.aibook.android.core.data.mapper

import com.aibook.android.core.data.db.BookEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityMappersPdfProgressTest {

    @Test
    fun pdfZoomRoundTripsThroughBookEntity() {
        val entity = BookEntity(
            id = "pdf-book",
            title = "PDF",
            format = "PDF",
            uri = "/tmp/book.pdf",
            progressChapterIndex = 7,
            progressPdfZoom = 2.25f
        )

        val domain = entity.toDomain()

        assertEquals(2.25f, domain.progress.pdfZoom)
        assertEquals(2.25f, domain.toEntity().progressPdfZoom)
    }

    @Test
    fun editableRatingAndTagsRoundTripThroughBookEntity() {
        val entity = BookEntity(
            id = "rated-book",
            title = "有评分的书",
            format = "EPUB",
            uri = "/tmp/book.epub",
            rating = 8.5f,
            tags = "科幻|经典|科幻"
        )

        val domain = entity.toDomain()

        assertEquals(8.5f, domain.rating)
        assertEquals(listOf("科幻", "经典"), domain.tags)
        assertEquals("科幻|经典", domain.toEntity().tags)
    }
}
