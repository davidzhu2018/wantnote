package com.davidzhu.wantnote.data

/**
 * Serialization of note-to-note links inside block text.
 *
 * A link is stored as `[[note:<stable-note-id>]]`. Only the ID is persisted;
 * the visible title is resolved from the note library at render time, so a
 * renamed note keeps all of its links and never shows a stale title.
 *
 * This object is deliberately Android-free so the parsing rules can be unit
 * tested on the JVM.
 */
object NoteLinkCodec {
    private val markerRegex = Regex("""\[\[note:([A-Za-z0-9._-]+)]]""")

    fun marker(noteId: String): String = "[[note:$noteId]]"

    /** All target IDs referenced in [text], deduplicated in appearance order. */
    fun targetIds(text: String): List<String> {
        return markerRegex.findAll(text)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    /** All target IDs referenced by the given blocks. */
    fun targetIds(blocks: List<NoteBlock>): List<String> {
        return blocks.asSequence()
            .filter { !it.isMedia && it.blockType != BlockType.DIVIDER }
            .flatMap { targetIds(it.text).asSequence() }
            .distinct()
            .toList()
    }

    /**
     * Replace every link marker with its resolved title. Unknown/deleted
     * targets fall back to [missingLabel] instead of leaking the raw ID or
     * throwing. Used by previews and exports.
     */
    fun renderPlain(
        text: String,
        resolveTitle: (String) -> String?,
        missingLabel: String = "已删除的笔记",
    ): String {
        return markerRegex.replace(text) { match ->
            val title = resolveTitle(match.groupValues[1])?.trim()
            if (title.isNullOrEmpty()) missingLabel else title
        }
    }

    fun containsLink(text: String): Boolean = markerRegex.containsMatchIn(text)
}

/**
 * An in-progress `@` mention. [start] points at the `@`, [end] at the caret.
 * The text between them (the query) is what the note picker searches for.
 */
data class NoteMention(
    val start: Int,
    val end: Int,
    val query: String,
)

object NoteMentionParser {
    /**
     * Return the active `@` mention immediately before [caret], or null.
     * The `@` must start the text or follow whitespace/opening punctuation,
     * and the query may not contain whitespace, another `@` or a line break.
     */
    fun activeMention(text: String, caret: Int): NoteMention? {
        val safeCaret = caret.coerceIn(0, text.length)
        if (safeCaret == 0) return null
        var index = safeCaret - 1
        while (index >= 0) {
            val char = text[index]
            if (char == '\n' || char == '\r' || char.isWhitespace()) return null
            if (char == '@') {
                val before = if (index == 0) ' ' else text[index - 1]
                if (!(before.isWhitespace() || before in "([{（【《，,。;；:：")) return null
                val query = text.substring(index + 1, safeCaret)
                if (query.any { it == '@' || it == '\n' || it == '\r' }) return null
                return NoteMention(start = index, end = safeCaret, query = query)
            }
            index -= 1
        }
        return null
    }
}
