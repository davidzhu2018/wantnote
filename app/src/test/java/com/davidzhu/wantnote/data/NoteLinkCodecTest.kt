package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLinkCodecTest {
    private fun block(
        text: String,
        type: BlockType = BlockType.PARAGRAPH,
    ) = NoteBlock(
        id = "b-${text.hashCode()}",
        noteId = "n1",
        type = type.name,
        text = text,
        indent = 0,
        checked = false,
        fontSize = BlockFontSize.NORMAL.name,
        mediaPath = "",
        sortOrder = 0,
    )

    @Test
    fun markerUsesStableIdNotTitle() {
        assertEquals("[[note:abc-123]]", NoteLinkCodec.marker("abc-123"))
    }

    @Test
    fun targetIdsExtractsInOrderAndDeduplicates() {
        val text = "见 [[note:a]] 和 [[note:b]]，再回顾 [[note:a]]。"
        assertEquals(listOf("a", "b"), NoteLinkCodec.targetIds(text))
    }

    @Test
    fun targetIdsAcrossBlocksSkipsMediaAndDividers() {
        val blocks = listOf(
            block("有 [[note:a]]"),
            block("", type = BlockType.DIVIDER).copy(text = "ignored [[note:x]]"),
            block("", type = BlockType.IMAGE).copy(mediaPath = "media/p.jpg", text = ""),
            block("还有 [[note:b]] 与 [[note:a]]", type = BlockType.TASK),
        )
        assertEquals(listOf("a", "b"), NoteLinkCodec.targetIds(blocks))
    }

    @Test
    fun renderPlainResolvesTitlesAndFallsBackForDeleted() {
        val text = "链接 [[note:a]] 与 [[note:gone]]。"
        val rendered = NoteLinkCodec.renderPlain(text, resolveTitle = { id -> if (id == "a") "甲" else null })
        assertEquals("链接 甲 与 已删除的笔记。", rendered)
    }

    @Test
    fun renderPlainTreatsBlankTitleAsMissing() {
        val rendered = NoteLinkCodec.renderPlain("[[note:a]]", { "" }, missingLabel = "缺失")
        assertEquals("缺失", rendered)
    }

    @Test
    fun containsLinkDetectsMarkers() {
        assertTrue(NoteLinkCodec.containsLink("前缀 [[note:a1]] 后缀"))
        assertFalse(NoteLinkCodec.containsLink("普通文本，没有链接"))
        assertFalse(NoteLinkCodec.containsLink("[[note:]]"))
    }

    @Test
    fun activeMentionStartsAtAtAndCarriesQuery() {
        val mention = NoteMentionParser.activeMention("今天 @设计", caret = 6)
        assertEquals(3, mention?.start)
        assertEquals(6, mention?.end)
        assertEquals("设计", mention?.query)
    }

    @Test
    fun activeMentionAtTextStartIsValid() {
        val mention = NoteMentionParser.activeMention("@ab", caret = 3)
        assertEquals(0, mention?.start)
        assertEquals("ab", mention?.query)
    }

    @Test
    fun activeMentionRejectedInsideEmailAddress() {
        assertNull(NoteMentionParser.activeMention("mail@example.com", caret = 17))
    }

    @Test
    fun activeMentionRejectedWhenQueryHasWhitespace() {
        assertNull(NoteMentionParser.activeMention("@ab cd", caret = 6))
    }

    @Test
    fun activeMentionRejectedWhenCaretBeforeAt() {
        assertNull(NoteMentionParser.activeMention("@ab", caret = 0))
    }

    @Test
    fun activeMentionAcceptedAtLineStart() {
        val mention = NoteMentionParser.activeMention("第一行\n@xy", caret = 9)
        assertEquals("xy", mention?.query)
    }
}
