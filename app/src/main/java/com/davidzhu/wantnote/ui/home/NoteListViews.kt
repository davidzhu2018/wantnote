package com.davidzhu.wantnote.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.HomeViewMode
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.ui.components.NoteMediaThumb
import com.davidzhu.wantnote.ui.components.TagChipRow

@Composable
fun NoteCollection(
    items: List<NoteWithDetails>,
    viewMode: HomeViewMode,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
) {
    if (items.isEmpty()) {
        if (emptyText != null) {
            Text(
                text = emptyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
        return
    }
    when (viewMode) {
        HomeViewMode.CARD -> CardView(items, onOpenNote, modifier)
        HomeViewMode.WATERFALL -> WaterfallView(items, onOpenNote, modifier)
        HomeViewMode.MINIMAL -> MinimalView(items, onOpenNote, modifier)
        HomeViewMode.TIMESTAMP -> TimestampView(items, onOpenNote, modifier)
    }
}

@Composable
private fun CardView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.note.id }) { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNote(item.note.id) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                Column {
                    val media = item.firstMedia
                    if (media != null) {
                        NoteMediaThumb(
                            media = media,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f),
                            corner = 0.dp,
                        )
                    }
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = SearchText.displayTitle(item.note),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val preview = SearchText.preview(item.note)
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 8.dp), max = 3)
                        Text(
                            text = Dates.formatTime(item.note.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterfallView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
    ) {
        items(items, key = { it.note.id }) { item ->
            val preview = SearchText.preview(item.note)
            val maxLines = when {
                preview.length > 120 -> 8
                preview.length > 60 -> 5
                preview.length > 20 -> 3
                else -> 2
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNote(item.note.id) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column {
                    val media = item.firstMedia
                    if (media != null) {
                        val ratio = if (media.blockType.name == "VIDEO") 16f / 9f else 1f
                        NoteMediaThumb(
                            media = media,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio),
                            corner = 0.dp,
                        )
                    }
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = SearchText.displayTitle(item.note),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = maxLines,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 8.dp), max = 3)
                        Text(
                            text = Dates.formatTime(item.note.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items, key = { it.note.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenNote(item.note.id) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val media = item.firstMedia
                if (media != null) {
                    NoteMediaThumb(
                        media = media,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(40.dp),
                        corner = 6.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = SearchText.displayTitle(item.note),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TagChipRow(tags = item.tags, max = 2)
                }
                Text(
                    text = Dates.formatTime(item.note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun TimestampView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, modifier: Modifier = Modifier) {
    val grouped = items.groupBy { Dates.dayKey(it.note.updatedAt) }
    val keys = grouped.keys.toList()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        keys.forEach { key ->
            val dayNotes = grouped.getValue(key)
            val dayLabel = Dates.formatDay(dayNotes.first().note.updatedAt)
            item(key = "header-$key") {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            items(dayNotes, key = { it.note.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenNote(item.note.id) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val media = item.firstMedia
                    if (media != null) {
                        NoteMediaThumb(
                            media = media,
                            modifier = Modifier
                                .padding(end = 12.dp, top = 4.dp)
                                .size(56.dp),
                            corner = 6.dp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Dates.formatTimestamp(item.note.updatedAt),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = SearchText.displayTitle(item.note),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val preview = SearchText.preview(item.note)
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 6.dp), max = 3)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
