package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportPolicyTest {
    @Test
    fun `recognizes supported reader formats case insensitively`() {
        val supported = listOf(
            "三体.EPUB",
            "notes.txt",
            "paper.Pdf",
            "draft.md",
            "archive.HTML",
            "page.htm"
        )

        supported.forEach { fileName ->
            assertTrue(ImportPolicy.isSupported(fileName), "$fileName should be importable")
        }
    }

    @Test
    fun `rejects unsupported files`() {
        val unsupported = listOf("cover.jpg", "music.mp3", "bundle.zip", "book")

        unsupported.forEach { fileName ->
            assertFalse(ImportPolicy.isSupported(fileName), "$fileName should not be importable")
        }
    }

    @Test
    fun `normalizes file names into book titles`() {
        assertEquals("三体", ImportPolicy.normalizedTitle("三体.epub"))
        assertEquals("My Reading Notes", ImportPolicy.normalizedTitle("My Reading Notes.TXT"))
        assertEquals("book", ImportPolicy.normalizedTitle("book"))
    }
}
