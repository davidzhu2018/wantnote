package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.BlockFontSize
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteLinkCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.9.0 强制迁移的映射测试：0.8.0 的块数据 → Markdown（正文 + 完整文档），
 * 标题、任务、双链、媒体引用、标签外数据全部原样保留，笔记 ID 不参与
 * 迁移（保持不变），任何行都不允许被丢弃。
 */
class MarkdownMigrationTest {

    private fun note(
        title: String = "",
        body: String = "",
    ) = Note(
        id = "note-1",
        title = title,
        body = body,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L,
    )

    private fun block(
        type: BlockType,
        text: String,
        indent: Int = 0,
        checked: Boolean = false,
        mediaPath: String = "",
        headingLevel: Int = 0,
        sortOrder: Int,
    ) = NoteBlock(
        id = "b-$sortOrder",
        noteId = "note-1",
        type = type.name,
        text = text,
        indent = indent,
        checked = checked,
        fontSize = BlockFontSize.NORMAL.name,
        mediaPath = mediaPath,
        sortOrder = sortOrder,
        headingLevel = headingLevel,
    )

    @Test
    fun plainParagraphsSurviveVerbatim() {
        val note = note(title = "随想", body = "第一行\n第二行")
        val blocks = listOf(
            block(BlockType.PARAGRAPH, "第一行", sortOrder = 0),
            block(BlockType.PARAGRAPH, "第二行", sortOrder = 1),
        )
        val migrated = MarkdownMigrator.migrate(note, blocks)
        assertEquals("第一行\n第二行", migrated.content)
        assertEquals("# 随想\n\n第一行\n第二行\n", migrated.document)
    }

    @Test
    fun tasksQuotesHeadingsAndListsMapToMarkdown() {
        val note = note(title = "清单")
        val blocks = listOf(
            block(BlockType.HEADING, "目标", headingLevel = 2, sortOrder = 0),
            block(BlockType.TASK, "买牛奶", checked = true, sortOrder = 1),
            block(BlockType.TASK, "跑步", checked = false, sortOrder = 2),
            block(BlockType.ORDERED, "第一步", sortOrder = 3),
            block(BlockType.UNORDERED, "备注", indent = 1, sortOrder = 4),
            block(BlockType.QUOTE, "保持简单", sortOrder = 5),
            block(BlockType.DIVIDER, "", sortOrder = 6),
            block(BlockType.CODE, "println(1)", sortOrder = 7),
        )
        val migrated = MarkdownMigrator.migrate(note, blocks)
        val document = migrated.document
        assertTrue(document.contains("## 目标"))
        assertTrue(document.contains("- [x] 买牛奶"))
        assertTrue(document.contains("- [ ] 跑步"))
        assertTrue(document.contains("1. 第一步"))
        assertTrue(document.contains("  - 备注"))
        assertTrue(document.contains("> 保持简单"))
        assertTrue(document.contains("---"))
        assertTrue(document.contains("```\nprintln(1)\n```"))
        // 重新解析回来的块与迁移产物一致（迁移幂等）。
        val reparsed = MarkdownCodec.parseMarkdown(migrated.content, fallbackTitle = "", resolveMedia = { it }, consumeLeadingTitle = false)
        assertEquals(migrated.markdownBlocks, reparsed.blocks)
    }

    @Test
    fun mediaReferencesAndLinksArePreserved() {
        val note = note(title = "资料")
        val blocks = listOf(
            block(BlockType.IMAGE, "截图", mediaPath = "media/img-1.jpg", sortOrder = 0),
            block(BlockType.VIDEO, "", mediaPath = "media/vid-1.mp4", sortOrder = 1),
            block(BlockType.FILE, "合同", mediaPath = "media/file-1.pdf", sortOrder = 2),
            block(BlockType.PARAGRAPH, "相关：[[note:abc-123]]", sortOrder = 3),
        )
        val migrated = MarkdownMigrator.migrate(note, blocks)
        assertTrue(migrated.document.contains("![截图](media/img-1.jpg)"))
        assertTrue(migrated.document.contains("[视频](media/vid-1.mp4)"))
        assertTrue(migrated.document.contains("[文件: 合同](media/file-1.pdf)"))
        assertTrue(migrated.document.contains("[[note:abc-123]]"))
        // 双链在迁移产物里仍然可解析。
        val targets = NoteLinkCodec.targetIds(migrated.content)
        assertEquals(listOf("abc-123"), targets)
    }

    @Test
    fun tablesIntroduceTableBlocksAfterMigration() {
        val note = note(title = "表格笔记")
        val rawTable = "| a | b |\n| --- | --- |\n| 1 | 2 |"
        val blocks = rawTable.split("\n").mapIndexed { index, line ->
            block(BlockType.PARAGRAPH, line, sortOrder = index)
        }
        val migrated = MarkdownMigrator.migrate(note, blocks)
        val reparsed = MarkdownCodec.parseMarkdown(migrated.content, fallbackTitle = "", resolveMedia = { it }, consumeLeadingTitle = false)
        val table = reparsed.blocks.first { it.type == BlockType.TABLE }
        assertEquals(rawTable, table.text)
    }

    @Test
    fun legacyBodyWithoutBlocksBecomesParagraphs() {
        val note = note(body = "旧版正文一行\n另一行")
        val migrated = MarkdownMigrator.migrate(note, emptyList())
        assertEquals("旧版正文一行\n另一行", migrated.content)
        assertEquals(2, MarkdownCodec.parseMarkdown(migrated.content, "", consumeLeadingTitle = false).blocks.size)
    }

    @Test
    fun migrationNeverMarksNoteMigratedItself() {
        // 迁移只产出 Markdown；mdPath 的写入与幂等判断在 NoteRepository。
        val note = note(title = "t")
        val migrated = MarkdownMigrator.migrate(note, emptyList())
        assertFalse(migrated.content.contains("mdPath"))
        assertEquals("# t\n", MarkdownMigrator.documentOf("t", ""))
        assertEquals("# t\n\n内容\n", MarkdownMigrator.documentOf("t", "内容"))
        assertEquals("内容\n", MarkdownMigrator.documentOf("", "内容"))
    }
}
