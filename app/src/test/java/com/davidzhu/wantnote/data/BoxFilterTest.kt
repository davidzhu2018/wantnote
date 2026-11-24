package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.10.0 主页按卡片盒/标签筛选：boxId 非空按归属过滤，空串匹配「未分配」，
 * null 不过滤。纯 JVM 测试（不依赖 Room 运行时）。
 */
class BoxFilterTest {
    private fun noteWith(boxId: String, vararg tagIds: String): NoteWithDetails {
        val note = Note(
            id = boxId.ifEmpty { "note-${tagIds.size}" },
            title = "t",
            body = "",
            createdAt = 0L,
            updatedAt = 0L,
            mdPath = "",
            boxId = boxId,
        )
        return NoteWithDetails(
            note = note,
            blocks = emptyList(),
            noteBox = null,
            tags = tagIds.map { Tag(id = it, path = it, color = 0L, createdAt = 0L) },
        )
    }

    @Test
    fun nullBoxIdMeansNoFilter() {
        val items = listOf(noteWith(""), noteWith("box-a"))
        assertEquals(items, NoteRepository.filterNotes(items))
    }

    @Test
    fun emptyBoxIdMatchesUnassignedOnly() {
        val items = listOf(noteWith(""), noteWith("box-a"))
        assertEquals(listOf(noteWith("")), NoteRepository.filterNotes(items, boxId = ""))
    }

    @Test
    fun boxIdFiltersByAssignment() {
        val items = listOf(noteWith(""), noteWith("box-a"), noteWith("box-b"))
        assertEquals(
            listOf(noteWith("box-b")),
            NoteRepository.filterNotes(items, boxId = "box-b"),
        )
    }

    @Test
    fun tagFilterCombinesWithBoxFilter() {
        val items = listOf(noteWith("box-a", "tag-1"), noteWith("box-b", "tag-1"), noteWith("box-a", "tag-2"))
        assertEquals(
            listOf(noteWith("box-a", "tag-1")),
            NoteRepository.filterNotes(items, tagIds = setOf("tag-1"), boxId = "box-a"),
        )
    }

    @Test
    fun multiTagFilterMatchesAnySelectedTagAndClearsToAll() {
        val n1 = noteWith("box-a", "tag-1")
        val n2 = noteWith("box-b", "tag-2")
        val n3 = noteWith("box-c", "tag-3")
        val items = listOf(n1, n2, n3)

        assertEquals(
            listOf(n1, n2),
            NoteRepository.filterNotes(items, tagIds = setOf("tag-1", "tag-2")),
        )
        assertEquals(
            items,
            NoteRepository.filterNotes(items, tagIds = emptySet()),
        )
    }
}
