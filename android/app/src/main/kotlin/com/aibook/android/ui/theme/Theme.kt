package com.aibook.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aibook.android.ui.design.DesignTokens

private val LightColors = lightColorScheme(
    primary = DesignTokens.Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF8A6F55),
    tertiary = Color(0xFF6F8F52),
    background = DesignTokens.AppBackground,
    surface = DesignTokens.CardBackground,
    surfaceContainerHighest = Color(0xFFF7F1EC),
    outline = DesignTokens.Hairline
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE09A4B),
    secondary = Color(0xFFD5C4B2),
    tertiary = Color(0xFFA9C388),
    background = Color(0xFF11140F),
    surface = Color(0xFF11140F)
)

@Composable
fun AiBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
