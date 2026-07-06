package com.aibook.android.feature.reader

import com.aibook.android.core.model.PageTurnMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageTurnVisualsTest {

    @Test
    fun slideKeepsDefaultPagerTransform() {
        val transform = PageTurnVisuals.transform(PageTurnMode.SLIDE, pageOffset = 0.5f)

        assertEquals(1f, transform.alpha)
        assertEquals(1f, transform.scale)
        assertEquals(0f, transform.translationXMultiplier)
        assertEquals(0f, transform.rotationY)
    }

    @Test
    fun simulationUsesBookPageHingeAndPaperLighting() {
        val transform = PageTurnVisuals.transform(PageTurnMode.SIMULATION, pageOffset = 0.5f)

        assertTrue(transform.rotationY <= -45f)
        assertEquals(0f, transform.pivotFractionX)
        assertEquals(1f, transform.scale)
        assertTrue(transform.shadowAlpha > 0f)
        assertTrue(transform.highlightAlpha > 0f)
        assertTrue(transform.zIndex > 0f)
    }

    @Test
    fun coverPinsCurrentPageButDoesNotPinIncomingPage() {
        val currentPage = PageTurnVisuals.transform(PageTurnMode.COVER, pageOffset = 0.25f)
        val incomingPage = PageTurnVisuals.transform(PageTurnMode.COVER, pageOffset = -0.75f)

        assertEquals(0.25f, currentPage.translationXMultiplier)
        assertEquals(0f, incomingPage.translationXMultiplier)
        assertTrue(currentPage.alpha < 1f)
        assertEquals(1f, currentPage.scale)
    }

    @Test
    fun panUsesParallaxWithoutDimmingOrScalingPages() {
        val transform = PageTurnVisuals.transform(PageTurnMode.PAN, pageOffset = 0.5f)

        assertTrue(transform.translationXMultiplier > 0f)
        assertTrue(transform.translationXMultiplier < 0.5f)
        assertEquals(1f, transform.alpha)
        assertEquals(1f, transform.scale)
    }
}
