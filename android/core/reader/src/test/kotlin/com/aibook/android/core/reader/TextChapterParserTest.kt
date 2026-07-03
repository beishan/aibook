package com.aibook.android.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class TextChapterParserTest {
    @Test
    fun `splits chinese chapter headings into chapters`() {
        val text = """
            序
            这是序言。
            第一章 科学边界
            汪淼看见了倒计时。
            第二章 台球
            宇宙不是一张台球桌。
        """.trimIndent()

        val chapters = TextChapterParser.parse(text)

        assertEquals(3, chapters.size)
        assertEquals("序章", chapters[0].title)
        assertEquals("第一章 科学边界", chapters[1].title)
        assertEquals("第二章 台球", chapters[2].title)
        assertEquals("汪淼看见了倒计时。", chapters[1].content.trim())
    }

    @Test
    fun `returns single chapter when headings are absent`() {
        val chapters = TextChapterParser.parse("没有章节标题的短篇内容")

        assertEquals(1, chapters.size)
        assertEquals("正文", chapters.single().title)
        assertEquals("没有章节标题的短篇内容", chapters.single().content)
    }
}
