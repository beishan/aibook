package com.aibook.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aibook.android.core.model.AccentColor
import com.aibook.android.core.model.AppThemeMode
import com.aibook.android.ui.design.DesignTokens

private fun lightColors(accent: AccentColor) = lightColorScheme(
    primary = Color(accent.colorValue),
    onPrimary = Color.White,
    secondary = Color(0xFF8A6F55),
    tertiary = Color(0xFF6F8F52),
    background = DesignTokens.AppBackground,
    surface = DesignTokens.CardBackground,
    surfaceContainerHighest = Color(0xFFF7F1EC),
    outline = DesignTokens.Hairline
)

private fun darkColors(accent: AccentColor) = darkColorScheme(
    primary = Color(accent.colorValue),
    onPrimary = Color.White,
    secondary = Color(0xFFD5C4B2),
    tertiary = Color(0xFFA9C388),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFF3EFE9),
    surface = Color(0xFF171A15),
    onSurface = Color(0xFFF3EFE9),
    surfaceVariant = Color(0xFF23271F),
    onSurfaceVariant = Color(0xFFC8C1B8),
    surfaceContainerHighest = Color(0xFF252920),
    outline = Color(0xFF5F6558)
)

@Composable
fun AiBookTheme(
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.ORANGE,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors(accentColor) else lightColors(accentColor),
        content = content
    )
}
