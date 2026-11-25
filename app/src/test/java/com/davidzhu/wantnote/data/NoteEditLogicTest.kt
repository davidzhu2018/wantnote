package com.davidzhu.wantnote.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.13.0 详情页阅读态的「改卡片盒 / 加删标签」纯逻辑测试。
 *
 * 覆盖两条锁定行为：
 * 1. 卡片盒弹窗按关键字过滤；关键字没有匹配时要给出「新建卡片盒」，
 *    新建成功后把新盒赋给当前笔记（重名 / 空名不落库、不赋值）；
 * 2. 从笔记上移除标签只走 `removeTagFromNote`（绝不全局删标签），
 *    多选时逐个摘掉、去重、忽略空白 id。
 */
class NoteEditLogicTest {

    private fun box(id: String, name: String) = NoteBox(
        id = id,
        name = name,
        createdAt = 0L,
    )

    private val boxes = listOf(
        box("b1", "工作"),
        box("b2", "Reading Notes"),
        box("b3", "工作/项目"),
    )

    // ---- 卡片盒关键字过滤 ----

    @Test
    fun blankQueryKeepsAllBoxesInOrder() {
        assertEquals(boxes, BoxPickerQuery.filter(boxes, ""))
        assertEquals(boxes, BoxPickerQuery.filter(boxes, "   "))
    }

    @Test
    fun queryMatchesNameIgnoringCaseAndSurroundingSpaces() {
        assertEquals(listOf(boxes[1]), BoxPickerQuery.filter(boxes, " reading "))
        assertEquals(listOf(boxes[1]), BoxPickerQuery.filter(boxes, "READING"))
    }

    @Test
    fun queryMatchesPartialNameAndKeepsOrder() {
        assertEquals(listOf(boxes[0], boxes[2]), BoxPickerQuery.filter(boxes, "工作"))
    }

    @Test
    fun noMatchYieldsEmptyListAndOffersCreate() {
        val filtered = BoxPickerQuery.filter(boxes, "灵感")
        assertTrue(filtered.isEmpty())
        // 关键字没有匹配 → 必须给出「新建卡片盒」入口。
        assertTrue(BoxPickerQuery.canCreate(boxes, "灵感"))
        assertEquals("灵感", BoxPickerQuery.createName("  灵感  "))
    }

    @Test
    fun existingNameIsNotOfferedForCreation() {
        assertFalse(BoxPickerQuery.canCreate(boxes, "工作"))
        assertFalse(BoxPickerQuery.canCreate(boxes, "  工作 "))
    }

    @Test
    fun blankQueryCannotCreate() {
        assertFalse(BoxPickerQuery.canCreate(boxes, ""))
        assertFalse(BoxPickerQuery.canCreate(boxes, "   "))
        assertNull(BoxPickerQuery.createName("   "))
    }

    // ---- 新建卡片盒并赋给当前笔记 ----

    @Test
    fun createAndAssignCreatesThenAssignsTrimmedName() = runBlocking {
        val created = mutableListOf<String>()
        val assigned = mutableListOf<Pair<String, String>>()
        val result = NoteBoxActions.createAndAssign(
            createBox = { name ->
                created += name
                box("new", name)
            },
            assignNoteToBox = { noteId, boxId -> assigned += noteId to boxId },
            noteId = "note-1",
            name = "  灵感  ",
        )
        assertEquals(BoxCreateResult.Created(box("new", "灵感")), result)
        assertEquals(listOf("灵感"), created)
        assertEquals(listOf("note-1" to "new"), assigned)
    }

    @Test
    fun createAndAssignSkipsRepositoryForEmptyName() = runBlocking {
        var called = false
        val result = NoteBoxActions.createAndAssign(
            createBox = { called = true; box("new", it) },
            assignNoteToBox = { _, _ -> called = true },
            noteId = "note-1",
            name = "   ",
        )
        assertEquals(BoxCreateResult.EmptyName, result)
        assertFalse(called)
    }

    @Test
    fun createAndAssignReportsDuplicateWithoutAssigning() = runBlocking {
        val assigned = mutableListOf<String>()
        val result = NoteBoxActions.createAndAssign(
            createBox = { null },
            assignNoteToBox = { _, boxId -> assigned += boxId },
            noteId = "note-1",
            name = "工作",
        )
        assertEquals(BoxCreateResult.DuplicateName, result)
        assertTrue(assigned.isEmpty())
    }

    // ---- 从笔记上移除标签 ----

    private class FakeRemover : NoteTagRemover {
        val removed = mutableListOf<Pair<String, String>>()
        override suspend fun removeTagFromNote(noteId: String, tagId: String) {
            removed += noteId to tagId
        }
    }

    @Test
    fun removeSingleTagOnlyDetachesFromThisNote() = runBlocking {
        val remover = FakeRemover()
        val count = NoteTagActions.removeTagsFromNote(remover, "note-1", listOf("t1"))
        assertEquals(1, count)
        assertEquals(listOf("note-1" to "t1"), remover.removed)
    }

    @Test
    fun removeMultipleTagsDetachesEachOnce() = runBlocking {
        val remover = FakeRemover()
        val count = NoteTagActions.removeTagsFromNote(
            remover = remover,
            noteId = "note-1",
            tagIds = listOf("t1", "t2", " t1 ", "", "  ", "t3"),
        )
        assertEquals(3, count)
        assertEquals(
            listOf("note-1" to "t1", "note-1" to "t2", "note-1" to "t3"),
            remover.removed,
        )
    }

    @Test
    fun removeTagsIgnoresBlankNoteId() = runBlocking {
        val remover = FakeRemover()
        val count = NoteTagActions.removeTagsFromNote(remover, "  ", listOf("t1"))
        assertEquals(0, count)
        assertTrue(remover.removed.isEmpty())
    }
}
