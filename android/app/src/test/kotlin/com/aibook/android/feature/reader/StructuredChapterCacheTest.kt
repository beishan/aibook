package com.aibook.android.feature.reader

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StructuredChapterCacheTest {
    @Test
    fun `round trips active manifest and unicode chapter content`() {
        val root = Files.createTempDirectory("structured-cache").toFile()
        try {
            val cache = StructuredChapterCache(root)
            val manifest = CachedStructuredManifest(
                versionId = 9,
                chapters = listOf(CachedStructuredChapter(42, "chapter-1", "第一章\t开始"))
            )

            cache.writeManifest("server-user", 7, manifest, markActive = true)
            cache.writeChapter("server-user", 7, 9, 42, "正文一\n\n正文二")

            assertEquals(manifest, cache.readManifest("server-user", 7, null))
            assertEquals("正文一\n\n正文二", cache.readChapter("server-user", 7, 9, 42))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `isolates cache by server account and version`() {
        val root = Files.createTempDirectory("structured-cache").toFile()
        try {
            val cache = StructuredChapterCache(root)
            cache.writeChapter("server-a-user", 7, 9, 42, "版本九")

            assertNull(cache.readChapter("server-b-user", 7, 9, 42))
            assertNull(cache.readChapter("server-a-user", 7, 10, 42))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `rejects incomplete cached manifest`() {
        val root = Files.createTempDirectory("structured-cache").toFile()
        try {
            val cache = StructuredChapterCache(root)
            val manifest = CachedStructuredManifest(
                versionId = 9,
                chapters = listOf(
                    CachedStructuredChapter(42, "chapter-1", "第一章"),
                    CachedStructuredChapter(43, "chapter-2", "第二章")
                )
            )
            cache.writeManifest("server-user", 7, manifest, markActive = true)
            root.walkTopDown().first { it.name == "manifest-active.tsv" }
                .writeText("9\t2\n42\tY2hhcHRlci0x\t56ys5LiA56ug\n")

            assertNull(cache.readManifest("server-user", 7, null))
        } finally {
            root.deleteRecursively()
        }
    }
}
