package com.aibook.android.feature.store

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactStoreRowLabelsTest {

    @Test
    fun formatUsesUppercaseFirstCharacter() {
        assertEquals("E", CompactStoreRowLabels.format("EPUB"))
        assertEquals("T", CompactStoreRowLabels.format("txt"))
        assertEquals("P", CompactStoreRowLabels.format("PDF"))
        assertEquals("M", CompactStoreRowLabels.format("Mobi"))
    }

    @Test
    fun blankFormatStaysBlank() {
        assertEquals("", CompactStoreRowLabels.format("  "))
    }

    @Test
    fun sourceUsesCompactKindMarker() {
        assertEquals("本", CompactStoreRowLabels.source(StoreItemKind.LOCAL))
        assertEquals("O", CompactStoreRowLabels.source(StoreItemKind.OPDS))
    }

    @Test
    fun localShelfLabelsKeepFullAccessibilityDescription() {
        assertEquals(
            CompactShelfLabel(text = "+", contentDescription = "加入书架"),
            CompactStoreRowLabels.localShelf(shelved = false)
        )
        assertEquals(
            CompactShelfLabel(text = "✓", contentDescription = "已在书架"),
            CompactStoreRowLabels.localShelf(shelved = true)
        )
    }
}
