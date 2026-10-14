package com.aibook.android.feature.reader

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.model.LocalBook
import com.aibook.android.core.reader.TextChapterParser
import com.aibook.android.core.reader.TextFileDecoder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteBookReaderSourceTest {
    @Test
    fun downloadedStructuredTextCanBeOpenedWithoutCloudMetadata() {
        withTemporaryDirectory { directory ->
            val file = File(directory, "book.txt").apply {
                writeText("第一章 出发\n下载后的正文。\n第二章 到达\n离线也能阅读。")
            }
            val book = downloadedBook("local-text", file, BookFormat.TXT)

            assertEquals(
                RemoteReaderSource.Downloaded(book.id),
                resolveRemoteReaderSource(42L, listOf(book))
            )
            val text = TextFileDecoder.decode(file.readBytes())
            assertTrue(TextChapterParser.parse(text).any { it.content.contains("离线也能阅读") })
        }
    }

    @Test
    fun downloadedPdfUsesLocalReaderEvenWhenNotShelvedOrVisibleInStore() {
        withTemporaryDirectory { directory ->
            val file = File(directory, "book.pdf").apply { writeText("%PDF-1.4") }
            val book = downloadedBook("local-pdf", file, BookFormat.PDF)
                .copy(shelved = false, visibleInStore = false)

            assertEquals(
                RemoteReaderSource.Downloaded(book.id),
                resolveRemoteReaderSource(42L, listOf(book))
            )
        }
    }

    @Test
    fun skipsMissingEmptyAndDirectoryCopiesAndFindsAnotherValidDownload() {
        withTemporaryDirectory { directory ->
            val empty = File(directory, "empty.epub").apply { createNewFile() }
            val valid = File(directory, "valid.epub").apply { writeText("downloaded content") }
            val books = listOf(
                downloadedBook("missing", File(directory, "missing.epub"), BookFormat.EPUB),
                downloadedBook("empty", empty, BookFormat.EPUB),
                downloadedBook("directory", directory, BookFormat.EPUB),
                downloadedBook("valid", valid, BookFormat.EPUB)
            )

            assertEquals(RemoteReaderSource.Downloaded("valid"), resolveRemoteReaderSource(42L, books))
            assertEquals(RemoteReaderSource.Cloud, resolveRemoteReaderSource(42L, books.dropLast(1)))
        }
    }

    @Test
    fun doesNotOpenAnUnrelatedDownloadOrAnUndownloadedCloudBook() {
        withTemporaryDirectory { directory ->
            val file = File(directory, "other.txt").apply { writeText("other book") }
            val other = downloadedBook("other", file, BookFormat.TXT).copy(remoteBookId = 43L)
            val cloud = downloadedBook("server:42", File(""), BookFormat.TXT)

            assertEquals(RemoteReaderSource.Cloud, resolveRemoteReaderSource(42L, listOf(other, cloud)))
            assertEquals(RemoteReaderSource.Cloud, resolveRemoteReaderSource(42L, emptyList()))
        }
    }

    private fun downloadedBook(id: String, file: File, format: BookFormat) = LocalBook(
        id = id,
        title = "下载书籍",
        format = format,
        uri = file.absolutePath,
        source = "SERVER",
        remoteBookId = 42L
    )

    private fun withTemporaryDirectory(test: (File) -> Unit) {
        val directory = java.nio.file.Files.createTempDirectory("offline-reader-test").toFile()
        try {
            test(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
