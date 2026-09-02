package com.aibook.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.aibook.android.core.model.AccentColor
import com.aibook.android.core.model.AppThemeMode
import com.aibook.android.ui.design.DesignTokens

private fun lightColors(accent: AccentColor) = lightColorScheme(
    primary = if (accent == AccentColor.ORANGE) DesignTokens.Accent else Color(accent.colorValue),
    onPrimary = Color.White,
    primaryContainer = DesignTokens.AccentContainer,
    onPrimaryContainer = DesignTokens.TextPrimary,
    secondary = Color(0xFF8A6F55),
    tertiary = DesignTokens.Success,
    background = DesignTokens.AppBackground,
    onBackground = DesignTokens.TextPrimary,
    surface = DesignTokens.CardBackground,
    onSurface = DesignTokens.TextPrimary,
    surfaceVariant = DesignTokens.WarmCard,
    onSurfaceVariant = DesignTokens.SoftText,
    surfaceContainerHighest = DesignTokens.WarmCard,
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

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    headlineLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
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
        typography = AppTypography,
        content = content
    )
}
