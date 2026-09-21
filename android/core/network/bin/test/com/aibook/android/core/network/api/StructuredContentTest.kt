package com.aibook.android.core.network.api

import com.aibook.android.core.network.api.dto.ProcessedContentResponse
import com.aibook.android.core.network.api.dto.StructuredManifestLinkDTO
import com.aibook.android.core.network.api.dto.structuredChapters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuredContentTest {
    @Test
    fun `parses valid structured chapter ranges`() {
        val text = "第一章\n\n正文一\n\n第二章\n\n正文二"
        val secondStart = text.indexOf("第二章")
        val response = ProcessedContentResponse(
            text = text,
            chapterInfo = """[
                {"key":"c1","title":"第一章","startIndex":0,"endIndex":$secondStart},
                {"key":"c2","title":"第二章","startIndex":$secondStart,"endIndex":${text.length}}
            ]"""
        )

        val chapters = response.structuredChapters()

        assertEquals(listOf("c1", "c2"), chapters.map { it.key })
        assertEquals("第二章", chapters.last().title)
    }

    @Test
    fun `drops invalid chapter ranges without failing reader`() {
        val response = ProcessedContentResponse(
            text = "正文",
            chapterInfo = """[{"key":"bad","title":"坏章节","startIndex":9,"endIndex":12}]"""
        )

        assertTrue(response.structuredChapters().isEmpty())
    }

    @Test
    fun `extracts chapter id from manifest href with version query`() {
        val link = StructuredManifestLinkDTO(
            href = "/api/books/7/structured/chapters/42?versionId=9"
        )

        assertEquals(42L, link.chapterId())
        assertEquals(9L, link.versionId())
    }

    @Test
    fun `rejects malformed manifest chapter href`() {
        val link = StructuredManifestLinkDTO(href = "/api/books/7/structured/chapters/not-a-number")

        assertEquals(null, link.chapterId())
    }
}
