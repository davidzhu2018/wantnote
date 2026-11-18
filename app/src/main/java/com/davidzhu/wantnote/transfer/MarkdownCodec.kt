package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.BlockType

/**
 * 与 Android/Compose 无关的轻量块模型：Markdown 导入解析和导出渲染都只
 * 依赖它，因此可以在 JVM 单元测试里做往返验证，再映射到编辑器的
 * [com.davidzhu.wantnote.data.NoteBlock]。
 */
data class MarkdownBlock(
    val type: BlockType,
    val text: String = "",
    val indent: Int = 0,
    val checked: Boolean = false,
    val mediaPath: String = "",
)

data class MarkdownNote(
    val title: String,
    val blocks: List<MarkdownBlock>,
    val missingMedia: List<String> = emptyList(),
)

/**
 * Markdown / HTML ⇄ 块模型。
 *
 * 约定（导出后能原样导入回来）：
 * - `# 标题`：首个 heading 作为笔记标题；其余 heading 变成段落。
 * - `- [ ]` / `- [x]`：TASK。
 * - `1.` / `1)`：ORDERED；`-` / `*` / `+`：UNORDERED。
 * - `---` / `***` / `___`：DIVIDER。
 * - `![说明](media/x)`：IMAGE。
 * - `[视频](media/x)`：VIDEO。
 * - `[文件: 名称](media/x)`：FILE。
 * - 缩进按 2 个空格一级（导入时 tab 记作 2 个空格）。
 * - 行内 `**粗体**`、`*斜体*`、`==高亮==`、`~~删除线~~` 原样保留在
 *   块文本里，与编辑器 Markup 一致。
 */
object MarkdownCodec {
    private val headingRegex = Regex("""^ {0,3}(#{1,6})[ \t]+(.+?)[ \t]*#*[ \t]*$""")
    private val dividerRegex = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
    private val taskRegex = Regex("""^([ \t]*)(?:[-*+]|\d+[.)])[ \t]+\[([ xX])\][ \t]*(.*)$""")
    private val orderedRegex = Regex("""^([ \t]*)\d+[.)][ \t]+(.*)$""")
    private val unorderedRegex = Regex("""^([ \t]*)[-*+][ \t]+(.*)$""")
    private val imageRegex = Regex("""^([ \t]*)!\[([^\]]*)\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")
    private val videoRegex = Regex("""^([ \t]*)\[视频\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")
    private val fileRegex = Regex("""^([ \t]*)\[文件[:：]?[ \t]*([^\]]*)\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")
    private val linkRegex = Regex("""^([ \t]*)\[([^\]]*)\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")

    private val videoExtensions = setOf(
        "mp4", "webm", "3gp", "mkv", "mov", "avi", "m4v",
    )
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif",
    )

    fun parseDocument(
        text: String,
        fallbackTitle: String,
        isHtml: Boolean,
        resolveMedia: (String) -> String? = { null },
    ): MarkdownNote {
        val source = if (isHtml || looksLikeHtml(text)) htmlToMarkdown(text) else text
        return parseMarkdown(source, fallbackTitle, resolveMedia)
    }

    fun parseMarkdown(
        markdown: String,
        fallbackTitle: String,
        resolveMedia: (String) -> String? = { null },
    ): MarkdownNote {
        val normalized = markdown
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val blocks = ArrayList<MarkdownBlock>()
        val missing = ArrayList<String>()
        var title: String? = null

        normalized.split('\n').forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach
            val heading = headingRegex.matchEntire(line)
            if (heading != null) {
                val text = stripInlineMarkup(heading.groupValues[2]).trim()
                if (title == null && text.isNotEmpty()) {
                    title = text
                    return@forEach
                }
                if (text.isNotEmpty()) {
                    blocks += MarkdownBlock(BlockType.PARAGRAPH, text, indentOf(line.takeWhile { it.isWhitespace() }))
                }
                return@forEach
            }
            if (dividerRegex.matches(line)) {
                blocks += MarkdownBlock(BlockType.DIVIDER)
                return@forEach
            }
            val task = taskRegex.matchEntire(line)
            if (task != null) {
                blocks += MarkdownBlock(
                    type = BlockType.TASK,
                    text = task.groupValues[3].trim(),
                    indent = indentOf(task.groupValues[1]),
                    checked = task.groupValues[2].equals("x", ignoreCase = true),
                )
                return@forEach
            }
            val image = imageRegex.matchEntire(line)
            if (image != null) {
                val ref = image.groupValues[3]
                val resolved = resolveMedia(ref)
                if (resolved == null) {
                    missing += ref
                } else {
                    blocks += MarkdownBlock(
                        type = BlockType.IMAGE,
                        text = image.groupValues[2].trim(),
                        indent = indentOf(image.groupValues[1]),
                        mediaPath = resolved,
                    )
                }
                return@forEach
            }
            val video = videoRegex.matchEntire(line)
            if (video != null) {
                val ref = video.groupValues[2]
                val resolved = resolveMedia(ref)
                if (resolved == null) {
                    missing += ref
                } else {
                    blocks += MarkdownBlock(
                        type = BlockType.VIDEO,
                        indent = indentOf(video.groupValues[1]),
                        mediaPath = resolved,
                    )
                }
                return@forEach
            }
            val file = fileRegex.matchEntire(line)
            if (file != null) {
                val ref = file.groupValues[3]
                val resolved = resolveMedia(ref)
                if (resolved == null) {
                    missing += ref
                } else {
                    blocks += MarkdownBlock(
                        type = BlockType.FILE,
                        text = file.groupValues[2].trim(),
                        indent = indentOf(file.groupValues[1]),
                        mediaPath = resolved,
                    )
                }
                return@forEach
            }
            val ordered = orderedRegex.matchEntire(line)
            if (ordered != null) {
                blocks += MarkdownBlock(
                    type = BlockType.ORDERED,
                    text = ordered.groupValues[2].trim(),
                    indent = indentOf(ordered.groupValues[1]),
                )
                return@forEach
            }
            val unordered = unorderedRegex.matchEntire(line)
            if (unordered != null) {
                blocks += MarkdownBlock(
                    type = BlockType.UNORDERED,
                    text = unordered.groupValues[2].trim(),
                    indent = indentOf(unordered.groupValues[1]),
                )
                return@forEach
            }
            val link = linkRegex.matchEntire(line)
            if (link != null) {
                val ref = link.groupValues[3]
                val label = link.groupValues[2].trim()
                if (!isRemoteLink(ref) && isLocalMediaRef(ref)) {
                    val resolved = resolveMedia(ref)
                    if (resolved == null) {
                        missing += ref
                    } else {
                        val type = if (extensionOf(ref) in videoExtensions) {
                            BlockType.VIDEO
                        } else {
                            BlockType.FILE
                        }
                        blocks += MarkdownBlock(
                            type = type,
                            text = if (type == BlockType.FILE) label else "",
                            indent = indentOf(link.groupValues[1]),
                            mediaPath = resolved,
                        )
                    }
                    return@forEach
                }
            }
            val indent = indentOf(line.takeWhile { it.isWhitespace() })
            blocks += MarkdownBlock(
                type = BlockType.PARAGRAPH,
                text = line.trim(),
                indent = indent,
            )
        }

        return MarkdownNote(
            title = title ?: fallbackTitle.trim(),
            blocks = blocks,
            missingMedia = missing.distinct(),
        )
    }

    fun render(
        title: String,
        blocks: List<MarkdownBlock>,
        mediaRef: (String) -> String = { it },
    ): String {
        val out = StringBuilder()
        val cleanTitle = title.replace('\n', ' ').trim()
        if (cleanTitle.isNotEmpty()) {
            out.append("# ").append(cleanTitle).append("\n\n")
        }
        blocks.forEachIndexed { index, block ->
            if (index > 0) out.append('\n')
            val prefix = "  ".repeat(block.indent.coerceIn(0, 8))
            val text = block.text.replace('\n', ' ').trim()
            when (block.type) {
                BlockType.DIVIDER -> out.append(prefix).append("---")
                BlockType.TASK -> out.append(prefix)
                    .append(if (block.checked) "- [x] " else "- [ ] ")
                    .append(text)
                BlockType.ORDERED -> out.append(prefix).append("1. ").append(text)
                BlockType.UNORDERED -> out.append(prefix).append("- ").append(text)
                BlockType.IMAGE -> out.append(prefix)
                    .append("![")
                    .append(text.ifBlank { "图片" })
                    .append("](")
                    .append(mediaRef(block.mediaPath))
                    .append(")")
                BlockType.VIDEO -> out.append(prefix)
                    .append("[视频](")
                    .append(mediaRef(block.mediaPath))
                    .append(")")
                BlockType.FILE -> out.append(prefix)
                    .append("[文件: ")
                    .append(text.ifBlank { "附件" })
                    .append("](")
                    .append(mediaRef(block.mediaPath))
                    .append(")")
                BlockType.PARAGRAPH -> out.append(prefix).append(text)
            }
        }
        return out.toString().trimEnd('\n') + "\n"
    }

    fun htmlToMarkdown(html: String): String {
        var text = html
        text = text.replace(Regex("(?is)<!--.*?-->"), "")
        text = text.replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1\\s*>"), "")
        // 先处理有序/无序列表，任务列表里的 checkbox 转成 [ ] / [x]。
        text = text.replace(Regex("(?is)<ol[^>]*>(.*?)</ol\\s*>")) { match ->
            val inner = match.groupValues[1]
                .replace(Regex("(?is)<li[^>]*>"), "\n1. ")
                .replace(Regex("(?is)</li\\s*>"), "\n")
            "\n$inner\n"
        }
        text = text.replace(Regex("(?is)<ul[^>]*>(.*?)</ul\\s*>")) { match ->
            val inner = match.groupValues[1]
                .replace(Regex("(?is)<input[^>]*type\\s*=\\s*[\"']?checkbox[\"']?[^>]*>")) { input ->
                    if (input.value.contains("checked", ignoreCase = true)) "[x] " else "[ ] "
                }
                .replace(Regex("(?is)<li[^>]*>"), "\n- ")
                .replace(Regex("(?is)</li\\s*>"), "\n")
            "\n$inner\n"
        }
        for (level in 1..6) {
            text = text.replace(Regex("(?is)<h$level[^>]*>(.*?)</h$level\\s*>")) { match ->
                "\n" + "#".repeat(level) + " " + stripTags(match.groupValues[1]).trim() + "\n"
            }
        }
        text = text.replace(Regex("(?is)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?is)<hr\\s*/?>"), "\n---\n")
        text = text.replace(Regex("(?is)<img[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>")) { match ->
            "\n![图片](${match.groupValues[1]})\n"
        }
        text = text.replace(Regex("(?is)<a[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a\\s*>")) { match ->
            "[${stripTags(match.groupValues[2]).trim()}](${match.groupValues[1]})"
        }
        text = text.replace(Regex("(?is)<(strong|b)[^>]*>"), "**")
            .replace(Regex("(?is)</(strong|b)\\s*>"), "**")
            .replace(Regex("(?is)<(em|i)[^>]*>"), "*")
            .replace(Regex("(?is)</(em|i)\\s*>"), "*")
            .replace(Regex("(?is)<(del|s|strike)[^>]*>"), "~~")
            .replace(Regex("(?is)</(del|s|strike)\\s*>"), "~~")
            .replace(Regex("(?is)<mark[^>]*>"), "==")
            .replace(Regex("(?is)</mark\\s*>"), "==")
        text = text.replace(
            Regex("(?is)</?(p|div|section|article|header|footer|main|blockquote|table|tr|td|th|pre)[^>]*>"),
            "\n",
        )
        text = stripTags(text)
        text = decodeEntities(text)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }

    fun looksLikeHtml(text: String): Boolean {
        val head = text.trimStart().take(200).lowercase()
        return head.startsWith("<!doctype html") ||
            head.startsWith("<html") ||
            head.contains("<body")
    }

    private fun stripTags(text: String): String = text.replace(Regex("(?is)<[^>]+>"), "")

    private fun stripInlineMarkup(text: String): String {
        return text
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "$1")
            .replace(Regex("""==(.+?)=="""), "$1")
            .replace(Regex("""~~(.+?)~~"""), "$1")
    }

    private fun indentOf(whitespace: String): Int {
        var spaces = 0
        whitespace.forEach { char -> spaces += if (char == '\t') 2 else 1 }
        return (spaces / 2).coerceIn(0, 8)
    }

    private fun isRemoteLink(ref: String): Boolean {
        val lower = ref.trim().lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("data:")
    }

    private fun isLocalMediaRef(ref: String): Boolean {
        val clean = ref.trim()
        if (clean.isEmpty() || isRemoteLink(clean)) return false
        val ext = extensionOf(clean)
        return ext.isNotEmpty() || clean.startsWith("media/") || clean.startsWith("./") ||
            clean.startsWith("../") || clean.startsWith("/")
    }

    fun extensionOf(path: String): String {
        val plain = path.substringBefore('?').substringBefore('#')
        val name = plain.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun isImagePath(path: String): Boolean = extensionOf(path) in imageExtensions

    fun isVideoPath(path: String): Boolean = extensionOf(path) in videoExtensions

    private val entityRegex = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]+);""")

    private fun decodeEntities(text: String): String {
        return entityRegex.replace(text) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x", ignoreCase = true) -> {
                    codePoint(entity.substring(2).toIntOrNull(16)) ?: match.value
                }
                entity.startsWith("#") -> {
                    codePoint(entity.substring(1).toIntOrNull()) ?: match.value
                }
                else -> when (entity.lowercase()) {
                    "nbsp" -> " "
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    else -> match.value
                }
            }
        }
    }

    private fun codePoint(value: Int?): String? {
        if (value == null || value <= 0 || value > 0x10FFFF) return null
        return runCatching { String(Character.toChars(value)) }.getOrNull()
    }
}
