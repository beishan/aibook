package com.aibook.android.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageTurnModeTest {
    @Test
    fun nonVerticalModesUsePagedReading() {
        assertTrue(PageTurnMode.SIMULATION.usesPagedReading())
        assertTrue(PageTurnMode.SLIDE.usesPagedReading())
        assertTrue(PageTurnMode.COVER.usesPagedReading())
        assertTrue(PageTurnMode.PAN.usesPagedReading())
    }

    @Test
    fun verticalModeKeepsScrollReading() {
        assertFalse(PageTurnMode.VERTICAL.usesPagedReading())
    }
}
