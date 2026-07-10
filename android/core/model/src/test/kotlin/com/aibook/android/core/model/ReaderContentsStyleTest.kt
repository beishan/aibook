package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderContentsStyleTest {
    @Test
    fun `reader settings use classic contents by default`() {
        assertEquals(ReaderContentsStyle.CLASSIC, ReaderSettings().contentsStyle)
    }

    @Test
    fun `stored contents style falls back to classic for missing or unknown values`() {
        assertEquals(ReaderContentsStyle.CLASSIC, ReaderContentsStyle.fromStoredValue(null))
        assertEquals(ReaderContentsStyle.CLASSIC, ReaderContentsStyle.fromStoredValue("future-style"))
        assertEquals(ReaderContentsStyle.GROUPED, ReaderContentsStyle.fromStoredValue("GROUPED"))
    }
}
