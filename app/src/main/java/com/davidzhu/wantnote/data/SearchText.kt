package com.davidzhu.wantnote.data

object SearchText {
    fun fromBlocks(blocks: List<NoteBlock>): String {
        return blocks.sortedBy { it.sortOrder }.joinToString("\n") { block ->
            when (block.blockType) {
                BlockType.TASK -> {
                    val mark = if (block.checked) "[x] " else "[ ] "
                    mark + block.text
                }
                BlockType.DIVIDER -> "---"
                BlockType.IMAGE -> "[图片]"
                BlockType.VIDEO -> "[视频]"
                BlockType.FILE -> "[文件] " + block.text
                BlockType.ORDERED, BlockType.UNORDERED, BlockType.PARAGRAPH -> block.text
            }
        }
    }

    fun displayTitle(note: Note): String {
        val titled = note.title.trim()
        if (titled.isNotEmpty()) return titled
        return firstMeaningfulLine(note.body).ifBlank { "无标题" }
    }

    fun preview(note: Note): String {
        val body = note.body.trim()
        if (note.title.isBlank()) {
            val lines = body.lineSequence().map { stripDecor(it) }.filter { it.isNotBlank() }.toList()
            return lines.drop(1).joinToString(" ").ifBlank { lines.firstOrNull().orEmpty() }
        }
        return firstMeaningfulLine(body)
    }

    fun firstMeaningfulLine(body: String): String {
        return body.lineSequence()
            .map { stripDecor(it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    fun stripDecor(line: String): String {
        return line
            .trim()
            .removePrefix("[x] ")
            .removePrefix("[ ] ")
            .replace("[图片]", "")
            .replace("[视频]", "")
            .replace("[文件]", "")
            .replace("---", "")
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "$1")
            .replace(Regex("""==(.+?)=="""), "$1")
            .replace(Regex("""~~(.+?)~~"""), "$1")
            .trim()
    }

    fun extractHighlights(text: String): List<String> {
        return Regex("""==(.+?)==""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
