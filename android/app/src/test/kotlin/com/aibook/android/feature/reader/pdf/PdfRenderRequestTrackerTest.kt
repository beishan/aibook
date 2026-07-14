package com.aibook.android.feature.reader.pdf

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfRenderRequestTrackerTest {

    @Test
    fun newerWidthForSamePageIsNotSuppressedOrOverwrittenByOldResult() {
        val tracker = PdfRenderRequestTracker()
        val old = PdfPageBitmapCache.Key(pageIndex = 2, targetWidthPx = 1080)
        val current = PdfPageBitmapCache.Key(pageIndex = 2, targetWidthPx = 2160)

        assertTrue(tracker.begin(old))
        assertTrue(tracker.begin(current))
        assertFalse(tracker.begin(current))
        assertFalse(tracker.isLatest(old))
        assertTrue(tracker.isLatest(current))

        tracker.complete(old)
        assertTrue(tracker.isLatest(current))
    }
}
