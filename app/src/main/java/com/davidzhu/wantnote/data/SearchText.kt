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
                BlockType.TABLE -> block.text
                BlockType.HEADING, BlockType.QUOTE, BlockType.CODE,
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


    private val linkedImageRegex = Regex(
        """\[!\[[^\]]*]\((?:<[^>]*>|[^()\n]*(?:\([^()\n]*\)[^()\n]*)*)\)]\((?:<[^>]*>|[^()\n]*(?:\([^()\n]*\)[^()\n]*)*)\)""",
    )
    private val inlineImageRegex = Regex(
        """!\[[^\]]*]\((?:<[^>]*>|[^()\n]*(?:\([^()\n]*\)[^()\n]*)*)\)""",
    )
    private val refImageRegex = Regex("""!\[[^\]]*]\[[^\]]*]""")
    private val htmlImgRegex = Regex("""(?i)<img\b[^>]*>""")
    private val multiSpaceRegex = Regex("""[ \t]{2,}""")

    /**
     * 0.12.0：移除预览行中的图片语法（`![alt](url)`、嵌套链接图 `[![...](...)](...)`、
     * 引用式图片 `![alt][ref]`、HTML `<img>` 与遗留 `[图片]` 占位），避免主页卡片在封面
     * 之外把同一张图再渲成行内小图或泄漏 `![]()` / alt 源码残段。
     * 保留其余行内样式（粗体/斜体/高亮/删除线/代码/超链接/双链）。
     */
    fun stripImagesForPreview(line: String): String {
        var text = line
        text = text.replace(linkedImageRegex, "")
        text = text.replace(inlineImageRegex, "")
        text = text.replace(refImageRegex, "")
        text = text.replace(htmlImgRegex, "")
        text = text.replace("[图片]", "")
        text = text.replace(multiSpaceRegex, " ")
        return text.trim()
    }

    /**
     * Preview text that keeps inline Markdown markers intact so the home
     * cards can render emphasis/links; block prefixes and image syntax are
     * stripped so home cards never duplicate cover images as inline thumbs.
     */
    fun previewMarkdown(note: Note): String {
        val lines = note.body.trim().lineSequence()
            .filter { it.isNotBlank() }
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
                stripImagesForPreview(text)
            }
            .filter { it.isNotBlank() }
            .toList()
        val selected = if (note.title.isBlank()) {
            val titleText = displayTitle(note)
            if (lines.isNotEmpty() && stripDecor(lines.first()) == titleText) {
                lines.drop(1)
            } else {
                lines
            }
        } else {
            lines
        }
        return selected.take(4).joinToString(" ")
    }

    fun firstMeaningfulLine(body: String): String {
        return body.lineSequence()
            .map { stripDecor(it) }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    /**
     * Turn one block/body line into display text. This never changes stored
     * content; it only hides Markdown/link syntax in previews so imported
     * notes do not leak raw `**`, `[]()` or `[[note:...]]` markers.
     */
    fun stripDecor(line: String, linkTitle: (String) -> String? = { null }): String {
        var text = line.trim()
        text = text.removePrefix("[x] ").removePrefix("[ ] ")
        text = text.replace(Regex("""^#{1,6}\s+"""), "")
        text = text.replace(Regex("""^>\s?"""), "")
        text = text.replace(Regex("""^```.*$"""), "")
        text = text.replace(Regex("""^~~~.*$"""), "")
        text = text.replace("[图片]", "")
        text = text.replace("[视频]", "")
        text = text.replace("[文件]", "")
        text = text.replace(Regex("""^[-*+]\s+"""), "")
        text = text.replace(Regex("""^\d+[.)]\s+"""), "")
        text = text.replace("---", "")
        text = NoteLinkCodec.renderPlain(text, linkTitle, missingLabel = "笔记链接")
        text = text.replace(Regex("""!\[([^\]]*)]\([^)]*\)"""), "$1")
        text = text.replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1")
        text = text.replace(Regex("""`([^`]*)`"""), "$1")
        text = text.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        text = text.replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "$1")
        text = text.replace(Regex("""==(.+?)==""", RegexOption.DOT_MATCHES_ALL), "$1")
        text = text.replace(Regex("""~~(.+?)~~"""), "$1")
        return text.trim()
    }

    fun extractHighlights(text: String): List<String> {
        return Regex("""==(.+?)==""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
