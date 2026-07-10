package com.aibook.android

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.aibook.android.core.model.AccentColor
import com.aibook.android.core.model.AppThemeMode
import com.aibook.android.di.ServiceLocator
import com.aibook.android.ui.theme.AiBookTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val locator = ServiceLocator.get(application)
        val appThemeModeFlow = locator.readerSettingsStore.appThemeMode
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)
        val accentColorFlow = locator.readerSettingsStore.accentColor
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AccentColor.ORANGE)

        setContent {
            val appThemeMode by appThemeModeFlow.collectAsState()
            val accentColor by accentColorFlow.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appThemeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            AiBookTheme(
                appThemeMode = appThemeMode,
                accentColor = accentColor
            ) {
                AiBookApp()
            }
        }
    }
}
