package com.aibook.android.feature.reader

import com.aibook.android.core.reader.BookContentError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BookContentErrorTextTest {

    @Test
    fun drmMessageGivesActionableAdvice() {
        assertEquals(
            "此书包含 DRM，当前仅支持无 DRM 的 MOBI/AZW3 文件",
            BookContentErrorText.forError(BookContentError.DrmProtected)
        )
    }

    @Test
    fun parseFailureDoesNotExposePaths() {
        val text = BookContentErrorText.forError(BookContentError.ParseFailed("/data/user/0/secret"))
        assertFalse(text.contains("/data/"))
    }
}
