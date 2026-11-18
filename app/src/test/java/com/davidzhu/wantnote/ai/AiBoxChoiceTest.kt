package com.davidzhu.wantnote.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 0.10.0 卡片盒自动分配的结果解析：只接受候选名单里逐字一致的名字。 */
class AiBoxChoiceTest {
    private val boxes = listOf("暂存箱", "工作", "读书笔记")

    @Test
    fun acceptsExactNameFromList() {
        assertEquals("工作", AiService.parseBoxChoice("""{"box":"工作"}""", boxes))
    }

    @Test
    fun trimsWhitespaceBeforeMatch() {
        assertEquals("读书笔记", AiService.parseBoxChoice("""{"box":" 读书笔记 "}""", boxes))
    }

    @Test
    fun rejectsInventedBoxNames() {
        assertNull(AiService.parseBoxChoice("""{"box":"新盒子"}""", boxes))
    }

    @Test
    fun rejectsEmptyChoice() {
        assertNull(AiService.parseBoxChoice("""{"box":""}""", boxes))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(AiService.parseBoxChoice("好的，我认为应该放在「工作」", boxes))
    }

    @Test
    fun acceptsJsonWrappedInCodeFence() {
        assertEquals("暂存箱", AiService.parseBoxChoice("```json\n{\"box\":\"暂存箱\"}\n```", boxes))
    }
}
