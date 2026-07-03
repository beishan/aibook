package com.aibook.android.core.reader

object TextChapterParser {
    private val chapterHeading = Regex(
        pattern = "^\\s*(第[一二三四五六七八九十百千万零〇两0-9]+[章节回卷部篇].*)\\s*$"
    )

    fun parse(text: String): List<ReaderChapter> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) {
            return listOf(ReaderChapter(0, "正文", "chapter-0", ""))
        }

        val chapters = mutableListOf<ReaderChapter>()
        val current = StringBuilder()
        var currentTitle = "正文"
        var hasHeading = false
        var index = 0

        normalized.lineSequence().forEach { line ->
            val heading = chapterHeading.matchEntire(line)?.groupValues?.get(1)
            if (heading != null) {
                if (current.isNotBlank()) {
                    chapters += ReaderChapter(
                        index = index,
                        title = if (hasHeading) currentTitle else "序章",
                        href = "chapter-$index",
                        content = current.toString().trim()
                    )
                    index += 1
                    current.clear()
                }
                currentTitle = heading
                hasHeading = true
            } else {
                current.appendLine(line)
            }
        }

        if (current.isNotBlank() || chapters.isEmpty()) {
            chapters += ReaderChapter(
                index = index,
                title = currentTitle,
                href = "chapter-$index",
                content = current.toString().trim()
            )
        }

        return chapters
    }
}
