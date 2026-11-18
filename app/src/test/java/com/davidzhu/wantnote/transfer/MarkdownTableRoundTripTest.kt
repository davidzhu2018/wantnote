package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.9.0 Markdown 编解码往返：表格（新增）与全文 WYSIWYG ⇄ 源码 的
 * 稳定性。约定：render → parse → render 必须逐字一致，否则「所见即所得
 * 模式编辑后再存」会悄悄改写用户内容。
 */
class MarkdownTableRoundTripTest {

    @Test
    fun tableSeparatorRecognizesStandardAndLooseForms() {
        assertTrue(MarkdownCodec.isTableSeparatorLine("| --- | --- |"))
        assertTrue(MarkdownCodec.isTableSeparatorLine("| :--- | ---: |"))
        assertTrue(MarkdownCodec.isTableSeparatorLine("| :---: | --- |"))
        assertTrue(MarkdownCodec.isTableSeparatorLine("--- | ---"))
        assertFalse(MarkdownCodec.isTableSeparatorLine("| -x- | --- |"))
        assertFalse(MarkdownCodec.isTableSeparatorLine("| 文字 | --- |"))
    }

    @Test
    fun pipeLineDetection() {
        assertTrue(MarkdownCodec.lineHasPipe("| a | b |"))
        assertTrue(MarkdownCodec.lineHasPipe("a | b"))
        assertFalse(MarkdownCodec.lineHasPipe("|"))
        assertFalse(MarkdownCodec.lineHasPipe("普通段落没有竖线"))
    }

    @Test
    fun parseTableExtractsHeaderAlignmentsAndRows() {
        val grid = MarkdownCodec.parseTable(
            "| 名称 | 数量 | 备注 |\n| :--- | ---: | :---: |\n| 苹果 | 3 | 红 |\n| 鸭梨 | 5 | 甜 |",
        )
        assertNotNull(grid)
        grid!!
        assertEquals(listOf("名称", "数量", "备注"), grid.header)
        assertEquals(listOf(0, 2, 1), grid.alignments)
        assertEquals(2, grid.rows.size)
        assertEquals(listOf("苹果", "3", "红"), grid.rows[0])
    }

    @Test
    fun parseTableRejectsMissingSeparatorAndEscapedPipe() {
        assertNull(MarkdownCodec.parseTable("| a | b |\n| c | d |"))
        val grid = MarkdownCodec.parseTable("| a\\|b | c |\n| --- | --- |")
        assertNotNull(grid)
        // \| 转义还原成字面竖线，不拆列。
        assertEquals(listOf("a|b", "c"), grid!!.header)
    }

    @Test
    fun tableBlockRoundTripsVerbatim() {
        val table = "| 列一 | 列二 |\n| --- | --- |\n| 内容 | 内容 |"
        val parsed = MarkdownCodec.parseMarkdown(
            "正文\n\n$table\n\n结尾",
            fallbackTitle = "",
        )
        val tableBlock = parsed.blocks.first { it.type == BlockType.TABLE }
        assertEquals(table, tableBlock.text)
        val rendered = MarkdownCodec.render(title = "", blocks = parsed.blocks)
        val reparsed = MarkdownCodec.parseMarkdown(rendered, fallbackTitle = "")
        assertEquals(table, reparsed.blocks.first { it.type == BlockType.TABLE }.text)
    }

    @Test
    fun indentedTableKeepsIndentThroughRoundTrip() {
        val markdown = "| a | b |\n| --- | --- |\n| 1 | 2 |"
        val parsed = MarkdownCodec.parseMarkdown("  $markdown", fallbackTitle = "")
        val tableBlock = parsed.blocks.single { it.type == BlockType.TABLE }
        assertEquals(1, tableBlock.indent)
        assertEquals(markdown, tableBlock.text)
    }

    @Test
    fun documentRoundTripIsVerbatim() {
        val document = listOf(
            "# 旅行清单",
            "",
            "## 装备",
            "",
            "- 背包 **防水**",
            "  - 内胆 *小*",
            "1. 证件",
            "- [x] 订机票",
            "- [ ] 订酒店",
            "",
            "> 引用一句 ==高亮== ~~删除线~~",
            "",
            "```kotlin",
            "fun main() {",
            "    println(\"代码里的 | 竖线不进表格\")",
            "}",
            "```",
            "",
            "| 物品 | 数量 |",
            "| :--- | ---: |",
            "| 背包 | 1 |",
            "",
            "---",
            "",
            "![图片](media/abc.jpg)",
            "",
            "[视频](media/demo.mp4)",
            "",
            "[文件: 合同](media/contract.pdf)",
            "",
            "末尾段落 [[note:abc-123]]",
            "",
        ).joinToString("\n")
        val first = MarkdownCodec.parseMarkdown(document, fallbackTitle = "旅行清单", resolveMedia = { it })
        assertEquals("旅行清单", first.title)
        val contentOne = MarkdownCodec.render(title = first.title, blocks = first.blocks)
        val second = MarkdownCodec.parseMarkdown(contentOne, fallbackTitle = "", resolveMedia = { it })
        val contentTwo = MarkdownCodec.render(title = second.title, blocks = second.blocks)
        // 第二轮开始必须完全稳定（WYSIWYG 每次保存都走 render）。
        assertEquals(contentOne, contentTwo)
        val third = MarkdownCodec.parseMarkdown(contentTwo, fallbackTitle = "", resolveMedia = { it })
        assertEquals(second.blocks, third.blocks)
        // 媒体引用必须保留。
        assertTrue(first.blocks.any { it.type == BlockType.IMAGE && it.mediaPath == "media/abc.jpg" })
        assertTrue(first.blocks.any { it.type == BlockType.VIDEO && it.mediaPath == "media/demo.mp4" })
        assertTrue(first.blocks.any { it.type == BlockType.FILE && it.mediaPath == "media/contract.pdf" })
    }

    @Test
    fun paragraphContainingPipeWithoutSeparatorStaysParagraph() {
        val parsed = MarkdownCodec.parseMarkdown("重量 | 价值\n\n下一段", fallbackTitle = "")
        assertEquals(listOf("重量 | 价值", "下一段"), parsed.blocks.map { it.text })
    }
}
