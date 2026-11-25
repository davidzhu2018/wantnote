package com.davidzhu.wantnote.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantSurface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.HomeViewMode
import com.davidzhu.wantnote.data.NoteLink
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.ui.components.MarkdownText
import com.davidzhu.wantnote.ui.components.NoteMediaThumb
import com.davidzhu.wantnote.ui.components.TagChipRow

/**
 * Raised 3D card chrome shared by every home view (grid cards, waterfall,
 * minimal list, timestamp list). Draws a real soft drop shadow below the
 * card plus a hairline top edge, so each note card reads as lifted off the
 * paper background instead of flat against it.
 */
private val NoteCardShape = RoundedCornerShape(16.dp)

@Composable
private fun Modifier.noteCardShadow(elevation: Int): Modifier {
    return shadow(
        elevation = elevation.dp,
        shape = NoteCardShape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.28f),
        spotColor = Color.Black.copy(alpha = 0.52f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Int = 8,
    content: @Composable ColumnScope.() -> Unit,
) {
    WantSurface(
        modifier = modifier
            .fillMaxWidth()
            .noteCardShadow(elevation)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = NoteCardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(content = content)
    }
}

@Composable
fun NoteCollection(
    items: List<NoteWithDetails>,
    viewMode: HomeViewMode,
    onOpenNote: (String) -> Unit,
    onLongPressNote: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    links: List<NoteLink> = emptyList(),
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
        HomeViewMode.CARD -> CardView(items, onOpenNote, onLongPressNote, links, modifier)
        HomeViewMode.WATERFALL -> WaterfallView(items, onOpenNote, onLongPressNote, links, modifier)
        HomeViewMode.MINIMAL -> MinimalView(items, onOpenNote, onLongPressNote, links, modifier)
        HomeViewMode.TIMESTAMP -> TimestampView(items, onOpenNote, onLongPressNote, links, modifier)
    }
}

/**
 * Card footer showing this note's relations from the SAME ID-based
 * `note_links` data the 图谱 tab uses. `→` marks outgoing links, `←`
 * incoming ones; tapping navigates straight to the other note. Links whose
 * other endpoint no longer resolves (deleted before the FK cascade ran) are
 * skipped instead of crashing or navigating nowhere.
 */
@Composable
private fun NoteLinkFooter(
    noteId: String,
    links: List<NoteLink>,
    linkTitles: Map<String, String>,
    onOpenNote: (String) -> Unit,
) {
    val outgoing = links.filter { it.sourceNoteId == noteId }
    val incoming = links.filter { it.targetNoteId == noteId }
    if (outgoing.isEmpty() && incoming.isEmpty()) return
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        outgoing.forEach { link ->
            val title = linkTitles[link.targetNoteId] ?: return@forEach
            FooterLink(arrow = "→", title = title) { onOpenNote(link.targetNoteId) }
        }
        incoming.forEach { link ->
            val title = linkTitles[link.sourceNoteId] ?: return@forEach
            FooterLink(arrow = "←", title = title) { onOpenNote(link.sourceNoteId) }
        }
    }
}

@Composable
private fun FooterLink(arrow: String, title: String, onClick: () -> Unit) {
    Text(
        text = "$arrow $title",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    )
}


@Composable
private fun PreviewText(
    text: String,
    linkTitles: Map<String, String>,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    MarkdownText(
        text = text,
        linkTitles = linkTitles,
        maxLines = maxLines,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        renderInlineImages = false,
    )
}

@Composable
private fun CardView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, onLongPressNote: (String) -> Unit, links: List<NoteLink>, modifier: Modifier = Modifier) {
    val linkTitles = items.associate { it.note.id to SearchText.displayTitle(it.note) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.note.id }) { item ->
            NoteCard(onClick = { onOpenNote(item.note.id) }, onLongClick = { onLongPressNote(item.note.id) }, elevation = 8) {
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
                    val preview = SearchText.previewMarkdown(item.note)
                    PreviewText(
                        text = preview,
                        linkTitles = linkTitles,
                        maxLines = 5,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 8.dp), max = 3)
                    Text(
                        text = Dates.formatTime(item.note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    NoteLinkFooter(
                        noteId = item.note.id,
                        links = links,
                        linkTitles = linkTitles,
                        onOpenNote = onOpenNote,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaterfallView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, onLongPressNote: (String) -> Unit, links: List<NoteLink>, modifier: Modifier = Modifier) {
    val linkTitles = items.associate { it.note.id to SearchText.displayTitle(it.note) }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        items(items, key = { it.note.id }) { item ->
            val preview = SearchText.previewMarkdown(item.note)
            val maxLines = when {
                preview.length > 120 -> 8
                preview.length > 60 -> 5
                preview.length > 20 -> 3
                else -> 2
            }
            NoteCard(onClick = { onOpenNote(item.note.id) }, onLongClick = { onLongPressNote(item.note.id) }, elevation = 8) {
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
                    PreviewText(
                        text = preview,
                        linkTitles = linkTitles,
                        maxLines = maxLines,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 8.dp), max = 3)
                    Text(
                        text = Dates.formatTime(item.note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    NoteLinkFooter(
                        noteId = item.note.id,
                        links = links,
                        linkTitles = linkTitles,
                        onOpenNote = onOpenNote,
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, onLongPressNote: (String) -> Unit, links: List<NoteLink>, modifier: Modifier = Modifier) {
    val linkTitles = items.associate { it.note.id to SearchText.displayTitle(it.note) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.note.id }) { item ->
            NoteCard(onClick = { onOpenNote(item.note.id) }, onLongClick = { onLongPressNote(item.note.id) }, elevation = 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        NoteLinkFooter(
                            noteId = item.note.id,
                            links = links,
                            linkTitles = linkTitles,
                            onOpenNote = onOpenNote,
                        )
                    }
                    Text(
                        text = Dates.formatTime(item.note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampView(items: List<NoteWithDetails>, onOpenNote: (String) -> Unit, onLongPressNote: (String) -> Unit, links: List<NoteLink>, modifier: Modifier = Modifier) {
    val linkTitles = items.associate { it.note.id to SearchText.displayTitle(it.note) }
    val grouped = items.groupBy { Dates.dayKey(it.note.updatedAt) }
    val keys = grouped.keys.toList()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        keys.forEach { key ->
            val dayNotes = grouped.getValue(key)
            val dayLabel = Dates.formatDay(dayNotes.first().note.updatedAt)
            item(key = "header-$key") {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            }
            items(dayNotes, key = { it.note.id }) { item ->
                NoteCard(onClick = { onOpenNote(item.note.id) }, onLongClick = { onLongPressNote(item.note.id) }, elevation = 5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                            val preview = SearchText.previewMarkdown(item.note)
                            PreviewText(
                                text = preview,
                                linkTitles = linkTitles,
                                maxLines = 2,
                            )
                            TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 6.dp), max = 3)
                            NoteLinkFooter(
                                noteId = item.note.id,
                                links = links,
                                linkTitles = linkTitles,
                                onOpenNote = onOpenNote,
                            )
                        }
                    }
                }
            }
        }
    }
}
