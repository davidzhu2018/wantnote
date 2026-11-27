package com.davidzhu.wantnote.data

import androidx.compose.ui.graphics.Color
import com.davidzhu.wantnote.ui.components.MarkdownVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.17.0：验证行内语法包裹光标位置、MarkdownVisual 逐字偏移映射（支持平滑拖拽光标）
 * 及自动检查更新默认配置。
 */
class EditorCaretAndOffsetMappingTest {

    @Test
    fun wrapSelectedTextPlacesCaretAfterClosingMarker() {
        val (boldText, boldLo, boldHi) = Markup.wrap("测试文字", 0, 2, "**")
        assertEquals("**测试**文字", boldText)
        assertEquals(6, boldLo)
        assertEquals(6, boldHi)

        val (italicText, italicLo, italicHi) = Markup.wrap("hello", 0, 5, "*")
        assertEquals("*hello*", italicText)
        assertEquals(7, italicLo)
        assertEquals(7, italicHi)

        val (hlText, hlLo, hlHi) = Markup.wrap("重点", 0, 2, "==")
        assertEquals("==重点==", hlText)
        assertEquals(6, hlLo)
        assertEquals(6, hlHi)
    }

    @Test
    fun markdownVisualOffsetMappingTracksEveryCharInsideFormattedSpans() {
        val source = "前**测试**后"
        val rendered = MarkdownVisual.render(source, highlightColor = Color.Yellow)
        assertEquals("前测试后", rendered.text.text)

        // 原文在 **测|试**（offset=4）应映射到可见位置 2（前测|试后），不再回跳到 1
        assertEquals(2, rendered.mapping.originalToTransformed(4))
        // 原文在 **测试|**（offset=5）应映射到可见位置 3（前测试|后）
        assertEquals(3, rendered.mapping.originalToTransformed(5))
        // 原文在 **测试**|（offset=7）应映射到可见位置 3
        assertEquals(3, rendered.mapping.originalToTransformed(7))

        // 拖拽光标到可见位置 2（前测|试后）应回到原文 offset=4（**测|试**）
        assertEquals(4, rendered.mapping.transformedToOriginal(2))
        // 拖拽光标到可见位置 3（前测试|后）应落在闭合 ** 之后（offset=7），便于继续输入正文
        assertEquals(7, rendered.mapping.transformedToOriginal(3))
    }

    @Test
    fun autoCheckUpdateDefaultsToTrue() {
        assertTrue(AppSettings.DEFAULT.autoCheckUpdate)
    }
}
