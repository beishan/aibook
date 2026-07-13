package com.aibook.android.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.aibook.android.core.model.ReaderOrientationMode
import com.aibook.android.core.model.ReaderSettings

private data class ReaderWindowSnapshot(
    val brightness: Float,
    val requestedOrientation: Int,
    val keepScreenOn: Boolean
)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun ReaderWindowEffects(
    settings: ReaderSettings,
    autoPlaying: Boolean
) {
    val activity = LocalContext.current.findActivity() ?: return
    val window = activity.window
    val snapshot = remember(activity) {
        ReaderWindowSnapshot(
            brightness = window.attributes.screenBrightness,
            requestedOrientation = activity.requestedOrientation,
            keepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        )
    }

    SideEffect {
        window.attributes = window.attributes.apply {
            screenBrightness = if (settings.autoBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                settings.brightness.coerceIn(0.1f, 1f)
            }
        }
        activity.requestedOrientation = when (settings.orientationMode) {
            ReaderOrientationMode.SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ReaderOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ReaderOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        if (settings.screenAlwaysOn || autoPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else if (!snapshot.keepScreenOn) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(activity) {
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = snapshot.brightness }
            activity.requestedOrientation = snapshot.requestedOrientation
            if (snapshot.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
