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
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Text
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.davidzhu.wantnote.transfer.LatexMath
import com.davidzhu.wantnote.transfer.MarkdownCodec
import com.davidzhu.wantnote.transfer.MermaidDiagram
import com.davidzhu.wantnote.transfer.MermaidNode
import com.davidzhu.wantnote.transfer.MermaidParser
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.theme.LocalFontScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.davidzhu.wantnote.ui.components.NoteImage

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
    onOpenNote: ((String) -> Unit)? = null,
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
                    onOpenNote = onOpenNote,
                )
                BlockType.CODE -> {
                    if (MermaidParser.isMermaidCodeBlock(block.text)) {
                        PreviewMermaid(block)
                    } else {
                        PreviewCode(block)
                    }
                }
                BlockType.TASK -> PreviewTask(block, linkTitles = linkTitles, onOpenNote = onOpenNote)
                BlockType.ORDERED -> PreviewListItem(
                    block,
                    marker = "${orderedNumber(blocks, index)}.",
                    linkTitles = linkTitles,
                    onOpenNote = onOpenNote,
                )
                BlockType.UNORDERED -> PreviewListItem(
                    block,
                    marker = "•",
                    linkTitles = linkTitles,
                    onOpenNote = onOpenNote,
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
                BlockType.TABLE -> PreviewTable(block, linkTitles = linkTitles, onOpenNote = onOpenNote)
                BlockType.PARAGRAPH -> {
                    val latexFormula = LatexMath.extractBlockFormula(block.text)
                    if (latexFormula != null) {
                        PreviewLatexBlock(formula = latexFormula)
                    } else {
                        MarkdownText(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            linkTitles = linkTitles,
                            onOpenNote = onOpenNote,
                        )
                    }
                }
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
    onOpenNote: ((String) -> Unit)? = null,
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
            onOpenNote = onOpenNote,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
    }
}

@Composable
private fun PreviewCode(block: MarkdownBlock) {
    WantSurface(
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
    onOpenNote: ((String) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = (block.indent * 16).dp),
    ) {
        // 预览只展示勾选状态；勾选编辑回源码里改 [ ] / [x]。
        WantCheckbox(checked = block.checked, onCheckedChange = null)
        MarkdownText(
            text = block.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (block.checked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            linkTitles = linkTitles,
            onOpenNote = onOpenNote,
        )
    }
}

@Composable
private fun PreviewListItem(
    block: MarkdownBlock,
    marker: String,
    linkTitles: Map<String, String>,
    onOpenNote: ((String) -> Unit)? = null,
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
            onOpenNote = onOpenNote,
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
    WantSurface(
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
    WantSurface(
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
    onOpenNote: ((String) -> Unit)? = null,
) {
    val grid = remember(block.text) { MarkdownCodec.parseTable(block.text) }
    if (grid == null) {
        MarkdownText(
            text = block.text.replace("\n", "  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            linkTitles = linkTitles,
            onOpenNote = onOpenNote,
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


/**
 * 0.16.5：离线原生 Mermaid 流程图渲染卡片（支持 TD/LR 拓扑分层、节点形状与连线标签）。
 */
@Composable
private fun PreviewMermaid(block: MarkdownBlock) {
    val diagram = remember(block.text) { MermaidParser.parse(block.text) }
    if (diagram == null) {
        PreviewCode(block)
        return
    }
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val nodeBg = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    val dirLabel = if (diagram.direction == MermaidDiagram.Direction.LEFT_RIGHT) "从左到右" else "自上而下"

    WantSurface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Mermaid 流程图 · $dirLabel",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Medium,
            )
            if (diagram.direction == MermaidDiagram.Direction.LEFT_RIGHT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    diagram.layers.forEachIndexed { layerIdx, layer ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            layer.forEach { node ->
                                MermaidNodeChip(node = node, borderColor = borderColor, backgroundColor = nodeBg)
                            }
                        }
                        if (layerIdx < diagram.layers.lastIndex) {
                            val nextIds = diagram.layers[layerIdx + 1].map { it.id }.toSet()
                            val curIds = layer.map { it.id }.toSet()
                            val edgeLabels = diagram.edges
                                .filter { it.from in curIds && it.to in nextIds && it.label.isNotBlank() }
                                .map { it.label }
                                .distinct()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (edgeLabels.isNotEmpty()) {
                                    Text(
                                        text = edgeLabels.joinToString("/"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("→", color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    diagram.layers.forEachIndexed { layerIdx, layer ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            layer.forEach { node ->
                                MermaidNodeChip(node = node, borderColor = borderColor, backgroundColor = nodeBg)
                            }
                        }
                        if (layerIdx < diagram.layers.lastIndex) {
                            val nextIds = diagram.layers[layerIdx + 1].map { it.id }.toSet()
                            val curIds = layer.map { it.id }.toSet()
                            val edgeLabels = diagram.edges
                                .filter { it.from in curIds && it.to in nextIds && it.label.isNotBlank() }
                                .map { it.label }
                                .distinct()
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("↓", color = accent, fontWeight = FontWeight.Bold)
                                if (edgeLabels.isNotEmpty()) {
                                    Text(
                                        text = edgeLabels.joinToString(" / "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (diagram.edges.isNotEmpty()) {
                val nodeById = remember(diagram) { diagram.nodes.associateBy { it.id } }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    diagram.edges.forEach { edge ->
                        val fromLabel = nodeById[edge.from]?.label ?: edge.from
                        val toLabel = nodeById[edge.to]?.label ?: edge.to
                        val arrow = if (edge.label.isBlank()) "→" else "—[${edge.label}]→"
                        Text(
                            text = "$fromLabel $arrow $toLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MermaidNodeChip(
    node: MermaidNode,
    borderColor: Color,
    backgroundColor: Color,
) {
    val shape = when (node.shape) {
        MermaidNode.Shape.CIRCLE -> CircleShape
        MermaidNode.Shape.ROUND -> RoundedCornerShape(18.dp)
        MermaidNode.Shape.DIAMOND -> RoundedCornerShape(4.dp)
        MermaidNode.Shape.RECT -> RoundedCornerShape(8.dp)
    }
    val prefix = if (node.shape == MermaidNode.Shape.DIAMOND) "◇ " else ""
    Box(
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (node.shape == MermaidNode.Shape.DIAMOND) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = prefix + node.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 0.16.5：离线原生 LaTeX 块级公式渲染卡片（`$$ ... $$`）。
 */
@Composable
private fun PreviewLatexBlock(formula: String) {
    val scale = LocalFontScale.current
    val formatted = remember(formula) { LatexMath.format(formula) }
    WantSurface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatted,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = (18 * scale).sp,
                lineHeight = (26 * scale).sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}
