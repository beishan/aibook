package com.aibook.android.feature.reader

import com.aibook.android.core.model.ReaderAutoScrollSpeed

object ReaderAutoPlayPolicy {
    fun pageDelayMillis(seconds: Int): Long = seconds.coerceIn(3, 30) * 1_000L

    fun scrollStepPx(speed: ReaderAutoScrollSpeed, density: Float): Float {
        val dpPerSecond = when (speed) {
            ReaderAutoScrollSpeed.SLOW -> 18f
            ReaderAutoScrollSpeed.MEDIUM -> 34f
            ReaderAutoScrollSpeed.FAST -> 54f
        }
        return dpPerSecond * density.coerceAtLeast(0f) / 60f
    }
}
