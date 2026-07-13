package com.aibook.android.feature.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class TxtParagraphNormalizerTest {

    @Test
    fun compressesConsecutiveWhitespaceOnlyLinesToOneBlankLine() {
        assertEquals(
            listOf("第一段", "", "第二段"),
            TxtParagraphNormalizer.normalize(
                text = "第一段\n\n　\n\u00A0\n第二段",
                compressBlankLines = true
            )
        )
    }

    @Test
    fun keepsOriginalBlankLinesWhenCompressionIsDisabled() {
        assertEquals(
            listOf("第一段", "", "", "第二段"),
            TxtParagraphNormalizer.normalize(
                text = "第一段\n\n\n第二段",
                compressBlankLines = false
            )
        )
    }
}
