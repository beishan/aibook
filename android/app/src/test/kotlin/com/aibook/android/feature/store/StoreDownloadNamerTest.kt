package com.aibook.android.feature.store

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreDownloadNamerTest {

    @Test
    fun fileNameUsesHrefFileNameWhenAvailable() {
        val book = remoteBook(
            title = "银河帝国",
            acquisitionHref = "https://example.com/books/foundation.epub?token=1",
            acquisitionType = "application/epub+zip"
        )

        assertEquals("foundation.epub", StoreDownloadNamer.fileName(book))
    }

    @Test
    fun fileNameFallsBackToSanitizedTitleAndMimeExtension() {
        val book = remoteBook(
            title = "银河 / 帝国：基地",
            acquisitionHref = "/download?id=1",
            acquisitionType = "application/pdf"
        )

        assertEquals("银河_帝国_基地.pdf", StoreDownloadNamer.fileName(book))
    }

    private fun remoteBook(
        title: String,
        acquisitionHref: String,
        acquisitionType: String?
    ) = StoreBook(
        id = "remote",
        title = title,
        author = "作者",
        summary = null,
        sourceId = "source",
        sourceName = "源",
        kind = StoreItemKind.OPDS,
        format = "EPUB",
        categories = listOf("EPUB"),
        updatedRank = 0L,
        acquisitionHref = acquisitionHref,
        acquisitionType = acquisitionType
    )
}
