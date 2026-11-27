package com.davidzhu.wantnote.sync

import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.transfer.ImportSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinStarAndReadingSyncTest {

    private fun sampleNote(
        id: String,
        title: String,
        updatedAt: Long,
        pinned: Boolean = false,
        starred: Boolean = false,
    ): NoteWithDetails = NoteWithDetails(
        note = Note(
            id = id,
            title = title,
            body = "正文 $title",
            createdAt = updatedAt,
            updatedAt = updatedAt,
            pinned = pinned,
            starred = starred,
        ),
        tags = emptyList(),
        blocks = emptyList(),
        noteBox = null,
    )

    @Test
    fun filterNotes_starredOnly_returnsOnlyStarred() {
        val list = listOf(
            sampleNote("1", "普通笔记", 300L, pinned = false, starred = false),
            sampleNote("2", "星标笔记A", 200L, pinned = false, starred = true),
            sampleNote("3", "置顶且星标", 100L, pinned = true, starred = true),
        )
        val result = NoteRepository.filterNotes(list, starredOnly = true)
        assertEquals(listOf("2", "3"), result.map { it.note.id })
    }

    @Test
    fun pinnedNotes_alwaysSortBeforeUnpinnedRegardlessOfTimestamp() {
        val list = listOf(
            sampleNote("new", "最新普通笔记", 5000L, pinned = false),
            sampleNote("old-pin", "旧置顶笔记", 1000L, pinned = true),
            sampleNote("mid-pin", "较新置顶笔记", 2000L, pinned = true),
        )
        val sorted = list.sortedWith(
            compareByDescending<NoteWithDetails> { it.note.pinned }.thenByDescending { it.note.updatedAt },
        )
        assertEquals(listOf("mid-pin", "old-pin", "new"), sorted.map { it.note.id })
    }

    @Test
    fun formatFlomoPayload_includesTitleBodyAndHashTags() {
        val note = Note(id = "n1", title = "标题", body = "第一段内容", createdAt = 1L, updatedAt = 1L)
        val tags = listOf(
            Tag(id = "t1", path = "阅读/笔记", color = Tag.defaultColor(), createdAt = 1L),
            Tag(id = "t2", path = "灵感", color = Tag.defaultColor(), createdAt = 1L),
        )
        val payload = ExternalReadingSyncService.formatFlomoPayload(note, tags)
        assertTrue(payload.startsWith("标题\n\n第一段内容"))
        assertTrue(payload.contains("#阅读/笔记"))
        assertTrue(payload.contains("#灵感"))
    }

    @Test
    fun categoryBoxMap_roundTripsCleanly() {
        val original = mapOf("books" to "box-books", "articles" to "box-articles")
        val json = ExternalReadingSyncService.serializeCategoryBoxMap(original)
        val parsed = ExternalReadingSyncService.parseCategoryBoxMap(json)
        assertEquals(original, parsed)
    }

    @Test
    fun parseReadwiseExportPage_extractsCategoryHighlightsAndCursor() {
        val json = """
            {
              "nextPageCursor": "cursor-2",
              "results": [
                {
                  "title": "Deep Work",
                  "author": "Cal Newport",
                  "category": "books",
                  "source_url": "https://example.com/deep-work",
                  "highlights": [
                    {"text": "Focus is the new IQ.", "note": "核心观点"}
                  ]
                }
              ]
            }
        """.trimIndent()
        val page = ExternalReadingSyncService.parseReadwiseExportPage(json)
        assertEquals("cursor-2", page.nextCursor)
        assertEquals(1, page.docs.size)
        assertEquals("Deep Work", page.docs[0].title)
        assertEquals("books", page.docs[0].category)
        assertTrue(page.docs[0].markdown.contains("> Focus is the new IQ."))
        assertTrue(page.docs[0].markdown.contains("笔记：核心观点"))
    }

    @Test
    fun importSourceModes_coverAllFiveRequiredEntries() {
        val modes = ImportSourceMode.entries
        assertEquals(5, modes.size)
    }
}
