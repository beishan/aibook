package com.aibook.android.feature.reader.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfPageOffsetStoreTest {

    @Test
    fun navigationTargetUsesTheTargetPagesOwnOffset() {
        val store = PdfPageOffsetStore()

        store.restore(pageIndex = 3, offsetY = 120)
        store.update(pageIndex = 4, offsetY = 70)

        assertEquals(PdfPageNavigationTarget(3, 120), store.navigationTarget(3, pageCount = 6))
        assertEquals(PdfPageNavigationTarget(4, 70), store.navigationTarget(4, pageCount = 6))
        assertEquals(PdfPageNavigationTarget(5, 0), store.navigationTarget(5, pageCount = 6))
    }

    @Test
    fun navigationTargetClampsInvalidRequestedPageBeforeLookingUpOffset() {
        val store = PdfPageOffsetStore().apply { update(pageIndex = 4, offsetY = 90) }

        assertEquals(PdfPageNavigationTarget(4, 90), store.navigationTarget(99, pageCount = 5))
    }
}
