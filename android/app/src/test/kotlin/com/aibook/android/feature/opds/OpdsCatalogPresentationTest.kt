package com.aibook.android.feature.opds

import com.aibook.android.core.network.opds.OpdsEntry
import com.aibook.android.core.network.opds.OpdsLink
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsCatalogPresentationTest {
    private val epub = OpdsLink("/books/a.epub", "application/epub+zip")
    private val pdf = OpdsLink("/books/b.pdf", "application/pdf")

    @Test
    fun `filters by format and sorts by latest modified`() {
        val entries = listOf(
            OpdsEntry("旧 EPUB", acquisitionLink = epub, modifiedAt = "2026-07-01T10:00:00Z"),
            OpdsEntry("PDF", acquisitionLink = pdf, modifiedAt = "2026-07-15T10:00:00Z"),
            OpdsEntry("新 EPUB", acquisitionLink = epub, modifiedAt = "2026-07-14T10:00:00Z")
        )

        val result = presentOpdsEntries(entries, "EPUB", OpdsCatalogSort.MODIFIED)

        assertEquals(listOf("新 EPUB", "旧 EPUB"), result.map { it.title })
    }

    @Test
    fun `formats real opds timestamp in selected timezone`() {
        assertEquals(
            "2026-07-15 18:30",
            formatOpdsModifiedAt("2026-07-15T10:30:00Z", ZoneId.of("Asia/Shanghai"))
        )
        assertEquals("更新时间未知", formatOpdsModifiedAt(null))
    }
}
