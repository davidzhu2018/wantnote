package com.davidzhu.wantnote.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.transfer.MarkdownBlock
import com.davidzhu.wantnote.data.Markup
import com.davidzhu.wantnote.transfer.MarkdownCodec
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.theme.LocalFontScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Markdown 预览面板（0.9.0 源码模式的下半屏）。
 *
 * 覆盖全部基础格式：图片 / 多媒体（视频、文件、音频附件）/ 多级标题 /
 * 有序无序列表 / 表格（GFM 管道，含列对齐）/ 缩进 / 分割线 / 引用 /
 * 删除线 / 斜体 / 粗体 / 高亮 / `[[note:id]]` 双链 / 任务列表 / 围栏代码。
 * 渲染只读；编辑内容回到上半屏的源码文本框。
 */
@Composable
fun MarkdownPreview(
    content: String,
    modifier: Modifier = Modifier,
    linkTitles: Map<String, String> = emptyMap(),
    onOpenAttachment: (String) -> Unit = {},
    scrollable: Boolean = true,
) {
    val blocks = remember(content) {
        MarkdownCodec.parseMarkdown(
            content,
            fallbackTitle = "",
            resolveMedia = { it },
            consumeLeadingTitle = false,
        ).blocks
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val columnModifier = if (scrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }
    Column(
        modifier = columnModifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (blocks.isEmpty()) {
            Text(
                "（空）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        blocks.forEachIndexed { index, block ->
            when (block.type) {
                BlockType.HEADING -> PreviewHeading(block)
                BlockType.QUOTE -> PreviewQuote(
                    block,
                    linkTitles = linkTitles,
                    highlight = highlight,
                )
                BlockType.CODE -> PreviewCode(block)
                BlockType.TASK -> PreviewTask(block, linkTitles = linkTitles)
                BlockType.ORDERED -> PreviewListItem(
                    block,
                    marker = "${orderedNumber(blocks, index)}.",
                    linkTitles = linkTitles,
                )
                BlockType.UNORDERED -> PreviewListItem(
                    block,
                    marker = "•",
                    linkTitles = linkTitles,
                )
                BlockType.DIVIDER -> HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                BlockType.IMAGE -> PreviewImage(path = block.mediaPath)
                BlockType.VIDEO -> PreviewVideo(
                    path = block.mediaPath,
                    onOpen = { onOpenAttachment(block.mediaPath) },
                )
                BlockType.FILE -> PreviewFile(
                    path = block.mediaPath,
                    label = block.text,
                    onOpen = { onOpenAttachment(block.mediaPath) },
                )
                BlockType.TABLE -> PreviewTable(block, linkTitles = linkTitles)
                BlockType.PARAGRAPH -> MarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    linkTitles = linkTitles,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 与编辑器一致：数出当前 ORDERED 块在同一缩进层里的序号。 */
private fun orderedNumber(blocks: List<MarkdownBlock>, index: Int): Int {
    val current = blocks.getOrNull(index) ?: return 1
    if (current.type != BlockType.ORDERED) return 1
    var count = 1
    var cursor = index - 1
    while (cursor >= 0) {
        val other = blocks[cursor]
        if (other.indent < current.indent) break
        if (other.indent == current.indent) {
            if (other.type != BlockType.ORDERED) break
            count++
        }
        cursor--
    }
    return count
}

@Composable
private fun PreviewHeading(block: MarkdownBlock) {
    val scale = LocalFontScale.current
    val level = block.headingLevel.coerceIn(1, 6)
    val base = when (level) {
        1 -> 26.sp
        2 -> 22.sp
        3 -> 19.sp
        4 -> 17.sp
        5 -> 15.sp
        else -> 14.sp
    }
    Text(
        text = Markup.toAnnotatedString(block.text, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = (base.value * scale).sp,
        lineHeight = (base.value * scale * 1.4f).sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PreviewQuote(
    block: MarkdownBlock,
    linkTitles: Map<String, String>,
    highlight: Color,
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
        )
        MarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            linkTitles = linkTitles,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
    }
}

@Composable
private fun PreviewCode(block: MarkdownBlock) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            block.text.split("\n").forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewTask(
    block: MarkdownBlock,
    linkTitles: Map<String, String>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = (block.indent * 16).dp),
    ) {
        // 预览只展示勾选状态；勾选编辑回源码里改 [ ] / [x]。
        Checkbox(checked = block.checked, onCheckedChange = null)
        MarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (block.checked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            linkTitles = linkTitles,
        )
    }
}

@Composable
private fun PreviewListItem(
    block: MarkdownBlock,
    marker: String,
    linkTitles: Map<String, String>,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(start = (block.indent * 16).dp),
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .width(24.dp)
                .padding(end = 4.dp),
        )
        MarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            linkTitles = linkTitles,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreviewImage(path: String) {
    // 0.10.0：本地附件与远程 http(s) 图片统一由 NoteImage 真实渲染。
    NoteImage(path = path, fallbackLabel = "图片无法显示")
}

@Composable
private fun PreviewVideo(path: String, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall)
                Text(
                    "点击用系统播放器打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewFile(path: String, label: String, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    label.ifBlank { path.substringAfterLast('/') },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "点击用系统应用打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** GFM 管道表格：表头加粗，按分隔行解析列对齐；解析失败按原文段落显示。 */
@Composable
private fun PreviewTable(
    block: MarkdownBlock,
    linkTitles: Map<String, String>,
) {
    val grid = remember(block.text) { MarkdownCodec.parseTable(block.text) }
    if (grid == null) {
        MarkdownText(
            text = block.text.replace("\n", "  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            linkTitles = linkTitles,
        )
        return
    }
    val outline = MaterialTheme.colorScheme.outline
    val columns = maxOf(
        grid.header.size,
        grid.alignments.size,
        grid.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columns <= 0) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(0.5.dp, outline, RoundedCornerShape(6.dp)),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            (0 until columns).forEach { col ->
                TableCell(
                    text = grid.header.getOrNull(col).orEmpty(),
                    alignment = grid.alignments.getOrNull(col) ?: 0,
                    header = true,
                    linkTitles = linkTitles,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        grid.rows.forEach { row ->
            HorizontalDivider(color = outline.copy(alpha = 0.6f))
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until columns).forEach { col ->
                    TableCell(
                        text = row.getOrNull(col).orEmpty(),
                        alignment = grid.alignments.getOrNull(col) ?: 0,
                        header = false,
                        linkTitles = linkTitles,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    alignment: Int,
    header: Boolean,
    linkTitles: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val contentAlignment = when (alignment) {
        1 -> Alignment.Center
        2 -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    Box(
        modifier = modifier
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            .padding(6.dp),
        contentAlignment = contentAlignment,
    ) {
        MarkdownText(
            text = text,
            style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            linkTitles = linkTitles,
            maxLines = 6,
        )
    }
}
