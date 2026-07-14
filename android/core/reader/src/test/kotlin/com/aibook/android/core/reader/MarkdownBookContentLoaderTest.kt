package com.aibook.android.core.reader

import com.aibook.android.core.model.BookFormat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownBookContentLoaderTest {

    private val loader = MarkdownBookContentLoader()

    @Test
    fun h1AndH2CreateStableChapters() = runTest {
        val result = loader.load(request("# One\nText\n## Two\n- A\n- B"))

        val content = assertIs<BookContentResult.Success>(result).content
        assertEquals(listOf("One", "Two"), content.chapters.map { it.title })
        assertEquals(listOf("markdown:0", "markdown:1"), content.chapters.map { it.href })
        assertTrue(content.chapters[1].content.contains("• A"), content.chapters[1].content)
        assertTrue(content.chapters[1].content.contains("• B"), content.chapters[1].content)
    }

    @Test
    fun contentBeforeFirstHeadingBecomesPreface() = runTest {
        val result = loader.load(request("intro\n\n# Chapter\nbody"))

        val content = assertIs<BookContentResult.Success>(result).content
        assertEquals(listOf("前言", "Chapter"), content.chapters.map { it.title })
        assertEquals("intro", content.chapters.first().content)
    }

    @Test
    fun headingFreeDocumentCreatesBodyChapter() = runTest {
        val result = loader.load(request("plain **content**"))

        val chapter = assertIs<BookContentResult.Success>(result).content.chapters.single()
        assertEquals("正文", chapter.title)
        assertEquals("plain content", chapter.content)
    }

    @Test
    fun firstSafeLocalImageIsResolvedPerChapter() = runTest {
        val directory = Files.createTempDirectory("markdown-image-test").toFile()
        val source = File(directory, "book.md")
        val first = File(directory, "images/first.png").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val ignored = File(directory, "images/ignored.png").apply { writeBytes(byteArrayOf(2)) }
        val second = File(directory, "second.jpg").apply { writeBytes(byteArrayOf(3)) }
        source.writeText(
            "# One\n![first](images/first.png)\n![ignored](images/ignored.png)\ntext\n" +
                "# Two\n![second](second.jpg)\nbody"
        )

        val content = assertIs<BookContentResult.Success>(loader.load(request(source))).content

        assertEquals(first.canonicalPath, content.chapters[0].imageUri)
        assertEquals(second.canonicalPath, content.chapters[1].imageUri)
        assertTrue(content.chapters[0].content.contains("first"))
        assertTrue(content.chapters[0].content.contains("ignored"))
    }

    @Test
    fun unsafeAndMissingImagesAreRejectedWhileLaterSafeImageIsUsed() = runTest {
        val directory = Files.createTempDirectory("markdown-unsafe-image-test").toFile()
        val source = File(directory, "book.md")
        val outside = File(directory.parentFile, "outside-${directory.name}.png").apply { writeBytes(byteArrayOf(1)) }
        val safe = File(directory, "safe.png").apply { writeBytes(byteArrayOf(2)) }
        source.writeText(
            "# Images\n" +
                "![remote](https://example.com/a.png)\n" +
                "![absolute](${outside.absolutePath})\n" +
                "![traversal](../${outside.name})\n" +
                "![missing](missing.png)\n" +
                "![safe](safe.png)"
        )

        val chapter = assertIs<BookContentResult.Success>(loader.load(request(source))).content.chapters.single()

        assertEquals(safe.canonicalPath, chapter.imageUri)
        assertTrue(chapter.content.contains("remote"))
        assertTrue(chapter.content.contains("absolute"))
        assertTrue(chapter.content.contains("traversal"))
        assertTrue(chapter.content.contains("missing"))
        outside.delete()
    }

    @Test
    fun markdownReadAndParseRunOnInjectedIoDispatcher() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "markdown-test-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var readThreadName: String? = null
            val loader = MarkdownBookContentLoader(
                ioDispatcher = dispatcher,
                readMarkdown = { file ->
                    readThreadName = Thread.currentThread().name
                    file.readText()
                }
            )

            assertIs<BookContentResult.Success>(loader.load(request("# Off main\nbody")))

            assertEquals("markdown-test-io", readThreadName)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun resourceReferencesIncludeOnlySafeRelativeRasterImages() {
        val references = MarkdownResourceReferences.extract(
            """
            ![nested](images/a.png)
            ![encoded](images/cover%20one.jpg)
            ![remote](https://example.com/a.png)
            ![absolute](/sdcard/private.png)
            ![escape](../private.png)
            ![not-image](assets/data.json)
            """.trimIndent()
        )

        assertEquals(listOf("images/a.png", "images/cover one.jpg"), references)
    }

    @Test
    fun percentEncodedRelativeImageResolvesToCopiedPrivateFile() = runTest {
        val directory = Files.createTempDirectory("markdown-encoded-image-test").toFile()
        val source = File(directory, "book.md")
        val image = File(directory, "images/cover one.jpg").apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        source.writeText("# One\n![cover](images/cover%20one.jpg)")

        val chapter = assertIs<BookContentResult.Success>(loader.load(request(source))).content.chapters.single()

        assertEquals(image.canonicalPath, chapter.imageUri)
    }

    private fun request(markdown: String): BookContentRequest {
        val directory = Files.createTempDirectory("markdown-loader-test").toFile()
        val source = File(directory, "book.md").apply { writeText(markdown) }
        return request(source)
    }

    private fun request(source: File): BookContentRequest {
        val directory = source.parentFile
        return BookContentRequest(
            bookId = "markdown-test",
            file = source,
            format = BookFormat.MARKDOWN,
            cacheDirectory = File(directory, "cache")
        )
    }
}
