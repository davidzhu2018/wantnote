package com.davidzhu.wantnote.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.graphics.luminance
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
import com.davidzhu.wantnote.transfer.LatexMath
import com.davidzhu.wantnote.transfer.MarkdownCodec
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
            """|<[iI][mM][gG]\b([^>]*)>""" +
            """|\[([^\]]*)]\(([^)]*)\)""" +
            """|`([^`\n]*)`""" +
            """|(?<!\$)\$(?!\$|\s)([^$\n]+?)(?<!\s)\$(?!\$)""" +
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
            val htmlImgAttrs = match.groups[4]?.value
            val linkLabel = match.groups[5]?.value
            val code = match.groups[7]?.value
            val latexInline = match.groups[8]?.value
            val bold = match.groups[9]?.value
            val italic = match.groups[10]?.value
            val highlight = match.groups[11]?.value
            val strike = match.groups[12]?.value
            when {
                noteId != null -> {
                    val title = linkTitles[noteId]?.takeIf { it.isNotBlank() } ?: "已删除的笔记"
                    segments += Segment(
                        range.first, range.last + 1, "[[$title]]",
                        SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                }
                imageAlt != null -> segments += Segment(
                    range.first, range.last + 1, if (imageAlt.isBlank()) "[图片]" else "[图片] $imageAlt",
                    SpanStyle(color = linkColor),
                )
                htmlImgAttrs != null -> {
                    val alt = MarkdownCodec.extractHtmlAttr(htmlImgAttrs, "alt").orEmpty()
                    segments += Segment(
                        range.first, range.last + 1, if (alt.isBlank()) "[图片]" else "[图片] $alt",
                        SpanStyle(color = linkColor),
                    )
                }
                linkLabel != null -> segments += Segment(
                    range.first, range.last + 1, linkLabel.ifBlank { match.groups[6]?.value.orEmpty() },
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    visibleOffsetInSource = if (linkLabel.isNotBlank()) 1 else -1,
                )
                code != null -> segments += Segment(
                    range.first, range.last + 1, code,
                    SpanStyle(fontFamily = FontFamily.Monospace, background = highlightColor.copy(alpha = 0.25f)),
                    visibleOffsetInSource = 1,
                )
                latexInline != null -> segments += Segment(
                    range.first, range.last + 1, LatexMath.format(latexInline),
                    SpanStyle(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        background = highlightColor.copy(alpha = 0.16f),
                    ),
                )
                bold != null -> segments += Segment(
                    range.first, range.last + 1, bold,
                    SpanStyle(fontWeight = FontWeight.Bold),
                    visibleOffsetInSource = 2,
                )
                italic != null -> segments += Segment(
                    range.first, range.last + 1, italic,
                    SpanStyle(fontStyle = FontStyle.Italic),
                    visibleOffsetInSource = 1,
                )
                highlight != null -> segments += Segment(
                    range.first, range.last + 1, highlight,
                    SpanStyle(background = highlightColor),
                    visibleOffsetInSource = 2,
                )
                strike != null -> segments += Segment(
                    range.first, range.last + 1, strike,
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    visibleOffsetInSource = 2,
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
            } else if (segment.visibleOffsetInSource >= 0 &&
                segment.start + segment.visibleOffsetInSource + segment.visible.length <= segment.end
            ) {
                val prefixLen = segment.visibleOffsetInSource
                val visLen = segment.visible.length
                val totalLen = segment.end - segment.start
                for (offset in 1..prefixLen) {
                    originalTo[segment.start + offset] = outputIndex
                }
                for (offset in 1..visLen) {
                    originalTo[segment.start + prefixLen + offset] = outputIndex + offset
                }
                for (offset in (prefixLen + visLen + 1)..totalLen) {
                    originalTo[segment.start + offset] = outputIndex + visLen
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
        val reverse = IntArray(transformedLength + 1) { -1 }
        // 反向映射优先取最右侧的原文偏移（例如 `**abc**` 结尾映射到闭合 `**` 之后，
        // 这样点在格式化文字末尾或拖到末尾后可以继续输入正文，不会卡在语法符号前面）。
        for (i in 0..n) {
            val t = originalTo[i].coerceIn(0, transformedLength)
            reverse[t] = i
        }
        var last = 0
        for (i in 0..transformedLength) {
            if (reverse[i] < 0) reverse[i] = last else last = reverse[i]
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
        noteLinkColor: Color = Color(0xFF1A73E8),
        renderInlineImages: Boolean = true,
        onOpenNote: ((String) -> Unit)? = null,
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
                val htmlImgAttrs = match.groups[4]?.value
                val linkLabel = match.groups[5]?.value
                val code = match.groups[7]?.value
                val latexInline = match.groups[8]?.value
                val bold = match.groups[9]?.value
                val italic = match.groups[10]?.value
                val highlight = match.groups[11]?.value
                val strike = match.groups[12]?.value
                when {
                    noteId != null -> {
                        val title = linkTitles[noteId]?.takeIf { it.isNotBlank() } ?: "已删除的笔记"
                        val linkStyle = SpanStyle(
                            color = noteLinkColor,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        )
                        if (onOpenNote != null && linkTitles.containsKey(noteId)) {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "note:$noteId",
                                    styles = TextLinkStyles(style = linkStyle),
                                    linkInteractionListener = { onOpenNote(noteId) },
                                ),
                            ) {
                                append("[[$title]]")
                            }
                        } else {
                            withStyle(linkStyle) {
                                append("[[$title]]")
                            }
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
                    htmlImgAttrs != null -> {
                        if (renderInlineImages) {
                            val url = MarkdownCodec.extractHtmlAttr(htmlImgAttrs, "src").orEmpty().trim()
                            val alt = MarkdownCodec.extractHtmlAttr(htmlImgAttrs, "alt").orEmpty().trim()
                            if (url.isNotEmpty()) {
                                val id = "img${imageIndex++}"
                                inlineImages[id] = url
                                appendInlineContent(id, if (alt.isBlank()) "[图片]" else "[图片] $alt")
                            } else {
                                append(source.substring(range))
                            }
                        }
                    }
                    linkLabel != null -> {
                        val url = match.groups[6]?.value.orEmpty().trim()
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
                    latexInline != null -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            background = highlightColor.copy(alpha = 0.16f),
                        ),
                    ) { append(LatexMath.format(latexInline)) }
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
        /** When >= 0, visible characters map 1-to-1 starting at `start + visibleOffsetInSource`. */
        val visibleOffsetInSource: Int = -1,
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
    onOpenNote: ((String) -> Unit)? = null,
) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val linkColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val noteLinkColor = if (isDark) Color(0xFF6EA8FE) else Color(0xFF1A73E8)
    // 0.10.0：只读渲染走 renderRich——超链接可点击；0.14.0：双链渲染为蓝色带下划线 [[标题]] 并支持点击跳转。
    val rendered = remember(text, linkTitles, highlight, linkColor, noteLinkColor, renderInlineImages, onOpenNote) {
        MarkdownVisual.renderRich(
            source = text,
            highlightColor = highlight,
            linkTitles = linkTitles,
            linkColor = linkColor,
            noteLinkColor = noteLinkColor,
            renderInlineImages = renderInlineImages,
            onOpenNote = onOpenNote,
        )
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
