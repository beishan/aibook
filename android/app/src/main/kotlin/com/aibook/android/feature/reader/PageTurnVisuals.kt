package com.aibook.android.feature.reader

import com.aibook.android.core.model.PageTurnMode
import kotlin.math.absoluteValue

data class PageTurnTransform(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val translationXMultiplier: Float = 0f,
    val rotationY: Float = 0f,
    val pivotFractionX: Float = 0.5f,
    val shadowAlpha: Float = 0f,
    val highlightAlpha: Float = 0f,
    val zIndex: Float = 0f
)

object PageTurnVisuals {
    fun transform(mode: PageTurnMode, pageOffset: Float): PageTurnTransform {
        val offset = pageOffset.coerceIn(-1f, 1f)
        val distance = offset.absoluteValue
        return when (mode) {
            PageTurnMode.SLIDE,
            PageTurnMode.VERTICAL -> PageTurnTransform()
            PageTurnMode.SIMULATION -> PageTurnTransform(
                alpha = 1f,
                scale = 1f,
                translationXMultiplier = offset * 0.04f,
                rotationY = -offset * 96f,
                pivotFractionX = if (offset >= 0f) 0f else 1f,
                shadowAlpha = distance * 0.46f,
                highlightAlpha = distance * 0.26f,
                zIndex = 1f - distance * 0.4f
            )
            PageTurnMode.COVER -> PageTurnTransform(
                alpha = if (distance < 0.5f) 1f - distance * 0.12f else 1f,
                scale = 1f,
                translationXMultiplier = if (distance < 0.5f) offset else 0f
            )
            PageTurnMode.PAN -> PageTurnTransform(
                alpha = 1f,
                scale = 1f,
                translationXMultiplier = offset * 0.32f
            )
        }
    }
}
