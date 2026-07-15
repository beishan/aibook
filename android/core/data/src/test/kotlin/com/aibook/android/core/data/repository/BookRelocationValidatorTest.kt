package com.aibook.android.core.data.repository

import com.aibook.android.core.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookRelocationValidatorTest {
    @Test
    fun acceptsSameFormatAndContentHash() {
        assertNull(BookRelocationValidator.validateFormat("EPUB", BookFormat.EPUB))
        assertNull(BookRelocationValidator.validateHash("abc", "abc"))
    }

    @Test
    fun rejectsDifferentFormatOrContent() {
        assertEquals("请选择 EPUB 格式的文件", BookRelocationValidator.validateFormat("EPUB", BookFormat.PDF))
        assertEquals("所选文件与原书内容不一致", BookRelocationValidator.validateHash("abc", "def"))
    }

    @Test
    fun permitsRepairWhenLegacyRecordHasNoHash() {
        assertNull(BookRelocationValidator.validateHash(null, "new-hash"))
    }
}
