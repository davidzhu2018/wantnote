package com.davidzhu.wantnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.data.Tag

fun tagColor(color: Long): Color = Color(color.toInt())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChipRow(
    tags: List<Tag>,
    modifier: Modifier = Modifier,
    max: Int = 4,
    onClick: ((Tag) -> Unit)? = null,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.take(max).forEach { tag ->
            TagChip(tag = tag, onClick = onClick?.let { handler -> { handler(tag) } })
        }
        val extra = tags.size - max
        if (extra > 0) {
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val bg = tagColor(tag.color).copy(alpha = if (selected) 0.22f else 0.10f)
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tagColor(tag.color)),
            )
            Text(
                text = tag.path,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (onRemove != null) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable(onClick = onRemove),
                )
            }
        }
    }
}

@Composable
fun TagColorPicker(
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compact single-row color strip: one scrollable line of dots, the chosen
    // one enlarged with a ring and a check. Replaces the old wrapping palette.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tag.PALETTE.forEach { color ->
            val chosen = color == selected
            Box(
                modifier = Modifier
                    .size(if (chosen) 28.dp else 22.dp)
                    .clip(CircleShape)
                    .background(tagColor(color))
                    .then(
                        if (chosen) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                shape = CircleShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已选颜色",
                        tint = checkTint(tagColor(color)),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private fun checkTint(color: Color): Color {
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.6f) Color(0xFF333333) else Color.White
}
