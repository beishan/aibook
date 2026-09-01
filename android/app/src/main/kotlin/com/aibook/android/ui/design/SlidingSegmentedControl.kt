package com.aibook.android.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SlidingSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex.coerceIn(options.indices),
            label = "segmentedIndicator"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelected(index) }
                        .padding(vertical = 10.dp),
                    color = if (index == selectedIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
