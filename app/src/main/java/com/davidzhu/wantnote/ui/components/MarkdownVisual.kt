package com.davidzhu.wantnote.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.davidzhu.wantnote.data.NoteLinkCodec

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
) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkTitles, highlight, linkColor) {
        MarkdownVisual.render(text, highlight, linkTitles, linkColor).text
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Render a single line that may still contain note markers (for exports). */
fun renderNoteLinks(text: String, linkTitles: Map<String, String>): String =
    NoteLinkCodec.renderPlain(text, resolveTitle = { id -> linkTitles[id] })
