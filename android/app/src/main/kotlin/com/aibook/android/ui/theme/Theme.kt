package com.aibook.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF386A20),
    secondary = Color(0xFF56624A),
    tertiary = Color(0xFF386667),
    background = Color(0xFFFBFDF5),
    surface = Color(0xFFFBFDF5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD67D),
    secondary = Color(0xFFBECBAD),
    tertiary = Color(0xFFA0CFD0),
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
