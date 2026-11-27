package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.16.5 新增 Markdown 渲染能力测试：
 * - HTML <img> 标签（含 data-* 扩展属性）解析为 IMAGE 块，且主页预览剥离原始 HTML；
 * - Mermaid 流程图解析（方向、节点形状、连线与分层）；
 * - LaTeX 块级与行内公式格式化。
 */
class MarkdownRichRenderTest {

    @Test
    fun htmlImgTagParsesAsImageBlockAndStripsInHomePreview() {
        val htmlImg = """<img src="https://dinox3422.s3.bitiful.net/dinox3422/assets/01903370-cd99-7c96-9c5f-346ad433cd0c/images/link-clip/wecom-link-3d911a1f1fb892f25c798427/4c9310a3722af68b0e58435b3c969bfd22ec489a88444193a6b2aa9e4264472e.png" alt="图片" data-type="image" data-resource-id="01a20d9a-9ef7-7abf-8c3c-0f2561c4df72" data-storage-key="dinox3422/assets/test.png">"""
        val md = "第一段文字\n\n$htmlImg\n\n第二段文字"
        val note = MarkdownCodec.parseMarkdown(md, fallbackTitle = "测试", consumeLeadingTitle = false)
        val imgBlock = note.blocks.firstOrNull { it.type == BlockType.IMAGE }
        assertNotNull("HTML <img> 应解析为 IMAGE 块", imgBlock)
        assertTrue(imgBlock!!.mediaPath.startsWith("https://dinox3422.s3.bitiful.net/"))
        assertEquals("图片", imgBlock.text)

        val preview = SearchText.previewMarkdown(
            Note(id = "n1", title = "测试", body = md, createdAt = 1L, updatedAt = 1L),
        )
        assertTrue("主页预览不应露出 <img 源码", !preview.contains("<img"))
        assertTrue(preview.contains("第一段文字"))
    }

    @Test
    fun mermaidParserExtractsFlowchartNodesEdgesAndLayers() {
        val code = """
            mermaid
            graph TD
                A[开始] --> B{是否已登录}
                B -->|是| C(进入主页)
                B -->|否| D((跳转登录))
        """.trimIndent()
        assertTrue(MermaidParser.isMermaidCodeBlock(code))
        val diagram = MermaidParser.parse(code)
        assertNotNull(diagram)
        assertEquals(MermaidDiagram.Direction.TOP_DOWN, diagram!!.direction)
        assertEquals(4, diagram.nodes.size)
        assertEquals(3, diagram.edges.size)
        assertEquals(MermaidNode.Shape.DIAMOND, diagram.nodes.first { it.id == "B" }.shape)
        assertEquals("是", diagram.edges.first { it.to == "C" }.label)
        assertTrue(diagram.layers.size >= 3)
    }

    @Test
    fun latexMathFormatsFractionsRootsGreeksAndScripts() {
        val block = LatexMath.extractBlockFormula("\$\$E = mc^2 + \\frac{a}{b}\$\$")
        assertEquals("E = mc^2 + \\frac{a}{b}", block)

        val formatted = LatexMath.format("""E = mc^2 + \frac{\alpha}{\sqrt{x_1}} \le \infty""")
        assertTrue(formatted.contains("mc²"))
        assertTrue(formatted.contains("α"))
        assertTrue(formatted.contains("√(x₁)"))
        assertTrue(formatted.contains("≤"))
        assertTrue(formatted.contains("∞"))
    }
}
