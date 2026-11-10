package com.davidzhu.wantnote.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class DayTask(
    val block: NoteBlock,
    val note: Note,
)

class NoteRepository(private val db: NoteDatabase) {
    private val notes = db.noteDao()
    private val blocks = db.blockDao()
    private val tags = db.tagDao()

    fun observeAll(): Flow<List<Note>> = notes.observeAll()

    fun observeAllWithBlocks(): Flow<List<NoteWithBlocks>> = notes.observeAllWithBlocks()

    fun observeById(id: String): Flow<Note?> = notes.observeById(id)

    fun observeBlocks(noteId: String): Flow<List<NoteBlock>> = blocks.observeByNoteId(noteId)

    fun search(query: String): Flow<List<Note>> = notes.search(query)

    suspend fun getById(id: String): Note? = notes.getById(id)

    suspend fun getBlocks(noteId: String): List<NoteBlock> = blocks.getByNoteId(noteId)

    suspend fun ensureMigratedBlocks(note: Note) {
        if (blocks.countByNoteId(note.id) > 0) return
        val lines = if (note.body.isEmpty()) {
            listOf("")
        } else {
            note.body.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        }
        val imported = lines.mapIndexed { index, line ->
            NoteBlock.paragraph(
                id = UUID.randomUUID().toString(),
                noteId = note.id,
                text = line,
                sortOrder = index,
            )
        }
        blocks.upsertAll(imported)
    }

    suspend fun createBlank(): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "",
            body = "",
            createdAt = now,
            updatedAt = now,
        )
        val starter = NoteBlock.paragraph(
            id = UUID.randomUUID().toString(),
            noteId = note.id,
            text = "",
            sortOrder = 0,
        )
        db.withTransaction {
            notes.upsert(note)
            blocks.upsertAll(listOf(starter))
        }
        return note
    }

    suspend fun save(id: String, title: String, blockList: List<NoteBlock>): Note? {
        val existing = notes.getById(id) ?: return null
        val normalized = blockList.mapIndexed { index, block ->
            block.copy(
                noteId = id,
                indent = block.indent.coerceIn(0, 8),
                sortOrder = index,
            )
        }
        val body = SearchText.fromBlocks(normalized)
        val updated = existing.copy(
            title = title,
            body = body,
            updatedAt = System.currentTimeMillis(),
        )
        db.withTransaction {
            notes.upsert(updated)
            blocks.deleteByNoteId(id)
            if (normalized.isNotEmpty()) {
                blocks.upsertAll(normalized)
            }
        }
        return updated
    }

    suspend fun setTaskChecked(blockId: String, checked: Boolean) {
        db.withTransaction {
            val all = blocks.getAllTasks()
            val target = all.firstOrNull { it.id == blockId } ?: return@withTransaction
            blocks.setChecked(blockId, checked)
            val note = notes.getById(target.noteId) ?: return@withTransaction
            val refreshed = blocks.getByNoteId(note.id).map { block ->
                if (block.id == blockId) block.copy(checked = checked) else block
            }
            notes.upsert(note.copy(body = SearchText.fromBlocks(refreshed)))
        }
    }

    suspend fun deleteIfBlank(id: String): Boolean {
        val existing = notes.getById(id) ?: return false
        val blockList = blocks.getByNoteId(id)
        val emptyBlocks = blockList.isEmpty() || blockList.all { it.isBlank() }
        if (existing.title.isBlank() && emptyBlocks) {
            db.withTransaction {
                blocks.deleteByNoteId(id)
                notes.delete(id)
            }
            return true
        }
        return false
    }

    fun tasksOnDay(notesWithBlocks: List<NoteWithBlocks>, dayStart: Long): List<DayTask> {
        return notesWithBlocks.flatMap { item ->
            if (!Dates.belongsToDay(item.note, dayStart)) {
                emptyList()
            } else {
                item.blocks
                    .filter { it.blockType == BlockType.TASK }
                    .sortedBy { it.sortOrder }
                    .map { DayTask(it, item.note) }
            }
        }
    }

    fun observeAllWithDetails(): Flow<List<NoteWithDetails>> = notes.observeAllWithDetails()

    fun observeAllTags(): Flow<List<Tag>> = tags.observeAll()

    fun observeTagsForNote(noteId: String): Flow<List<Tag>> = tags.observeForNote(noteId)

    suspend fun getTagsForNote(noteId: String): List<Tag> = tags.getForNote(noteId)

    suspend fun getAllTags(): List<Tag> = tags.getAll()

    suspend fun updateTitle(id: String, title: String) {
        val existing = notes.getById(id) ?: return
        notes.upsert(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun upsertTag(path: String, color: Long = Tag.defaultColor()): Tag? {
        val normalized = Tag.normalizePath(path)
        if (normalized.isEmpty()) return null
        val existing = tags.getByPath(normalized)
        if (existing != null) {
            if (existing.color != color && color != Tag.defaultColor()) {
                val updated = existing.copy(color = color)
                tags.upsert(updated)
                return updated
            }
            return existing
        }
        val created = Tag(
            id = UUID.randomUUID().toString(),
            path = normalized,
            color = color,
            createdAt = System.currentTimeMillis(),
        )
        tags.upsert(created)
        return created
    }

    suspend fun setTagColor(tagId: String, color: Long) {
        val existing = tags.getById(tagId) ?: return
        tags.upsert(existing.copy(color = color))
    }

    suspend fun deleteTag(tagId: String) {
        tags.delete(tagId)
    }

    suspend fun setNoteTags(noteId: String, tagIds: List<String>) {
        db.withTransaction {
            tags.clearNoteTags(noteId)
            if (tagIds.isNotEmpty()) {
                tags.upsertCrossRefs(tagIds.distinct().map { NoteTagCrossRef(noteId, it) })
            }
        }
    }

    suspend fun setNoteTagPaths(noteId: String, paths: List<String>, defaultColor: Long = Tag.defaultColor()) {
        val ids = paths.mapNotNull { path -> upsertTag(path, defaultColor)?.id }.distinct()
        setNoteTags(noteId, ids)
    }

    suspend fun addTagToNote(noteId: String, path: String, color: Long = Tag.defaultColor()): Tag? {
        val tag = upsertTag(path, color) ?: return null
        tags.upsertCrossRef(NoteTagCrossRef(noteId, tag.id))
        return tag
    }

    suspend fun removeTagFromNote(noteId: String, tagId: String) {
        tags.removeNoteTag(noteId, tagId)
    }

    companion object {
        fun filterNotes(
            items: List<NoteWithDetails>,
            keyword: String = "",
            tagIds: Set<String> = emptySet(),
            startMs: Long? = null,
            endMs: Long? = null,
        ): List<NoteWithDetails> {
            val q = keyword.trim()
            return items.filter { item ->
                val keywordOk = q.isEmpty() ||
                    item.note.title.contains(q, ignoreCase = true) ||
                    item.note.body.contains(q, ignoreCase = true) ||
                    item.tags.any { it.path.contains(q, ignoreCase = true) }
                val tagOk = tagIds.isEmpty() || item.tags.any { it.id in tagIds }
                val dateOk = dateMatches(item.note, startMs, endMs)
                keywordOk && tagOk && dateOk
            }
        }

        fun dateMatches(note: Note, startMs: Long?, endMs: Long?): Boolean {
            if (startMs == null && endMs == null) return true
            val start = startMs ?: Long.MIN_VALUE
            val end = endMs ?: Long.MAX_VALUE
            return (note.createdAt in start..end) || (note.updatedAt in start..end)
        }

        fun bodyCharCount(blocks: List<NoteBlock>): Int {
            return blocks
                .filter { it.blockType != BlockType.DIVIDER && !it.isMedia }
                .sumOf { block -> block.text.codePointCount(0, block.text.length) }
        }
    }
}
