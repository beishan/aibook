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
import androidx.compose.ui.draw.shadow
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
    val topPadding = if (title.isNotEmpty()) 20.dp else 0.dp
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = DesignTokens.PagePadding, vertical = topPadding)
    ) {
        if (title.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold
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
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.surface else color
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = DesignTokens.SoftShadow,
                shape = RoundedCornerShape(DesignTokens.CardRadius),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(DesignTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor)
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
        trailing?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                .clip(RoundedCornerShape(8.dp))
                .background(brush, RoundedCornerShape(8.dp))
        )
        return
    }

    Box(
        modifier = modifier
            .then(sizeModifier)
            .background(brush, RoundedCornerShape(8.dp))
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
    val color = if (source.equals("OPDS", ignoreCase = true)) DesignTokens.OpdsGreen else DesignTokens.Accent
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp)
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
