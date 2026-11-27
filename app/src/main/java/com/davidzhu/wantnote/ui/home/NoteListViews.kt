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
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.transfer.MarkdownCodec
import com.davidzhu.wantnote.ui.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
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
import com.davidzhu.wantnote.data.LiquidGlassSurfaceRole
import com.davidzhu.wantnote.ui.components.liquidGlassSurface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
private val NoteCardShape = RoundedCornerShape(20.dp)

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
    elevation: Int = 11,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 让卡片背景比页面底色更明亮/通透，配合 20dp 大圆角与立体阴影明显浮起。
    val liftedSurface = if (isDark) {
        lerp(MaterialTheme.colorScheme.surfaceVariant, Color.White, 0.14f)
    } else {
        lerp(MaterialTheme.colorScheme.surfaceVariant, Color.White, 0.80f)
    }
    val cardElevation = maxOf(elevation, 13)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = NoteCardShape,
                role = LiquidGlassSurfaceRole.CARD,
                baseColor = liftedSurface,
                elevationOverride = cardElevation.dp,
                borderWidth = 1.3.dp,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        content = content,
    )
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
    profileNickname: String = "",
    profileAvatarPath: String = "",
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
        HomeViewMode.MOMENTS -> MomentsView(
            items = items,
            onOpenNote = onOpenNote,
            onLongPressNote = onLongPressNote,
            links = links,
            profileNickname = profileNickname,
            profileAvatarPath = profileAvatarPath,
            modifier = modifier,
        )
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


/**
 * 0.17.2：仿微信朋友圈视图。
 * 展示用户头像、昵称、正文前 200 字、最多 9 张图片九宫格以及发布时间。
 */
@Composable
private fun MomentsView(
    items: List<NoteWithDetails>,
    onOpenNote: (String) -> Unit,
    onLongPressNote: (String) -> Unit,
    links: List<NoteLink>,
    profileNickname: String,
    profileAvatarPath: String,
    modifier: Modifier = Modifier,
) {
    val media = LocalMedia.current
    val linkTitles = remember(items) { items.associate { it.note.id to SearchText.displayTitle(it.note) } }
    val displayName = profileNickname.trim().ifBlank { "想记用户" }
    val avatarBitmap by produceState<Bitmap?>(initialValue = null, profileAvatarPath) {
        value = if (profileAvatarPath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { media.loadPreview(profileAvatarPath, maxSide = 256) }
        }
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val nicknameColor = if (isDark) Color(0xFF7DA7D9) else Color(0xFF576B95)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.note.id }) { item ->
            val images = remember(item.note.id, item.note.body, item.blocks) {
                extractMomentImages(item)
            }
            val bodyExcerpt = remember(item.note.id, item.note.title, item.note.body) {
                extractMomentsExcerpt(item.note.body, maxChars = 200)
            }
            NoteCard(
                onClick = { onOpenNote(item.note.id) },
                onLongClick = { onLongPressNote(item.note.id) },
                elevation = 7,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // 左侧用户头像（圆角方形，仿微信朋友圈）
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = avatarBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = displayName,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = nicknameColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.note.title.isNotBlank()) {
                            Text(
                                text = item.note.title.trim(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (bodyExcerpt.isNotBlank()) {
                            MarkdownText(
                                text = bodyExcerpt,
                                linkTitles = linkTitles,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                renderInlineImages = false,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (images.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MomentsNineGrid(images = images)
                        }
                        TagChipRow(tags = item.tags, modifier = Modifier.padding(top = 8.dp), max = 3)
                        Text(
                            text = Dates.formatTimestamp(
                                if (item.note.createdAt > 0L) item.note.createdAt else item.note.updatedAt,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
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
}

/** 从块列表与正文 Markdown / HTML `<img>` 中提取最多 9 张图片路径或 URL。 */
private fun extractMomentImages(item: NoteWithDetails): List<String> {
    val result = LinkedHashSet<String>()
    item.blocks
        .filter { it.blockType == BlockType.IMAGE && it.mediaPath.isNotBlank() }
        .forEach { result += it.mediaPath.trim() }
    val mdImgRegex = Regex("""!\[[^\]]*]\(([^)\s]+)[^)]*\)""")
    mdImgRegex.findAll(item.note.body).forEach { m ->
        val url = m.groupValues[1].trim()
        if (url.isNotEmpty()) result += url
    }
    val htmlImgRegex = Regex("""(?i)<img\b([^>]*)>""")
    htmlImgRegex.findAll(item.note.body).forEach { m ->
        val url = MarkdownCodec.extractHtmlAttr(m.groupValues[1], "src").orEmpty().trim()
        if (url.isNotEmpty()) result += url
    }
    return result.take(9)
}

/** 提取正文前 200 字（剥离图片源码与块级前缀，保留可读文字）。 */
private fun extractMomentsExcerpt(body: String, maxChars: Int = 200): String {
    val cleaned = body.trim().lineSequence()
        .map { line ->
            var text = line.trim()
            text = text.removePrefix("[x] ").removePrefix("[ ] ")
            text = text.replace(Regex("""^#{1,6}\s+"""), "")
            text = text.replace(Regex("""^>\s?"""), "")
            text = text.replace(Regex("""^```.*$"""), "")
            text = text.replace(Regex("""^~~~.*$"""), "")
            text = text.replace(Regex("""^[-*+]\s+"""), "")
            text = text.replace(Regex("""^\d+[.)]\s+"""), "")
            text = text.replace(Regex("""^---+$"""), "")
            SearchText.stripImagesForPreview(text)
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    return if (cleaned.length > maxChars) cleaned.take(maxChars) + "…" else cleaned
}

@Composable
private fun MomentsNineGrid(images: List<String>) {
    if (images.size == 1) {
        MomentImageTile(
            path = images.first(),
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(4f / 3f),
        )
        return
    }
    val cols = if (images.size == 4) 2 else 3
    val rows = images.chunked(cols)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { path ->
                    MomentImageTile(
                        path = path,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
                repeat(cols - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MomentImageTile(path: String, modifier: Modifier = Modifier) {
    val media = LocalMedia.current
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { media.loadPreview(path, maxSide = 720) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "笔记图片",
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "图片",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
