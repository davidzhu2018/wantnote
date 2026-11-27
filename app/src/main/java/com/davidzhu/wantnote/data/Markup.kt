package com.davidzhu.wantnote.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

object Markup {
    private val token = Regex(
        """\*\*(.+?)\*\*|(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|==(.+?)==|~~(.+?)~~""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun toAnnotatedString(text: String, highlightColor: Color): AnnotatedString {
        return buildAnnotatedString {
            var index = 0
            for (match in token.findAll(text)) {
                if (match.range.first > index) {
                    append(text.substring(index, match.range.first))
                }
                when {
                    match.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groupValues[1])
                    }
                    match.groups[2] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(match.groupValues[2])
                    }
                    match.groups[3] != null -> withStyle(SpanStyle(background = highlightColor)) {
                        append(match.groupValues[3])
                    }
                    match.groups[4] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(match.groupValues[4])
                    }
                }
                index = match.range.last + 1
            }
            if (index < text.length) append(text.substring(index))
        }
    }

    fun wrap(text: String, start: Int, end: Int, marker: String): Triple<String, Int, Int> {
        val lo = start.coerceIn(0, text.length)
        val hi = end.coerceIn(0, text.length).coerceAtLeast(lo)
        if (lo == hi) {
            val next = text.substring(0, lo) + marker + marker + text.substring(hi)
            val caret = lo + marker.length
            return Triple(next, caret, caret)
        }
        val next = text.substring(0, lo) + marker + text.substring(lo, hi) + marker + text.substring(hi)
        val afterClosing = hi + marker.length * 2
        return Triple(next, afterClosing, afterClosing)
    }
}
