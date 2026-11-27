package com.davidzhu.wantnote.sync

import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteGraph
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.ui.insights.computeNebula3DLayout
import com.davidzhu.wantnote.ui.insights.pickReviewBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class V020FeaturesTest {

    private fun makeNote(id: String, pinned: Boolean = false, starred: Boolean = false, reviewed: Boolean = false) =
        NoteWithDetails(
            note = Note(
                id = id,
                title = "标题 $id",
                body = "正文 $id",
                createdAt = 100L,
                updatedAt = 100L,
                pinned = pinned,
                starred = starred,
                reviewed = reviewed,
            ),
            tags = emptyList(),
            blocks = emptyList(),
            noteBox = null,
        )

    @Test
    fun filterNotes_pinnedOnly_filtersCorrectly() {
        val list = listOf(makeNote("1", pinned = false), makeNote("2", pinned = true), makeNote("3", pinned = true))
        val res = NoteRepository.filterNotes(list, pinnedOnly = true)
        assertEquals(listOf("2", "3"), res.map { it.note.id })
    }

    @Test
    fun wereadGatewayPayload_usesFlatTopLevelParamsAndSkillVersion() {
        val payload = ExternalReadingSyncService.buildWeReadGatewayPayload(
            "/user/notebooks",
            mapOf("count" to 50, "lastSort" to 1700000000L),
        )
        assertEquals("/user/notebooks", payload.getString("api_name"))
        assertEquals(ExternalReadingSyncService.WEREAD_SKILL_VERSION, payload.getString("skill_version"))
        assertEquals(50, payload.getInt("count"))
        assertEquals(1700000000L, payload.getLong("lastSort"))
        assertTrue(!payload.has("params"))
    }

    @Test
    fun wereadParsers_extractNotebooksBookmarksAndReviews() {
        val notebooksJson = """
            {"totalBookCount":1,"hasMore":0,"books":[{"bookId":"b1","noteCount":2,"reviewCount":1,"sort":1710000000,"book":{"title":"三体","author":"刘慈欣"}}]}
        """.trimIndent()
        val books = ExternalReadingSyncService.parseWeReadNotebooks(notebooksJson)
        assertEquals(1, books.size)
        assertEquals("三体", books[0].title)
        assertEquals(3, books[0].noteCount)

        val bmJson = """
            {"chapters":[{"chapterUid":1,"title":"第一章"}],"updated":[{"chapterUid":1,"markText":"给岁月以文明"}]}
        """.trimIndent()
        val bms = ExternalReadingSyncService.parseWeReadBookmarks(bmJson)
        assertEquals(1, bms.size)
        assertEquals("第一章", bms[0].chapterTitle)

        val revJson = """
            {"reviews":[{"review":{"abstract":"弱小和无知不是生存的障碍","content":"经典名句","chapterName":"第二章"}}]}
        """.trimIndent()
        val revs = ExternalReadingSyncService.parseWeReadReviews(revJson)
        assertEquals(1, revs.size)

        val md = ExternalReadingSyncService.formatWeReadBookMarkdown(books[0], bms, revs)
        assertTrue(md.contains("### 第一章"))
        assertTrue(md.contains("> 给岁月以文明"))
        assertTrue(md.contains("经典名句"))
    }

    @Test
    fun readwiseCategoryTag_defaultsToReadwiseAndRespectsCustom() {
        val custom = mapOf("books" to "阅读/书籍")
        assertEquals("阅读/书籍", ExternalReadingSyncService.resolveReadwiseTagForCategory(custom, "books"))
        assertEquals("Readwise", ExternalReadingSyncService.resolveReadwiseTagForCategory(custom, "articles"))
    }

    @Test
    fun pickReviewBatch_prioritizesUnreviewedAndCapsAt20() {
        val pool = (1..12).map { makeNote("u$it", reviewed = false) } +
            (1..20).map { makeNote("r$it", reviewed = true) }
        val batch = pickReviewBatch(pool, batchSize = 20, seed = 42L)
        assertEquals(20, batch.size)
        // All 12 unreviewed notes MUST appear first before any reviewed notes
        val first12 = batch.take(12)
        assertTrue(first12.all { !it.note.reviewed })
        assertEquals(8, batch.drop(12).count { it.note.reviewed })
    }

    @Test
    fun computeNebula3DLayout_generatesFinite3DCoordinatesInsideUnitSphere() {
        val graph = NoteGraph.build(
            notes = listOf("a" to "笔记A", "b" to "笔记B", "c" to "笔记C"),
            links = listOf("a" to "b", "a" to "c"),
        )
        val pts = computeNebula3DLayout(graph)
        assertEquals(3, pts.size)
        pts.values.forEach { p ->
            val dist = sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
            assertTrue("Point distance $dist should be <= 1.01", dist <= 1.01f)
        }
    }
}
