package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownCodecTest {
    @Test
    fun renderAndParseRoundTripKeepsBlockTypes() {
        val blocks = listOf(
            MarkdownBlock(
                BlockType.PARAGRAPH,
                "普通 **粗体** *斜体* ==高亮== ~~删除线~~",
            ),
            MarkdownBlock(BlockType.TASK, "待办一", indent = 0, checked = false),
            MarkdownBlock(BlockType.TASK, "已完成的子任务", indent = 1, checked = true),
            MarkdownBlock(BlockType.ORDERED, "第一步"),
            MarkdownBlock(BlockType.UNORDERED, "项目", indent = 2),
            MarkdownBlock(BlockType.HEADING, "二级标题", headingLevel = 2),
            MarkdownBlock(BlockType.QUOTE, "引用的一句话", indent = 1),
            MarkdownBlock(BlockType.CODE, "val x = 1"),
            MarkdownBlock(BlockType.CODE, "val y = 2"),
            MarkdownBlock(BlockType.DIVIDER),
        )

        val markdown = MarkdownCodec.render("读书笔记", blocks)
        val parsed = MarkdownCodec.parseMarkdown(markdown, "文件名")

        assertEquals("读书笔记", parsed.title)
        assertEquals(blocks, parsed.blocks)
        assertTrue(parsed.missingMedia.isEmpty())
    }

    @Test
    fun fenceContentIsNeverInterpreted() {
        val parsed = MarkdownCodec.parseMarkdown(
            """
            # 代码笔记

            ```
            # 不是标题，是代码注释
            - 不是列表
            ```

            正文
            """.trimIndent(),
            "代码笔记",
        )
        assertEquals("代码笔记", parsed.title)
        assertEquals(
            listOf(
                MarkdownBlock(BlockType.CODE, "# 不是标题，是代码注释"),
                MarkdownBlock(BlockType.CODE, "- 不是列表"),
                MarkdownBlock(BlockType.PARAGRAPH, "正文"),
            ),
            parsed.blocks,
        )
    }

    @Test
    fun headingsBeyondFirstBecomeHeadingBlocksWithLevel() {
        val parsed = MarkdownCodec.parseMarkdown(
            "# 首个标题\n\n## 二级\n### 三级\n#### 四级",
            "回退标题",
        )
        assertEquals("首个标题", parsed.title)
        assertEquals(
            listOf(
                MarkdownBlock(BlockType.HEADING, "二级", headingLevel = 2),
                MarkdownBlock(BlockType.HEADING, "三级", headingLevel = 3),
                MarkdownBlock(BlockType.HEADING, "四级", headingLevel = 4),
            ),
            parsed.blocks,
        )
    }

    @Test
    fun remoteImagesStayRenderedAsMarkdown() {
        val parsed = MarkdownCodec.parseMarkdown(
            "# 远程图\n\n![截图](https://example.com/a.png)",
            "远程图",
            resolveMedia = { null },
        )
        assertEquals(
            listOf(
                MarkdownBlock(BlockType.PARAGRAPH, "![截图](https://example.com/a.png)"),
            ),
            parsed.blocks,
        )
    }

    @Test
    fun mediaBlocksRoundTripWithResolver() {
        val blocks = listOf(
            MarkdownBlock(BlockType.IMAGE, "封面", mediaPath = "media/a.jpg"),
            MarkdownBlock(BlockType.VIDEO, mediaPath = "media/b.mp4"),
            MarkdownBlock(BlockType.FILE, "附件说明", mediaPath = "media/c.pdf"),
        )
        val markdown = MarkdownCodec.render("附件", blocks) { "../$it" }
        val parsed = MarkdownCodec.parseMarkdown(markdown, "附件") { ref ->
            when (ref) {
                "../media/a.jpg" -> "media/a.jpg"
                "../media/b.mp4" -> "media/b.mp4"
                "../media/c.pdf" -> "media/c.pdf"
                else -> null
            }
        }
        assertEquals("附件", parsed.title)
        assertEquals(blocks, parsed.blocks)
    }

    @Test
    fun missingMediaIsReportedNotCrashed() {
        val parsed = MarkdownCodec.parseMarkdown(
            "# 带图\n\n![图](images/missing.png)\n\n正文",
            "fallback",
        ) { null }
        assertEquals("带图", parsed.title)
        assertEquals(listOf("images/missing.png"), parsed.missingMedia)
        assertEquals(1, parsed.blocks.size)
        assertEquals("正文", parsed.blocks.first().text)
    }

    @Test
    fun htmlIsConvertedToBlocks() {
        val html = """
            <html><body>
            <h1>网页标题</h1>
            <p><strong>粗体</strong>与<em>斜体</em></p>
            <ul><li>无序</li><li><input type="checkbox" checked> 已完成</li></ul>
            <ol><li>有序</li></ol>
            <hr>
            </body></html>
        """.trimIndent()
        val parsed = MarkdownCodec.parseDocument(html, "网页", isHtml = true)

        assertEquals("网页标题", parsed.title)
        assertEquals(BlockType.PARAGRAPH, parsed.blocks[0].type)
        assertEquals("**粗体**与*斜体*", parsed.blocks[0].text)
        assertEquals(BlockType.UNORDERED, parsed.blocks[1].type)
        assertEquals(BlockType.TASK, parsed.blocks[2].type)
        assertTrue(parsed.blocks[2].checked)
        assertEquals(BlockType.ORDERED, parsed.blocks[3].type)
        assertEquals(BlockType.DIVIDER, parsed.blocks[4].type)
    }

    @Test
    fun plainMarkdownWithoutHeadingUsesFallbackTitle() {
        val parsed = MarkdownCodec.parseMarkdown("只有正文\n\n- [x] 完成", "我的文件")
        assertEquals("我的文件", parsed.title)
        assertEquals("只有正文", parsed.blocks[0].text)
        assertEquals(BlockType.TASK, parsed.blocks[1].type)
        assertTrue(parsed.blocks[1].checked)
    }

    @Test
    fun indentationUsesTwoSpacesPerLevel() {
        val parsed = MarkdownCodec.parseMarkdown("  - 一级\n    - 二级", "t")
        assertEquals(1, parsed.blocks[0].indent)
        assertEquals(2, parsed.blocks[1].indent)
    }
}
