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
    val headingLevel: Int = 0,
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
 * - `# 标题`：首个 heading 作为笔记标题；其余 heading 变成 HEADING 块。
 * - `>` 引用：QUOTE。
 * - ``` / ~~~ 围栏代码：CODE（逐行保留，编辑器可直接编辑）。
 * - `- [ ]` / `- [x]`：TASK。
 * - `1.` / `1)`：ORDERED；`-` / `*` / `+`：UNORDERED。
 * - `---` / `***` / `___`：DIVIDER。
 * - `![说明](media/x)`：IMAGE。
 * - `[视频](media/x)`：VIDEO。
 * - `[文件: 名称](media/x)`：FILE。
 * - 缩进按 2 个空格一级（导入时 tab 记作 2 个空格）。
 * - GFM 管道表格（`| a | b |` + `| --- | --- |` 分隔行）：TABLE 块，
 *   原始竖线行整体保存，导出时逐行还原。
 * - 行内 `**粗体**`、`*斜体*`、`==高亮==`、`~~删除线~~` 原样保留在
 *   块文本里，与编辑器 Markup 一致。
 */
object MarkdownCodec {
    private val headingRegex = Regex("""^ {0,3}(#{1,6})[ \t]+(.+?)[ \t]*#*[ \t]*$""")
    private val quoteRegex = Regex("""^ {0,3}>[ \t]?(.*)$""")
    private val fenceRegex = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")
    private val dividerRegex = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
    private val taskRegex = Regex("""^([ \t]*)(?:[-*+]|\d+[.)])[ \t]+\[([ xX])\][ \t]*(.*)$""")
    private val orderedRegex = Regex("""^([ \t]*)\d+[.)][ \t]+(.*)$""")
    private val unorderedRegex = Regex("""^([ \t]*)[-*+][ \t]+(.*)$""")
    private val imageRegex = Regex("""^([ \t]*)!\[([^\]]*)\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")
    private val videoRegex = Regex("""^([ \t]*)\[视频\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")
    private val fileRegex = Regex("""^([ \t]*)\[文件[:：]?[ \t]*([^\]]*)\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")
    private val linkRegex = Regex("""^([ \t]*)\[([^\]]*)\]\(([^)\s]+)(?:[ \t]+"[^"]*")?\)[ \t]*$""")

    /** GFM 表头分隔行：`| --- | :---: |`，也接受无首尾管道的 `--- | ---`。 */
    private val tableSeparatorRegex = Regex("""^[ \t]*\|?[ \t]*:?-+:?[ \t]*(\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$""")

    /** 解析后的表格：表头、每列对齐方式（0 左 / 1 中 / 2 右）与数据行。 */
    data class TableGrid(
        val header: List<String>,
        val alignments: List<Int>,
        val rows: List<List<String>>,
    )

    /** 行内是否含竖线（长度 >1 的竖线才算潜在表格行）。 */
    fun lineHasPipe(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length > 1 && trimmed.contains('|')
    }

    /** 该行是否是 GFM 表头分隔行（`--- | :---: |` 样式，只允许连字符与冒号）。 */
    fun isTableSeparatorLine(line: String): Boolean {
        return tableSeparatorRegex.matches(line.trimEnd())
    }

    /**
     * 把一个 TABLE 块的原始文本解析成 [TableGrid]，供预览渲染。
     * 第一行是表头，第二行是分隔行（决定列对齐），其余是数据行。
     * 单元格内 `\|` 转义为字面竖线。解析失败返回 null（按段落显示）。
     */
    fun parseTable(text: String): TableGrid? {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        if (!isTableSeparatorLine(lines[1])) return null
        val header = splitTableRow(lines[0])
        val separator = splitTableRow(lines[1])
        if (header.isEmpty()) return null
        val alignments = separator.map { cell ->
            val clean = cell.trim().lowercase()
            val leading = clean.startsWith(":")
            val trailing = clean.endsWith(":")
            when {
                leading && trailing -> 1
                trailing -> 2
                else -> 0
            }
        }
        val rows = lines.drop(2).map { splitTableRow(it) }
        return TableGrid(header, alignments, rows)
    }

    /** 按竖线切分一行表格，处理 `\|` 转义，返回去掉首尾管道后的单元格。 */
    private fun splitTableRow(row: String): List<String> {
        var body = row.trim()
        if (body.startsWith("|")) body = body.substring(1)
        if (body.endsWith("|") && !body.endsWith("\\|")) body = body.substring(0, body.length - 1)
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var escaped = false
        body.forEach { char ->
            when {
                escaped -> {
                    if (char != '|') current.append('\\')
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '|' -> {
                    cells += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(char)
            }
        }
        if (escaped) current.append('\\')
        cells += current.toString().trim()
        return cells
    }

    /** 由块列表生成表格的原始 Markdown 行（编辑器插入模板用）。 */
    fun tableTemplate(indent: Int = 0): String {
        val prefix = "  ".repeat(indent.coerceIn(0, 8))
        return listOf(
            "${prefix}| 列一 | 列二 |",
            "${prefix}| --- | --- |",
            "${prefix}| 内容 | 内容 |",
        ).joinToString("\n")
    }

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
        return parseMarkdown(source, fallbackTitle, resolveMedia = resolveMedia)
    }

    fun parseMarkdown(
        markdown: String,
        fallbackTitle: String,
        consumeLeadingTitle: Boolean = true,
        resolveMedia: (String) -> String? = { null },
    ): MarkdownNote {
        val normalized = markdown
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val blocks = ArrayList<MarkdownBlock>()
        val missing = ArrayList<String>()
        var title: String? = null
        var inFence = false
        var fenceIndent = 0

        val lines = normalized.split('\n')
        var index = 0
        while (index < lines.size) {
            val rawLine = lines[index]
            val line = rawLine.trimEnd()
            val fence = fenceRegex.matchEntire(line)
            if (fence != null) {
                if (!inFence) {
                    inFence = true
                    fenceIndent = indentOf(fence.groupValues[1].takeWhile { it.isWhitespace() })
                } else {
                    inFence = false
                }
                index += 1
                continue
            }
            if (inFence) {
                blocks += MarkdownBlock(
                    type = BlockType.CODE,
                    text = rawLine,
                    indent = fenceIndent,
                )
                index += 1
                continue
            }
            if (line.isBlank()) {
                index += 1
                continue
            }
            // GFM 管道表格：当前行含竖线且下一行是分隔行（| --- | --- |），
            // 则把连续的行合并为一个 TABLE 块（原始竖线行原样保留，保证
            // 导出后可以逐字还原）。
            if (lineHasPipe(line) && index + 1 < lines.size &&
                isTableSeparatorLine(lines[index + 1])
            ) {
                val indent = indentOf(line.takeWhile { it.isWhitespace() })
                val rows = ArrayList<String>()
                var cursor = index
                while (cursor < lines.size) {
                    val candidate = lines[cursor].trimEnd()
                    if (candidate.isBlank() || !lineHasPipe(candidate)) break
                    rows += candidate.trimStart()
                    cursor += 1
                }
                blocks += MarkdownBlock(
                    type = BlockType.TABLE,
                    text = rows.joinToString("\n"),
                    indent = indent,
                )
                index = cursor
                continue
            }
            val heading = headingRegex.matchEntire(line)
            if (heading != null) {
                val level = heading.groupValues[1].length.coerceIn(1, 6)
                val rawText = heading.groupValues[2].trim()
                val text = stripInlineMarkup(rawText).trim()
                // 完整文档导入时，首个标题作为笔记标题、不进入正文块。
                // 正文/预览解析必须关闭此行为，否则正文里的第一个标题会消失。
                if (consumeLeadingTitle && title == null && text.isNotEmpty()) {
                    title = text
                    index += 1
                    continue
                }
                if (rawText.isNotEmpty()) {
                    blocks += MarkdownBlock(
                        type = BlockType.HEADING,
                        text = rawText,
                        indent = 0,
                        headingLevel = level,
                    )
                }
                index += 1
                continue
            }
            val quote = quoteRegex.matchEntire(line)
            if (quote != null) {
                val text = quote.groupValues[1].trim()
                if (text.isNotEmpty()) {
                    blocks += MarkdownBlock(
                        type = BlockType.QUOTE,
                        text = text,
                        indent = indentOf(line.takeWhile { it.isWhitespace() }),
                    )
                }
                index += 1
                continue
            }
            if (dividerRegex.matches(line)) {
                blocks += MarkdownBlock(BlockType.DIVIDER)
                index += 1
                continue
            }
            val task = taskRegex.matchEntire(line)
            if (task != null) {
                blocks += MarkdownBlock(
                    type = BlockType.TASK,
                    text = task.groupValues[3].trim(),
                    indent = indentOf(task.groupValues[1]),
                    checked = task.groupValues[2].equals("x", ignoreCase = true),
                )
                index += 1
                continue
            }
            val image = imageRegex.matchEntire(line)
            if (image != null) {
                val ref = image.groupValues[3]
                if (isRemoteLink(ref)) {
                    // 0.10.0：远程图片保留为真正的 IMAGE 块（mediaPath 是
                    // http(s) URL），编辑器与预览直接拉取渲染，不再退化为
                    // 原始 Markdown 段落。导出时 render() 原样还原 `![alt](url)`。
                    blocks += MarkdownBlock(
                        type = BlockType.IMAGE,
                        text = image.groupValues[2].trim(),
                        indent = indentOf(image.groupValues[1]),
                        mediaPath = ref,
                    )
                    index += 1
                    continue
                }
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
                index += 1
                continue
            }
            val video = videoRegex.matchEntire(line)
            if (video != null) {
                val ref = video.groupValues[2]
                if (isRemoteLink(ref)) {
                    blocks += MarkdownBlock(
                        type = BlockType.PARAGRAPH,
                        text = line.trim(),
                        indent = indentOf(video.groupValues[1]),
                    )
                    index += 1
                continue
                }
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
                index += 1
                continue
            }
            val file = fileRegex.matchEntire(line)
            if (file != null) {
                val ref = file.groupValues[3]
                if (isRemoteLink(ref)) {
                    blocks += MarkdownBlock(
                        type = BlockType.PARAGRAPH,
                        text = line.trim(),
                        indent = indentOf(file.groupValues[1]),
                    )
                    index += 1
                continue
                }
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
                index += 1
                continue
            }
            val ordered = orderedRegex.matchEntire(line)
            if (ordered != null) {
                blocks += MarkdownBlock(
                    type = BlockType.ORDERED,
                    text = ordered.groupValues[2].trim(),
                    indent = indentOf(ordered.groupValues[1]),
                )
                index += 1
                continue
            }
            val unordered = unorderedRegex.matchEntire(line)
            if (unordered != null) {
                blocks += MarkdownBlock(
                    type = BlockType.UNORDERED,
                    text = unordered.groupValues[2].trim(),
                    indent = indentOf(unordered.groupValues[1]),
                )
                index += 1
                continue
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
                    index += 1
                continue
                }
            }
            val indent = indentOf(line.takeWhile { it.isWhitespace() })
            blocks += MarkdownBlock(
                type = BlockType.PARAGRAPH,
                text = line.trim(),
                indent = indent,
            )
            index += 1
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
            val prefix = "  ".repeat(block.indent.coerceIn(0, 8))
            val text = block.text.replace('\n', ' ').trim()
            when (block.type) {
                BlockType.CODE -> {
                    val previousCode = index > 0 && blocks[index - 1].type == BlockType.CODE
                    val nextCode = index + 1 < blocks.size && blocks[index + 1].type == BlockType.CODE
                    if (!previousCode) {
                        if (index > 0) out.append('\n')
                        out.append("```\n")
                    } else {
                        out.append('\n')
                    }
                    out.append(block.text.trimEnd('\n'))
                    if (!nextCode) out.append("\n```")
                }
                BlockType.HEADING -> {
                    if (index > 0) out.append('\n')
                    val level = block.headingLevel.coerceIn(1, 6)
                    out.append("#".repeat(level)).append(' ').append(text)
                }
                BlockType.QUOTE -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix).append("> ").append(text)
                }
                BlockType.DIVIDER -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix).append("---")
                }
                BlockType.TASK -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix)
                        .append(if (block.checked) "- [x] " else "- [ ] ")
                        .append(text)
                }
                BlockType.ORDERED -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix).append("1. ").append(text)
                }
                BlockType.UNORDERED -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix).append("- ").append(text)
                }
                BlockType.IMAGE -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix)
                        .append("![")
                        .append(text.ifBlank { "图片" })
                        .append("](")
                        .append(mediaRef(block.mediaPath))
                        .append(")")
                }
                BlockType.TABLE -> {
                    if (index > 0) out.append('\n')
                    // 表格按原始竖线行逐行还原（text 是多行原文）。
                    out.append(prefix).append(block.text.replace("\n", "\n$prefix"))
                }
                BlockType.VIDEO -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix)
                        .append("[视频](")
                        .append(mediaRef(block.mediaPath))
                        .append(")")
                }
                BlockType.FILE -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix)
                        .append("[文件: ")
                        .append(text.ifBlank { "附件" })
                        .append("](")
                        .append(mediaRef(block.mediaPath))
                        .append(")")
                }
                BlockType.PARAGRAPH -> {
                    if (index > 0) out.append('\n')
                    out.append(prefix).append(text)
                }
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
        text = text.replace(Regex("(?is)<pre[^>]*>(.*?)</pre\\s*>")) { match ->
            val code = stripTags(match.groupValues[1]).trim('\n')
            "\n```\n" + code + "\n```\n"
        }
        text = text.replace(Regex("(?is)<blockquote[^>]*>(.*?)</blockquote\\s*>")) { match ->
            val inner = stripTags(match.groupValues[1])
                .replace(Regex("\\r\\n?"), "\n")
                .lineSequence()
                .joinToString("\n") { line -> if (line.isBlank()) ">" else "> " + line.trim() }
            "\n$inner\n"
        }
        text = text.replace(Regex("(?is)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?is)<hr\\s*/?>"), "\n---\n")
        text = text.replace(Regex("(?is)<img[^>]*src\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>")) { match ->
            "\n![图片](${match.groupValues[1]})\n"
        }
        text = text.replace(Regex("(?is)<a[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a\\s*>")) { match ->
            "[${stripTags(match.groupValues[2]).trim()}](${match.groupValues[1]})"
        }
        // Word boundaries so <body>/<br> are not treated as <b>, and
        // <img>/<input>/<iframe> are not treated as <i>.
        text = text.replace(Regex("(?is)<(strong|b)\\b[^>]*>"), "**")
            .replace(Regex("(?is)</(strong|b)\\s*>"), "**")
            .replace(Regex("(?is)<(em|i)\\b[^>]*>"), "*")
            .replace(Regex("(?is)</(em|i)\\s*>"), "*")
            .replace(Regex("(?is)<(del|s|strike)\\b[^>]*>"), "~~")
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
