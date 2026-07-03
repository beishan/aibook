package com.aibook.android.core.network.opds

import kotlin.test.Test
import kotlin.test.assertEquals

class OpdsDownloadNamerTest {
    @Test
    fun `uses href extension when present`() {
        val entry = OpdsEntry(
            title = "三体",
            acquisitionLink = OpdsLink(
                href = "/opds/books/1/download/three-body.epub",
                type = "application/epub+zip"
            )
        )

        assertEquals("three-body.epub", OpdsDownloadNamer.fileName(entry))
    }

    @Test
    fun `falls back to title and mime type extension`() {
        val entry = OpdsEntry(
            title = "三体：黑暗森林",
            acquisitionLink = OpdsLink(
                href = "/opds/books/2/download",
                type = "application/epub+zip"
            )
        )

        assertEquals("三体_黑暗森林.epub", OpdsDownloadNamer.fileName(entry))
    }
}
