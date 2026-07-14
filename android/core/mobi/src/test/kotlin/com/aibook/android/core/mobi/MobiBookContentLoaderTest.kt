package com.aibook.android.core.mobi

import com.aibook.android.core.model.BookFormat
import com.aibook.android.core.reader.BookContentError
import com.aibook.android.core.reader.BookContentRequest
import com.aibook.android.core.reader.BookContentResult
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MobiBookContentLoaderTest {

    @Test
    fun `normalizes mobi html into reader chapters and reuses cache`() = runTest {
        val root = createTempDirectory("mobi-loader").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            val raw = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<html><head><title>Fallback</title></head><body><h1>第一章</h1><p>离线正文</p></body></html>")
            }
            MobiParseResult.Success(
                MobiDocument(
                    title = "测试书",
                    author = "作者",
                    chapters = listOf(MobiChapter(null, "mobi:0", raw.path))
                )
            )
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(source, File(root, "cache"), BookFormat.MOBI)

        val first = assertIs<BookContentResult.Success>(loader.load(request)).content
        val second = assertIs<BookContentResult.Success>(loader.load(request)).content

        assertEquals("测试书", first.title)
        assertEquals("作者", first.author)
        assertEquals("第一章", first.chapters.single().title)
        assertEquals("离线正文", first.chapters.single().content)
        assertEquals(first, second)
        assertEquals(1, parseCount)
    }

    @Test
    fun `splits a single legacy mobi html file by major headings`() = runTest {
        val root = createTempDirectory("mobi-headings").toFile()
        val source = File(root, "legacy.mobi").apply { writeText("fixture") }
        val parser = MobiDocumentParser { _, outputDirectory ->
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<h1>第一章</h1><p>甲</p><h2>第二章</h2><p>乙</p>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }

        val content = assertIs<BookContentResult.Success>(
            MobiBookContentLoader(parser).load(request(source, File(root, "cache"), BookFormat.MOBI))
        ).content

        assertEquals(listOf("第一章", "第二章"), content.chapters.map { it.title })
        assertEquals(listOf("甲", "乙"), content.chapters.map { it.content })
    }

    @Test
    fun `invalidates cache when source changes`() = runTest {
        val root = createTempDirectory("mobi-cache").toFile()
        val source = File(root, "book.azw3").apply { writeText("one") }
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<p>第 $parseCount 次解析</p>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(source, File(root, "cache"), BookFormat.AZW3)

        loader.load(request)
        source.appendText("two")
        loader.load(request)

        assertEquals(2, parseCount)
    }

    @Test
    fun `stale supplied content hash cannot hide source replacement or truncation`() = runTest {
        val root = createTempDirectory("mobi-stale-source-hash").toFile()
        val source = File(root, "book.azw3").apply { writeText("original source") }
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<p>source=${source.readText()}; parse=$parseCount</p>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(
            source,
            File(root, "cache"),
            BookFormat.AZW3,
            contentHash = "a".repeat(64)
        )

        loader.load(request)
        source.writeText("replacement source")
        val replaced = assertIs<BookContentResult.Success>(loader.load(request)).content
        source.writeText("")
        val truncated = assertIs<BookContentResult.Success>(loader.load(request)).content

        assertEquals("source=replacement source; parse=2", replaced.chapters.single().content)
        assertEquals("source=; parse=3", truncated.chapters.single().content)
        assertEquals(3, parseCount)
    }

    @Test
    fun `stale supplied content hash cannot serve old cache after source becomes drm protected`() = runTest {
        val root = createTempDirectory("mobi-stale-drm-hash").toFile()
        val source = File(root, "book.mobi").apply { writeText("plain") }
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            if (source.readText() == "drm") {
                MobiParseResult.Failure(MobiParseError.DRM_PROTECTED)
            } else {
                val html = File(outputDirectory, "chapter00001.html").apply {
                    parentFile?.mkdirs()
                    writeText("<p>readable</p>")
                }
                MobiParseResult.Success(
                    MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path)))
                )
            }
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(
            source,
            File(root, "cache"),
            BookFormat.MOBI,
            contentHash = "b".repeat(64)
        )

        assertIs<BookContentResult.Success>(loader.load(request))
        source.writeText("drm")
        val failure = assertIs<BookContentResult.Failure>(loader.load(request))

        assertEquals(BookContentError.DrmProtected, failure.error)
        assertEquals(2, parseCount)
    }

    @Test
    fun `missing cached chapter invalidates cache and reparses only once`() = runTest {
        val root = createTempDirectory("mobi-broken-cache").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val cache = File(root, "cache")
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<p>第 $parseCount 次解析</p>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(source, cache, BookFormat.MOBI)

        loader.load(request)
        val cachedChapter = cache.walkTopDown().single { it.name == "chapter-00000.txt" }
        assertTrue(cachedChapter.delete())

        val repaired = assertIs<BookContentResult.Success>(loader.load(request)).content
        val reused = assertIs<BookContentResult.Success>(loader.load(request)).content

        assertEquals("第 2 次解析", repaired.chapters.single().content)
        assertEquals(repaired, reused)
        assertEquals(2, parseCount)
    }

    @Test
    fun `truncated cached chapter invalidates cache and reparses only once`() = runTest {
        val root = createTempDirectory("mobi-truncated-cache").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val cache = File(root, "cache")
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<p>第 $parseCount 次完整解析</p>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(source, cache, BookFormat.MOBI)

        loader.load(request)
        cache.walkTopDown().single { it.name == "chapter-00000.txt" }.writeText("")

        val repaired = assertIs<BookContentResult.Success>(loader.load(request)).content
        val reused = assertIs<BookContentResult.Success>(loader.load(request)).content

        assertEquals("第 2 次完整解析", repaired.chapters.single().content)
        assertEquals(repaired, reused)
        assertEquals(2, parseCount)
    }

    @Test
    fun `replaced cached image invalidates cache and reparses only once`() = runTest {
        assertResourceCacheIsRepaired { cached -> cached.writeBytes(byteArrayOf(9, 8, 7)) }
    }

    @Test
    fun `zero byte cached image invalidates cache and reparses only once`() = runTest {
        assertResourceCacheIsRepaired { cached -> cached.writeBytes(byteArrayOf()) }
    }

    @Test
    fun `truncated cached cover invalidates cache and reparses only once`() = runTest {
        assertResourceCacheIsRepaired(mutateCover = { cached -> cached.writeBytes(byteArrayOf(1)) })
    }

    @Test
    fun `nested list paragraphs are normalized without duplicate text`() = runTest {
        val root = createTempDirectory("mobi-nested-list").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val parser = MobiDocumentParser { _, outputDirectory ->
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText(
                    "<ul><li><p>父项</p><ul><li><p>子项</p></li></ul></li></ul>"
                )
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }

        val content = assertIs<BookContentResult.Success>(
            MobiBookContentLoader(parser).load(request(source, File(root, "cache"), BookFormat.MOBI))
        ).content

        assertEquals("父项\n子项", content.chapters.single().content)
    }

    @Test
    fun `wrapped nested list paragraphs keep parent without duplicate text`() = runTest {
        val root = createTempDirectory("mobi-wrapped-nested-list").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val parser = MobiDocumentParser { _, outputDirectory ->
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<li><div><p>父项</p></div><ul><li><p>子项</p></li></ul></li>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }

        val content = assertIs<BookContentResult.Success>(
            MobiBookContentLoader(parser).load(request(source, File(root, "cache"), BookFormat.MOBI))
        ).content

        assertEquals("父项\n子项", content.chapters.single().content)
    }

    @Test
    fun `list item prefix and wrapped paragraph are both kept exactly once`() = runTest {
        val root = createTempDirectory("mobi-list-prefix").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val parser = MobiDocumentParser { _, outputDirectory ->
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<li>前缀<div><p>正文</p></div></li>")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }

        val content = assertIs<BookContentResult.Success>(
            MobiBookContentLoader(parser).load(request(source, File(root, "cache"), BookFormat.MOBI))
        ).content

        assertEquals("前缀\n正文", content.chapters.single().content)
    }

    @Test
    fun `maps drm error to shared content error`() = runTest {
        val root = createTempDirectory("mobi-drm").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val loader = MobiBookContentLoader(
            MobiDocumentParser { _, _ -> MobiParseResult.Failure(MobiParseError.DRM_PROTECTED) }
        )

        val failure = assertIs<BookContentResult.Failure>(
            loader.load(request(source, File(root, "cache"), BookFormat.MOBI))
        )

        assertEquals(BookContentError.DrmProtected, failure.error)
    }

    @Test
    fun `does not expose image paths outside parser output`() = runTest {
        val root = createTempDirectory("mobi-path").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        File(root, "secret.png").writeBytes(byteArrayOf(1, 2, 3))
        val parser = MobiDocumentParser { _, outputDirectory ->
            val html = File(outputDirectory, "chapter00001.html").apply {
                parentFile?.mkdirs()
                writeText("<p>正文</p><img src=\"../../secret.png\">")
            }
            MobiParseResult.Success(MobiDocument(chapters = listOf(MobiChapter(null, "mobi:0", html.path))))
        }

        val content = assertIs<BookContentResult.Success>(
            MobiBookContentLoader(parser).load(request(source, File(root, "cache"), BookFormat.MOBI))
        ).content

        assertEquals(null, content.chapters.single().imageUri)
    }

    private suspend fun assertResourceCacheIsRepaired(
        mutateImage: ((File) -> Unit)? = null,
        mutateCover: ((File) -> Unit)? = null
    ) {
        val root = createTempDirectory("mobi-resource-cache").toFile()
        val source = File(root, "book.mobi").apply { writeText("fixture") }
        val cache = File(root, "cache")
        var parseCount = 0
        val parser = MobiDocumentParser { _, outputDirectory ->
            parseCount++
            val raw = File(outputDirectory).apply { mkdirs() }
            File(raw, "image.png").writeBytes(byteArrayOf(parseCount.toByte(), 2, 3, 4))
            val cover = File(raw, "cover.jpg").apply {
                writeBytes(byteArrayOf(parseCount.toByte(), 6, 7, 8))
            }
            val html = File(raw, "chapter00001.html").apply {
                writeText("<p>第 $parseCount 次解析</p><img src=\"image.png\">")
            }
            MobiParseResult.Success(
                MobiDocument(
                    coverPath = cover.path,
                    chapters = listOf(MobiChapter(null, "mobi:0", html.path))
                )
            )
        }
        val loader = MobiBookContentLoader(parser)
        val request = request(source, cache, BookFormat.MOBI)

        val first = assertIs<BookContentResult.Success>(loader.load(request)).content
        mutateImage?.invoke(File(requireNotNull(first.chapters.single().imageUri)))
        mutateCover?.invoke(File(requireNotNull(first.coverPath)))

        val repaired = assertIs<BookContentResult.Success>(loader.load(request)).content
        val reused = assertIs<BookContentResult.Success>(loader.load(request)).content

        assertEquals("第 2 次解析", repaired.chapters.single().content)
        assertEquals(repaired, reused)
        assertEquals(2, parseCount)
    }

    private fun request(
        file: File,
        cache: File,
        format: BookFormat,
        contentHash: String? = null
    ) = BookContentRequest(
        bookId = "book-1",
        file = file,
        format = format,
        cacheDirectory = cache,
        contentHash = contentHash
    )
}
