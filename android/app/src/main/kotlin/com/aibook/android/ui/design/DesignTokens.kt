package com.aibook.android.ui.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DesignTokens {
    val AppBackground = Color(0xFFFFFCF8)
    val CardBackground = Color(0xFFFFFEFC)
    val WarmCard = Color(0xFFFFF8F0)
    val Accent = Color(0xFFD47A1F)
    val AccentDark = Color(0xFFB96312)
    val SoftText = Color(0xFF6F6A64)
    val Hairline = Color(0xFFECE5DE)
    val Success = Color(0xFF3A8A4C)
    val OpdsGreen = Color(0xFF6F8F52)

    val PagePadding = 24.dp
    val CardRadius = 18.dp
    val SoftShadow = 8.dp

    val WarmGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFFF7EF), Color(0xFFF7E6D2))
    )
}
