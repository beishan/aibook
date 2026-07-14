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
}
