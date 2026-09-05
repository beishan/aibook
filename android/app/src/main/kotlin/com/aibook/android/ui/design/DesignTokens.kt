package com.aibook.android.ui.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DesignTokens {
    val AppBackground = Color(0xFFFFF9F2)
    val CardBackground = Color(0xFFFFFDF9)
    val WarmCard = Color(0xFFF7EFE6)
    val Accent = Color(0xFF9B5E24)
    val AccentDark = Color(0xFF784415)
    val AccentContainer = Color(0xFFEED9C1)
    val TextPrimary = Color(0xFF2B2118)
    val SoftText = Color(0xFF8A7A6A)
    val Hairline = Color(0xFFEEE3D7)
    val Success = Color(0xFF4E8A52)
    val Warning = Color(0xFFC7872D)
    val Danger = Color(0xFFC94A45)
    val OpdsGreen = Success

    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val PagePadding = Space16

    val RadiusSmall = 8.dp
    val RadiusMedium = 12.dp
    val CardRadius = 16.dp
    val RadiusLarge = 24.dp
    val SoftShadow = 1.dp
    val FloatingShadow = 3.dp
    val BottomNavigationHeight = 72.dp

    val WarmGradient = Brush.horizontalGradient(
        colors = listOf(CardBackground, AccentContainer.copy(alpha = 0.72f))
    )
}
