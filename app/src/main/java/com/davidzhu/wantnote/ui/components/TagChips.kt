package com.davidzhu.wantnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagColorPicker(
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tag.PALETTE.forEach { color ->
            val chosen = color == selected
            Box(
                modifier = Modifier
                    .size(if (chosen) 26.dp else 22.dp)
                    .clip(CircleShape)
                    .background(tagColor(color))
                    .clickable { onSelect(color) },
            )
        }
    }
}
