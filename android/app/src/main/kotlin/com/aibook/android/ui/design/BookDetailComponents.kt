package com.aibook.android.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class DetailInfoItem(
    val label: String,
    val value: String,
    val onClick: (() -> Unit)? = null
)

@Composable
fun BookDetailTopBar(
    title: String? = null,
    favorite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = DesignTokens.TextPrimary)
        }
        if (title != null) {
            Text(
                title,
                modifier = Modifier.weight(1f).padding(start = DesignTokens.Space8),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onShare) { Icon(Icons.Default.IosShare, contentDescription = "分享") }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (favorite) "取消收藏" else "收藏",
                tint = if (favorite) DesignTokens.Accent else DesignTokens.TextPrimary
            )
        }
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
    }
}

@Composable
fun DetailPrimaryButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                if (enabled) DesignTokens.Accent else DesignTokens.Accent.copy(alpha = 0.42f),
                RoundedCornerShape(DesignTokens.RadiusMedium)
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 15.dp),
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun RowScope.DetailActionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.weight(1f).height(64.dp),
        shape = RoundedCornerShape(DesignTokens.RadiusMedium),
        color = DesignTokens.CardBackground,
        border = BorderStroke(1.dp, DesignTokens.Hairline)
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = DesignTokens.Space8),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) DesignTokens.Accent else DesignTokens.TextPrimary)
            Text(
                label,
                modifier = Modifier.padding(start = DesignTokens.Space8),
                color = DesignTokens.TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DetailIntroduction(
    text: String,
    card: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space12)) {
            Text("简介", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text, color = DesignTokens.TextPrimary, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (card) {
        SoftCard(color = DesignTokens.CardBackground) { content() }
    } else {
        content()
    }
}

@Composable
fun DetailInfoCard(
    items: List<DetailInfoItem>,
    title: String = "书籍信息"
) {
    SoftCard(color = DesignTokens.CardBackground) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(top = DesignTokens.Space8)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = item.onClick != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { item.onClick?.invoke() }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.label, color = DesignTokens.SoftText)
                    Text(
                        item.value,
                        modifier = Modifier.weight(1f).padding(start = DesignTokens.Space16),
                        textAlign = TextAlign.End,
                        color = DesignTokens.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index != items.lastIndex) HorizontalDivider(color = DesignTokens.Hairline)
            }
        }
    }
}

@Composable
fun DetailTag(text: String) {
    Text(
        text,
        modifier = Modifier
            .border(1.dp, DesignTokens.Hairline, RoundedCornerShape(DesignTokens.RadiusMedium))
            .background(DesignTokens.WarmCard.copy(alpha = 0.42f), RoundedCornerShape(DesignTokens.RadiusMedium))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = DesignTokens.TextPrimary,
        style = MaterialTheme.typography.bodyMedium
    )
}
