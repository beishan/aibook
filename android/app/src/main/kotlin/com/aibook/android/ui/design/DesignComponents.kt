package com.aibook.android.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun DesignPage(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val topPadding = if (title.isNotEmpty()) DesignTokens.Space16 else 0.dp
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = DesignTokens.PagePadding, vertical = topPadding)
    ) {
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Space8, bottom = DesignTokens.Space16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), content = actions)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), content = actions)
            }
        }
        content()
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentPadding: Dp = DesignTokens.Space16,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.surface else color
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignTokens.SoftShadow)
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        trailing?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun BookCover(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp? = 88.dp,
    height: Dp = 128.dp,
    imageUri: String? = null,
    brush: Brush = Brush.verticalGradient(listOf(Color(0xFF28323A), Color(0xFF0F1418))),
    placeholderTitleMaxLength: Int = 6,
    placeholderMaxLines: Int = 3,
    placeholderTextStyle: TextStyle? = null
) {
    val sizeModifier = if (width != null) Modifier.size(width, height) else Modifier.fillMaxWidth().height(height)

    if (!imageUri.isNullOrBlank()) {
        AsyncImage(
            model = imageUri,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .then(sizeModifier)
                .clip(RoundedCornerShape(DesignTokens.RadiusSmall))
                .background(brush, RoundedCornerShape(DesignTokens.RadiusSmall))
        )
        return
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .background(brush, RoundedCornerShape(DesignTokens.RadiusSmall))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(placeholderTitleMaxLength),
            color = Color.White,
            style = placeholderTextStyle ?: MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = placeholderMaxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SourceBadge(text: String, source: String = text) {
    val color = when {
        source.equals("OPDS", ignoreCase = true) -> DesignTokens.OpdsGreen
        source.equals("后端", ignoreCase = true) || source.equals("远程", ignoreCase = true) -> DesignTokens.Warning
        else -> DesignTokens.Accent
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(DesignTokens.RadiusSmall)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CoverSourceBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun WarmProgress(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.height(5.dp),
        color = DesignTokens.Accent,
        trackColor = DesignTokens.Hairline,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
fun SpacerSmall() {
    Spacer(Modifier.height(8.dp))
}
