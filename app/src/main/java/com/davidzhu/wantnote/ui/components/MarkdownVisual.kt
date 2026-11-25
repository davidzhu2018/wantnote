package com.davidzhu.wantnote.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidzhu.wantnote.data.NoteLinkCodec
import com.davidzhu.wantnote.ui.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inline Markdown rendering shared by the editor and the home previews.
 *
 * Block syntax (headings, lists, quotes, fences) is handled by the block
 * model; this renderer only hides inline source markers and applies real
 * emphasis/highlight/code/link styling. The stored text is never modified.
 */
object MarkdownVisual {
    private val token = Regex(
        """\[\[note:([A-Za-z0-9._-]+)]]""" +
            """|!\[([^\]]*)]\(([^)]*)\)""" +
            """|\[([^\]]*)]\(([^)]*)\)""" +
            """|`([^`\n]*)`""" +
            """|\*\*(.+?)\*\*""" +
            """|(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""" +
            """|==(.+?)==""" +
            """|~~(.+?)~~""",
        RegexOption.DOT_MATCHES_ALL,
    )

    data class Rendered(
        val text: AnnotatedString,
        val mapping: OffsetMapping,
    )

    fun transformation(
        highlightColor: Color,
        linkTitles: Map<String, String> = emptyMap(),
        linkColor: Color = Color.Unspecified,
    ): VisualTransformation = VisualTransformation { original ->
        val rendered = render(original.text, highlightColor, linkTitles, linkColor)
        TransformedText(rendered.text, rendered.mapping)
    }

    fun render(
        source: String,
        highlightColor: Color,
        linkTitles: Map<String, String> = emptyMap(),
        linkColor: Color = Color.Unspecified,
    ): Rendered {
        if (source.isEmpty()) {
            return Rendered(AnnotatedString(""), OffsetMapping.Identity)
        }
        val segments = ArrayList<Segment>()
        var cursor = 0
        for (match in token.findAll(source)) {
            if (match.range.first > cursor) {
                segments += Segment(cursor, match.range.first, source.substring(cursor, match.range.first), null)
            }
            val range = match.range
            val noteId = match.groups[1]?.value
            val imageAlt = match.groups[2]?.value
            val linkLabel = match.groups[4]?.value
            val code = match.groups[6]?.value
            val bold = match.groups[7]?.value
            val italic = match.groups[8]?.value
            val highlight = match.groups[9]?.value
            val strike = match.groups[10]?.value
            when {
                noteId != null -> {
                    val title = linkTitles[noteId]?.takeIf { it.isNotBlank() } ?: "已删除的笔记"
                    segments += Segment(
                        range.first, range.last + 1, "→ $title",
                        SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                    )
                }
                imageAlt != null -> segments += Segment(
                    range.first, range.last + 1, if (imageAlt.isBlank()) "[图片]" else "[图片] $imageAlt",
                    SpanStyle(color = linkColor),
                )
                linkLabel != null -> segments += Segment(
                    range.first, range.last + 1, linkLabel.ifBlank { match.groups[5]?.value.orEmpty() },
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                )
                code != null -> segments += Segment(
                    range.first, range.last + 1, code,
                    SpanStyle(fontFamily = FontFamily.Monospace, background = highlightColor.copy(alpha = 0.25f)),
                )
                bold != null -> segments += Segment(
                    range.first, range.last + 1, bold,
                    SpanStyle(fontWeight = FontWeight.Bold),
                )
                italic != null -> segments += Segment(
                    range.first, range.last + 1, italic,
                    SpanStyle(fontStyle = FontStyle.Italic),
                )
                highlight != null -> segments += Segment(
                    range.first, range.last + 1, highlight,
                    SpanStyle(background = highlightColor),
                )
                strike != null -> segments += Segment(
                    range.first, range.last + 1, strike,
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                )
            }
            cursor = range.last + 1
        }
        if (cursor < source.length) {
            segments += Segment(cursor, source.length, source.substring(cursor), null)
        }

        val out = buildAnnotatedString {
            var emitted = 0
            segments.forEach { segment ->
                if (segment.visible.isNotEmpty()) {
                    val start = emitted
                    append(segment.visible)
                    emitted += segment.visible.length
                    segment.style?.let { addStyle(it, start, emitted) }
                }
            }
        }

        val n = source.length
        val originalTo = IntArray(n + 1) { -1 }
        var outputIndex = 0
        var sourceIndex = 0
        segments.forEach { segment ->
            while (sourceIndex < segment.start) {
                originalTo[sourceIndex] = outputIndex
                sourceIndex += 1
            }
            originalTo[segment.start] = outputIndex
            if (segment.visible.length == segment.end - segment.start &&
                segment.visible == source.substring(segment.start, segment.end)
            ) {
                for (offset in 1..segment.visible.length) {
                    originalTo[segment.start + offset] = outputIndex + offset
                }
            } else {
                for (offset in 1 until segment.end - segment.start) {
                    originalTo[segment.start + offset] = outputIndex
                }
                originalTo[segment.end] = outputIndex + segment.visible.length
            }
            outputIndex += segment.visible.length
            sourceIndex = segment.end
        }
        while (sourceIndex <= n) {
            originalTo[sourceIndex] = outputIndex
            sourceIndex += 1
        }
        val transformedLength = emittedLength(segments)
        val reverse = IntArray(transformedLength + 1) { n }
        for (i in 0..n) {
            val t = originalTo[i].coerceIn(0, transformedLength)
            if (reverse[t] == n || i < reverse[t]) reverse[t] = i
        }
        var last = 0
        for (i in 0..transformedLength) {
            if (reverse[i] == n && i > 0) reverse[i] = last else last = reverse[i]
        }

        return Rendered(
            text = out,
            mapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    originalTo[offset.coerceIn(0, n)].coerceIn(0, transformedLength)

                override fun transformedToOriginal(offset: Int): Int =
                    reverse[offset.coerceIn(0, transformedLength)].coerceIn(0, n)
            },
        )
    }

    /**
     * 只读面（预览、卡片、搜索）的富渲染：与 [render] 同一套行内语法，但
     * 额外提供 0.10.0 的两项真实渲染——
     * - http(s) 超链接用 [LinkAnnotation] 输出，`Text` 会自动响应点击（用
     *   系统 UriHandler 打开浏览器），不再只是带下划线的样式文本；
     * - 行内 `![alt](url)` 图片输出为 inlineContent 占位，由调用方把
     *   [RichRendered.inlineImages] 里的 URL 渲染成真实位图。
     */
    fun renderRich(
        source: String,
        highlightColor: Color,
        linkTitles: Map<String, String> = emptyMap(),
        linkColor: Color = Color.Unspecified,
        renderInlineImages: Boolean = true,
    ): RichRendered {
        if (source.isEmpty()) return RichRendered(AnnotatedString(""), emptyMap())
        val inlineImages = LinkedHashMap<String, String>()
        var imageIndex = 0
        val text = buildAnnotatedString {
            var cursor = 0
            for (match in token.findAll(source)) {
                if (match.range.first > cursor) {
                    append(source.substring(cursor, match.range.first))
                }
                val range = match.range
                val noteId = match.groups[1]?.value
                val imageAlt = match.groups[2]?.value
                val linkLabel = match.groups[4]?.value
                val code = match.groups[6]?.value
                val bold = match.groups[7]?.value
                val italic = match.groups[8]?.value
                val highlight = match.groups[9]?.value
                val strike = match.groups[10]?.value
                when {
                    noteId != null -> {
                        val title = linkTitles[noteId]?.takeIf { it.isNotBlank() } ?: "已删除的笔记"
                        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                            append("→ $title")
                        }
                    }
                    imageAlt != null -> {
                        if (renderInlineImages) {
                            val url = match.groups[3]?.value.orEmpty().trim()
                            if (url.isNotEmpty()) {
                                val id = "img${imageIndex++}"
                                inlineImages[id] = url
                                appendInlineContent(id, if (imageAlt.isBlank()) "[图片]" else "[图片] $imageAlt")
                            } else {
                                append(source.substring(range))
                            }
                        }
                    }
                    linkLabel != null -> {
                        val url = match.groups[5]?.value.orEmpty().trim()
                        val label = linkLabel.ifBlank { url }
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            withLink(
                                LinkAnnotation.Url(
                                    url,
                                    TextLinkStyles(
                                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                    ),
                                ),
                            ) {
                                append(label)
                            }
                        } else {
                            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                append(label)
                            }
                        }
                    }
                    code != null -> withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = highlightColor.copy(alpha = 0.25f)),
                    ) { append(code) }
                    bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                    italic != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                    highlight != null -> withStyle(SpanStyle(background = highlightColor)) { append(highlight) }
                    strike != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(strike) }
                }
                cursor = range.last + 1
            }
            if (cursor < source.length) append(source.substring(cursor))
        }
        return RichRendered(text, inlineImages)
    }

    data class RichRendered(
        val text: AnnotatedString,
        /** inlineContent id → 图片 URL（本地附件路径或 http(s) URL）。 */
        val inlineImages: Map<String, String>,
    )

    private fun emittedLength(segments: List<Segment>): Int = segments.sumOf { it.visible.length }

    private data class Segment(
        val start: Int,
        val end: Int,
        val visible: String,
        val style: SpanStyle?,
    )
}

@Composable
fun MarkdownText(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    linkTitles: Map<String, String> = emptyMap(),
    renderInlineImages: Boolean = true,
) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val linkColor = MaterialTheme.colorScheme.primary
    // 0.10.0：只读渲染走 renderRich——超链接可点击；0.12.0 支持在主页卡片预览关闭行内图片。
    val rendered = remember(text, linkTitles, highlight, linkColor, renderInlineImages) {
        MarkdownVisual.renderRich(text, highlight, linkTitles, linkColor, renderInlineImages = renderInlineImages)
    }
    val inline = remember(rendered.inlineImages) {
        rendered.inlineImages.mapValues { (_, url) ->
            InlineTextContent(
                Placeholder(width = 64.sp, height = 40.sp, PlaceholderVerticalAlign.TextCenter),
            ) {
                InlineNoteImage(url)
            }
        }
    }
    Text(
        text = rendered.text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inline,
    )
}

/** 行内小图：本地附件或 http(s) URL，加载失败显示占位小块。 */
@Composable
private fun InlineNoteImage(url: String) {
    val media = LocalMedia.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) { media.loadPreview(url, maxSide = 640) }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "图片",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
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

/** Render a single line that may still contain note markers (for exports). */
fun renderNoteLinks(text: String, linkTitles: Map<String, String>): String =
    NoteLinkCodec.renderPlain(text, resolveTitle = { id -> linkTitles[id] })
