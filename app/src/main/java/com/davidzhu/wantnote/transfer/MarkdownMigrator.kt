package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock

/**
 * 0.9.0 强制迁移的核心映射（纯 JVM，可单测）：
 *
 * - 0.8.0 的块列表 → Markdown 块模型（标题/任务/列表/引用/代码/媒体/表格）。
 * - 内容 Markdown = 正文（不含标题 H1），存入 `Note.body` 与编辑器。
 * - 完整文档 = `# 标题` + 内容，写入 `notes/<id>.md`。
 *
 * 迁移绝不丢弃内容：无法识别的行原样保留为段落文本。
 */
object MarkdownMigrator {

    /** 一篇笔记迁移后的 Markdown 产物。 */
    data class Migrated(
        /** 正文 Markdown（不含标题 H1），写入 Note.body。 */
        val content: String,
        /** 完整文档（含标题 H1），写入 notes/<id>.md。 */
        val document: String,
        /** 从内容 Markdown 解析回的块列表（迁移后的派生缓存）。 */
        val markdownBlocks: List<MarkdownBlock>,
    )

    /** 0.8.0 存储块 → Markdown 块。 */
    fun toMarkdownBlocks(blocks: List<NoteBlock>): List<MarkdownBlock> {
        return blocks.sortedBy { it.sortOrder }.map { block ->
            MarkdownBlock(
                type = block.blockType,
                text = block.text,
                indent = block.indent,
                checked = block.checked,
                mediaPath = block.mediaPath,
                headingLevel = block.headingLevel,
            )
        }
    }

    /**
     * 迁移一篇笔记。优先使用块列表（0.2.0 起每篇笔记都有块）；没有块时
     * 按 0.6.0 之前的规则把 body 逐行转成段落，保证旧内容不丢。
     */
    fun migrate(note: Note, blocks: List<NoteBlock>): Migrated {
        val markdownBlocks = if (blocks.isEmpty()) {
            bodyToParagraphs(note.body)
        } else {
            toMarkdownBlocks(blocks)
        }
        val content = MarkdownCodec.render(title = "", blocks = markdownBlocks)
            .trimEnd('\n')
        val document = documentOf(note.title, content)
        // 从内容重新解析，得到迁移后的块缓存（表格合并、代码围栏归组）。
        val parsed = MarkdownCodec.parseMarkdown(content, fallbackTitle = "", resolveMedia = { it }, consumeLeadingTitle = false)
        return Migrated(
            content = content,
            document = document,
            markdownBlocks = parsed.blocks.ifEmpty { markdownBlocks },
        )
    }

    /** 完整文档：标题 H1（可空）+ 正文内容。 */
    fun documentOf(title: String, content: String): String {
        val cleanTitle = title.replace('\n', ' ').trim()
        val body = content.trimEnd('\n')
        return when {
            cleanTitle.isEmpty() && body.isEmpty() -> ""
            cleanTitle.isEmpty() -> "$body\n"
            body.isEmpty() -> "# $cleanTitle\n"
            else -> "# $cleanTitle\n\n$body\n"
        }
    }

    /** 无块时的降级：body 按行拆成段落（与 0.6.0 ensureMigratedBlocks 一致）。 */
    fun bodyToParagraphs(body: String): List<MarkdownBlock> {
        val lines = if (body.isEmpty()) {
            listOf("")
        } else {
            body.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        }
        return lines.map { line ->
            MarkdownBlock(type = BlockType.PARAGRAPH, text = line)
        }
    }
}
