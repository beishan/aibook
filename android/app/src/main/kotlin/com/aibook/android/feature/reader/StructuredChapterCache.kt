package com.aibook.android.feature.reader

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class CachedStructuredManifest(
    val versionId: Long,
    val chapters: List<CachedStructuredChapter>
)

internal data class CachedStructuredChapter(
    val chapterId: Long,
    val key: String,
    val title: String
)

internal class StructuredChapterCache(private val root: File) {
    fun readManifest(namespace: String, bookId: Long, versionId: Long?): CachedStructuredManifest? =
        runCatching {
            val file = manifestFile(namespace, bookId, versionId)
            if (!file.isFile) return null
            val lines = file.readLines(StandardCharsets.UTF_8)
            val header = lines.firstOrNull()?.split('\t') ?: return null
            val cachedVersionId = header.getOrNull(0)?.toLongOrNull() ?: return null
            val expectedChapterCount = header.getOrNull(1)?.toIntOrNull() ?: return null
            val chapters = lines.drop(1).mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.size != 3) return@mapNotNull null
                val chapterId = fields[0].toLongOrNull() ?: return@mapNotNull null
                CachedStructuredChapter(chapterId, decode(fields[1]), decode(fields[2]))
            }
            CachedStructuredManifest(cachedVersionId, chapters).takeIf {
                it.chapters.isNotEmpty() && it.chapters.size == expectedChapterCount
            }
        }.getOrNull()

    fun writeManifest(
        namespace: String,
        bookId: Long,
        manifest: CachedStructuredManifest,
        markActive: Boolean
    ) {
        val value = buildString {
            append(manifest.versionId)
            append('\t')
            appendLine(manifest.chapters.size)
            manifest.chapters.forEach { chapter ->
                append(chapter.chapterId)
                append('\t')
                append(encode(chapter.key))
                append('\t')
                appendLine(encode(chapter.title))
            }
        }
        writeAtomically(manifestFile(namespace, bookId, manifest.versionId), value)
        if (markActive) writeAtomically(manifestFile(namespace, bookId, null), value)
    }

    fun readChapter(
        namespace: String,
        bookId: Long,
        versionId: Long,
        chapterId: Long
    ): String? = runCatching {
        chapterFile(namespace, bookId, versionId, chapterId)
            .takeIf(File::isFile)
            ?.readText(StandardCharsets.UTF_8)
    }.getOrNull()

    fun writeChapter(
        namespace: String,
        bookId: Long,
        versionId: Long,
        chapterId: Long,
        content: String
    ) {
        writeAtomically(chapterFile(namespace, bookId, versionId, chapterId), content)
    }

    private fun manifestFile(namespace: String, bookId: Long, versionId: Long?): File =
        File(bookDirectory(namespace, bookId), "manifest-${versionId ?: "active"}.tsv")

    private fun chapterFile(namespace: String, bookId: Long, versionId: Long, chapterId: Long): File =
        File(bookDirectory(namespace, bookId), "version-$versionId/chapter-$chapterId.txt")

    private fun bookDirectory(namespace: String, bookId: Long): File =
        File(root, "${namespace.safePathSegment()}/book-$bookId")

    private fun writeAtomically(target: File, value: String) {
        runCatching {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.part")
            try {
                temporary.writeText(value, StandardCharsets.UTF_8)
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }
    }

    private fun String.safePathSegment(): String =
        replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "default" }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
