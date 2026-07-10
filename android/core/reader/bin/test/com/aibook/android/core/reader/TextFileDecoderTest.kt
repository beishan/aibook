package com.aibook.android.core.reader

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

class TextFileDecoderTest {
    @Test
    fun `decodes utf8 and strips bom`() {
        val content = "第一章 中文内容"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + content.toByteArray()

        assertEquals(content, TextFileDecoder.decode(bytes))
    }

    @Test
    fun `falls back to gbk for invalid utf8 bytes`() {
        val content = "第一章 中文内容"
        val bytes = content.toByteArray(Charset.forName("GBK"))

        assertEquals(content, TextFileDecoder.decode(bytes))
    }
}
