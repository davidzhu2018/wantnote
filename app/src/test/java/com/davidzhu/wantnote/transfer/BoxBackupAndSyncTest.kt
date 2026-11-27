package com.davidzhu.wantnote.transfer

import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.sync.SyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.14.0 备份/恢复与 WebDAV/S3 云同步卡片盒往返测试：
 * - 备份清单支持 boxes 计数且兼容旧清单；
 * - BackupBoxPlan 完整恢复卡片盒、去重重名盒并补建仅有 boxName 的缺失盒；
 * - SyncSnapshot（WebDAV / S3 共用）往返保留 boxes 列表及每篇笔记的 boxId / boxName。
 */
class BoxBackupAndSyncTest {

    @Test
    fun backupManifestRoundTripsBoxesAndAcceptsLegacyText() {
        val m = BackupManifest(notes = 3, boxes = 4, appVersion = "0.14.0")
        val parsed = BackupManifest.fromText(m.toText())
        assertEquals(4, parsed.boxes)
        assertEquals(3, parsed.notes)

        val legacyText = m.toText().lines().filterNot { it.startsWith("boxes=") }.joinToString("\n")
        val legacy = BackupManifest.fromText(legacyText)
        assertEquals(0, legacy.boxes)
    }

    @Test
    fun backupBoxPlanRestoresBoxesDeduplicatesAndRecreatesMissingByName() {
        val boxes = listOf(
            NoteBox(id = NoteBox.DEFAULT_ID, name = NoteBox.DEFAULT_NAME, createdAt = 0L),
            NoteBox(id = "b1", name = "工作", createdAt = 10L),
            NoteBox(id = "b1_dup", name = "工作", createdAt = 20L),
        )
        val notes = listOf(
            Note(id = "n1", title = "A", body = "", createdAt = 1L, updatedAt = 1L, boxId = "b1"),
            Note(id = "n2", title = "B", body = "", createdAt = 2L, updatedAt = 2L, boxId = "b1_dup"),
            Note(id = "n3", title = "C", body = "", createdAt = 3L, updatedAt = 3L, boxId = "b_missing"),
        )
        val plan = BackupBoxPlan.prepare(
            boxes = boxes,
            notes = notes,
            noteBoxNames = mapOf("n3" to "灵感"),
            now = 99L,
        )

        val names = plan.boxes.map { it.name }.toSet()
        assertTrue(NoteBox.DEFAULT_NAME in names)
        assertTrue("工作" in names)
        assertTrue("灵感" in names)
        assertEquals(3, plan.boxes.size)

        val byNote = plan.notes.associateBy { it.id }
        assertEquals("b1", byNote.getValue("n1").boxId)
        assertEquals("b1", byNote.getValue("n2").boxId)
        assertEquals("b_missing", byNote.getValue("n3").boxId)
    }

    @Test
    fun syncSnapshotRoundTripsBoxesAndNoteBoxIds() {
        val boxes = listOf(
            NoteBox(id = NoteBox.DEFAULT_ID, name = NoteBox.DEFAULT_NAME, createdAt = 0L),
            NoteBox(id = "b_read", name = "阅读笔记", createdAt = 100L),
        )
        val notes = listOf(
            Note(id = "n1", title = "书摘", body = "内容", createdAt = 1L, updatedAt = 2L, mdPath = "notes/n1.md", boxId = "b_read"),
        )
        val snapshot = SyncSnapshot(
            exportedAt = 500L,
            notes = notes,
            blocks = emptyList(),
            tags = emptyList(),
            noteTags = emptyList(),
            attachments = emptyList(),
            boxes = boxes,
            noteBoxNames = mapOf("n1" to "阅读笔记"),
        )

        val restored = SyncSnapshot.fromJson(snapshot.toJson())
        assertEquals(2, restored.boxes.size)
        assertEquals("阅读笔记", restored.boxes.first { it.id == "b_read" }.name)
        assertEquals("b_read", restored.notes.single().boxId)
        assertEquals("阅读笔记", restored.noteBoxNames["n1"])
    }
}
