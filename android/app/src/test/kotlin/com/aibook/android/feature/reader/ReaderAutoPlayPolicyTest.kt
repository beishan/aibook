package com.aibook.android.feature.reader

import com.aibook.android.core.model.ReaderAutoScrollSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderAutoPlayPolicyTest {

    @Test
    fun `page delay is clamped between three and thirty seconds`() {
        assertEquals(3_000L, ReaderAutoPlayPolicy.pageDelayMillis(1))
        assertEquals(8_000L, ReaderAutoPlayPolicy.pageDelayMillis(8))
        assertEquals(30_000L, ReaderAutoPlayPolicy.pageDelayMillis(45))
    }

    @Test
    fun `scroll steps increase with speed`() {
        val slow = ReaderAutoPlayPolicy.scrollStepPx(ReaderAutoScrollSpeed.SLOW, density = 2f)
        val medium = ReaderAutoPlayPolicy.scrollStepPx(ReaderAutoScrollSpeed.MEDIUM, density = 2f)
        val fast = ReaderAutoPlayPolicy.scrollStepPx(ReaderAutoScrollSpeed.FAST, density = 2f)

        assertTrue(slow > 0f)
        assertTrue(medium > slow)
        assertTrue(fast > medium)
    }

    @Test
    fun `scroll step scales with display density`() {
        val mdpi = ReaderAutoPlayPolicy.scrollStepPx(ReaderAutoScrollSpeed.MEDIUM, density = 1f)
        val xhdpi = ReaderAutoPlayPolicy.scrollStepPx(ReaderAutoScrollSpeed.MEDIUM, density = 2f)

        assertEquals(mdpi * 2f, xhdpi)
    }
}
