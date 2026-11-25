package com.davidzhu.wantnote.data

import androidx.compose.ui.graphics.Color
import com.davidzhu.wantnote.ui.components.MarkdownVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.12.0：主页卡片正文预览去图单元测试。
 * 确保含图笔记在主页展示大封面时，正文预览不再把同一张图渲染成行内小图，
 * 也不泄漏 `![]()` 或 alt 源码残段，同时阅读态仍可正常渲染行内图片。
 */
class HomePreviewImageStripTest {

    private fun note(title: String, body: String): Note = Note(
        id = "n-1",
        title = title,
        body = body,
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    @Test
    fun previewMarkdownStripsCoverImageAndAltLeftoversWhileKeepingBodyText() {
        val running = note(
            title = "2026年4月15日室内跑步运动记录",
            body = """
                ![图表 详情](https://oss.example.com/run-20260415.jpg)
                2026年4月15日 22:30
                配速 **8'03"**，心率正常
            """.trimIndent(),
        )
        val preview = SearchText.previewMarkdown(running)
        assertEquals("2026年4月15日 22:30 配速 **8'03\"**，心率正常", preview)
        assertFalse(preview.contains("!["))
        assertFalse(preview.contains("图表 详情"))
        assertFalse(preview.contains("run-20260415"))
    }

    @Test
    fun previewMarkdownReturnsEmptyForImageOnlyNote() {
        val onlyImages = note(
            title = "今日拍照",
            body = """
                ![图片](media/photo_1.jpg)
                ![](https://example.com/photo_2.png "风景")
                ![]()
                <img src="https://example.com/photo_3.webp" alt="图表 详情" />
            """.trimIndent(),
        )
        assertEquals("", SearchText.previewMarkdown(onlyImages))
    }

    @Test
    fun previewMarkdownStripsInlineLinkedReferenceAndParenthesizedImagesButPreservesFormatting() {
        val mixed = note(
            title = "复盘",
            body = """
                ![](https://example.com/a(1).png) 你好，**重点** 见 [说明](https://example.com/doc) 与 [[note:n2]]
                [![截图](https://example.com/thumb.jpg)](https://example.com/full) ![引用图][img1] [图片] 补充内容
            """.trimIndent(),
        )
        val preview = SearchText.previewMarkdown(mixed)
        assertEquals(
            "你好，**重点** 见 [说明](https://example.com/doc) 与 [[note:n2]] 补充内容",
            preview,
        )
        assertFalse(preview.contains("!["))
        assertFalse(preview.contains("thumb.jpg"))
        assertFalse(preview.contains("img1"))
        assertFalse(preview.contains("[图片]"))
    }

    @Test
    fun previewMarkdownHandlesUntitledNoteStartingWithImage() {
        val untitledEmptyAlt = note(
            title = "",
            body = """
                ![](media/cover.jpg)
                第一行标题
                第二行正文
            """.trimIndent(),
        )
        assertEquals("第一行标题", SearchText.displayTitle(untitledEmptyAlt))
        assertEquals("第二行正文", SearchText.previewMarkdown(untitledEmptyAlt))

        val untitledWithAlt = note(
            title = "",
            body = """
                ![图片](media/cover.jpg)
                正文第一段
                正文第二段
            """.trimIndent(),
        )
        val preview = SearchText.previewMarkdown(untitledWithAlt)
        assertEquals("正文第一段 正文第二段", preview)
        assertFalse(preview.contains("!["))
    }

    @Test
    fun renderRichWithInlineImagesDisabledEmitsNoInlineImagesOrRawMarkers() {
        val source = "前文 ![图表 详情](https://example.com/run.jpg) ![]() **加粗** [链接](https://example.com)"
        val homeRendered = MarkdownVisual.renderRich(
            source = source,
            highlightColor = Color.Yellow,
            linkColor = Color.Blue,
            renderInlineImages = false,
        )
        assertTrue(homeRendered.inlineImages.isEmpty())
        assertFalse(homeRendered.text.text.contains("![]()"))
        assertFalse(homeRendered.text.text.contains("图表 详情"))
        assertFalse(homeRendered.text.text.contains("[图片]"))
        assertTrue(homeRendered.text.text.contains("加粗"))
        assertTrue(homeRendered.text.text.contains("链接"))

        // 阅读态（默认 renderInlineImages = true）仍正常输出行内图片占位与 URL 映射。
        val readingRendered = MarkdownVisual.renderRich(
            source = "说明 ![截图](https://example.com/run.jpg)",
            highlightColor = Color.Yellow,
            linkColor = Color.Blue,
            renderInlineImages = true,
        )
        assertEquals(mapOf("img0" to "https://example.com/run.jpg"), readingRendered.inlineImages)
    }
}
