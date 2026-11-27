package com.davidzhu.wantnote.data

import androidx.room.withTransaction
import com.davidzhu.wantnote.transfer.MarkdownBlock
import com.davidzhu.wantnote.transfer.MarkdownCodec
import com.davidzhu.wantnote.transfer.MarkdownMigrator
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class DayTask(
    val block: NoteBlock,
    val note: Note,
)

/**
 * 0.9.0 起 Markdown 是正文的唯一可信来源：
 *
 * - `saveMarkdown` 先写 `notes/<id>.md`（磁盘文件是源），再在一个事务里
 *   更新 body 镜像、重建派生块缓存（供日历任务 / 卡片媒体缩略图）并按
 *   正文里的 `[[note:id]]` 标记重建双向链接。
 * - 派生块用稳定 ID：内容未变的块在重新解析后保留原 ID，让日历勾选等
 *   基于 blockId 的操作在多次保存之间保持有效。
 * - 0.8.0 及更早的块数据通过 [migrateNoteToMarkdown] 强制迁移（不可跳过）。
 */
class NoteRepository(
    private val db: NoteDatabase,
    private val markdownStore: MarkdownStore,
) {
    private val notes = db.noteDao()
    private val blocks = db.blockDao()
    private val tags = db.tagDao()
    private val links = db.noteLinkDao()
    private val boxes = db.noteBoxDao()

    fun observeAll(): Flow<List<Note>> = notes.observeAll()

    fun observeAllLinks(): Flow<List<NoteLink>> = links.observeAll()

    fun observeAllWithBlocks(): Flow<List<NoteWithBlocks>> = notes.observeAllWithBlocks()

    fun observeById(id: String): Flow<Note?> = notes.observeById(id)

    fun search(query: String): Flow<List<Note>> = notes.search(query)

    suspend fun getById(id: String): Note? = notes.getById(id)

    suspend fun getBlocks(noteId: String): List<NoteBlock> = blocks.getByNoteId(noteId)

    // ---- Markdown 源（0.9.0） ------------------------------------------

    /** 把 Markdown 内容解析为块模型（本地 media 引用全部原样保留）。 */
    fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
        return MarkdownCodec.parseMarkdown(
            content,
            fallbackTitle = "",
            resolveMedia = { it },
            consumeLeadingTitle = false,
        ).blocks
    }

    /** 编辑器展示用：内容 → 派生块（沿用旧块的稳定 ID 与字号）。 */
    suspend fun deriveBlocks(noteId: String, content: String): List<NoteBlock> {
        return withStableIds(
            noteId,
            parseMarkdownBlocks(content),
            blocks.getByNoteId(noteId),
        )
    }

    /** 派生块的稳定 ID：类型/文本/缩进/勾选都相同的块沿用旧 ID（旧字号也保留）。 */
    private fun withStableIds(
        noteId: String,
        markdownBlocks: List<MarkdownBlock>,
        previous: List<NoteBlock>,
    ): List<NoteBlock> {
        val available = previous.toMutableList()
        return markdownBlocks.mapIndexed { sortOrder, mb ->
            val matchIndex = available.indexOfFirst { pb ->
                pb.type == mb.type.name &&
                    pb.text == mb.text &&
                    pb.indent == mb.indent.coerceIn(0, 8) &&
                    (mb.type != BlockType.TASK || pb.checked == mb.checked)
            }
            val reused = if (matchIndex >= 0) available.removeAt(matchIndex) else null
            NoteBlock(
                id = reused?.id ?: UUID.randomUUID().toString(),
                noteId = noteId,
                type = mb.type.name,
                text = mb.text,
                indent = mb.indent.coerceIn(0, 8),
                checked = mb.checked,
                fontSize = reused?.fontSize ?: BlockFontSize.NORMAL.name,
                mediaPath = mb.mediaPath,
                sortOrder = sortOrder,
                headingLevel = mb.headingLevel.coerceIn(0, 6),
            )
        }
    }

    /** 完整文档（文件内容）：标题 H1 + 正文内容。 */
    fun documentOf(note: Note, content: String): String =
        MarkdownMigrator.documentOf(note.title, content)

    /** 从完整文档剥离标题 H1，得到正文内容（与 [documentOf] 互逆）。 */
    fun contentFromDocument(note: Note, document: String): String {
        val trimmed = document.trimEnd('\n')
        if (note.title.isBlank()) return trimmed
        val lines = trimmed.split("\n")
        return if (lines.firstOrNull()?.startsWith("# ") == true) {
            lines.drop(1).joinToString("\n").trimStart('\n')
        } else {
            trimmed
        }
    }

    /**
     * 当前内容源：优先读 .md 文件（唯一可信来源）；文件缺失时用 body
     * 镜像补写文件（旧备份恢复 / 文件被清理后的自愈）。
     */
    suspend fun loadContent(note: Note): String {
        val document = markdownStore.readDocument(note.mdPath)
        if (document != null) {
            return contentFromDocument(note, document)
        }
        if (note.mdPath.isBlank()) {
            // 尚未迁移的旧数据：直接用 body（迁移流程会补文件）。
            return note.body
        }
        markdownStore.writeDocument(note.mdPath, documentOf(note, note.body))
        return note.body
    }

    /**
     * 保存 Markdown 内容：先写文件（源），再提交数据库（body 镜像、
     * 派生块、链接）。若内容以 H1 开头，首个 H1 作为标题（与导入一致），
     * 避免下次保存时标题行重复。
     */
    suspend fun saveMarkdown(noteId: String, title: String, contentMarkdown: String): Note? {
        val existing = notes.getById(noteId) ?: return null
        val content = contentMarkdown.replace("\r\n", "\n").trimEnd('\n')
        val parsed = parseMarkdownBlocks(content)
        // 正文与标题分开存：不要把正文里的第一个标题偷成笔记标题。
        val effectiveTitle = title
        val mdPath = existing.mdPath.ifBlank { markdownStore.relativePathFor(noteId) }
        markdownStore.writeDocument(mdPath, MarkdownMigrator.documentOf(effectiveTitle, content))
        val previous = blocks.getByNoteId(noteId)
        val nextBlocks = withStableIds(noteId, parsed, previous)
        val updated = existing.copy(
            title = effectiveTitle,
            body = content,
            mdPath = mdPath,
            updatedAt = System.currentTimeMillis(),
        )
        db.withTransaction {
            notes.upsert(updated)
            blocks.deleteByNoteId(noteId)
            if (nextBlocks.isNotEmpty()) blocks.upsertAll(nextBlocks)
            syncOutgoingLinksLocked(noteId, content)
        }
        return updated
    }

    /**
     * 导入/恢复时写入一篇新笔记：写 .md 文件、body 镜像、派生块与链接
     * 在同一事务内完成，避免出现半篇笔记。
     */
    suspend fun insertImportedMarkdown(note: Note, contentMarkdown: String): Note {
        val content = contentMarkdown.replace("\r\n", "\n").trimEnd('\n')
        val mdPath = note.mdPath.ifBlank { markdownStore.relativePathFor(note.id) }
        markdownStore.writeDocument(mdPath, MarkdownMigrator.documentOf(note.title, content))
        val nextBlocks = withStableIds(note.id, parseMarkdownBlocks(content), emptyList())
        val stored = note.copy(body = content, mdPath = mdPath)
        db.withTransaction {
            notes.upsert(stored)
            blocks.deleteByNoteId(note.id)
            if (nextBlocks.isNotEmpty()) blocks.upsertAll(nextBlocks)
            syncOutgoingLinksLocked(note.id, content)
        }
        return stored
    }

    /** 还未生成 .md 文件的笔记（强制迁移的待处理清单）。 */
    suspend fun pendingMarkdownMigration(): List<Note> {
        return notes.getAll().filter { it.mdPath.isBlank() }
    }

    /**
     * 强制迁移一篇：块/旧 body → Markdown，写文件，重建块缓存与链接。
     * 幂等：已迁移（mdPath 非空）的笔记直接跳过，所以中断后可续跑。
     */
    suspend fun migrateNoteToMarkdown(note: Note): Boolean {
        if (note.mdPath.isNotBlank()) return false
        val blockList = blocks.getByNoteId(note.id)
        val migrated = MarkdownMigrator.migrate(note, blockList)
        val mdPath = markdownStore.relativePathFor(note.id)
        markdownStore.writeDocument(mdPath, migrated.document)
        val nextBlocks = withStableIds(note.id, migrated.markdownBlocks, blockList)
        db.withTransaction {
            notes.upsert(
                note.copy(
                    body = migrated.content,
                    mdPath = mdPath,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            blocks.deleteByNoteId(note.id)
            if (nextBlocks.isNotEmpty()) blocks.upsertAll(nextBlocks)
            syncOutgoingLinksLocked(note.id, migrated.content)
        }
        return true
    }

    /** 恢复备份 / 云同步之后：给所有缺 .md 文件的笔记补文件，返回补写篇数。 */
    suspend fun ensureMarkdownFiles(): Int {
        var repaired = 0
        notes.getAll().forEach { note ->
            if (note.mdPath.isBlank()) {
                if (migrateNoteToMarkdown(note)) repaired += 1
            } else if (markdownStore.readDocument(note.mdPath) == null) {
                markdownStore.writeDocument(note.mdPath, documentOf(note, note.body))
                repaired += 1
            }
        }
        return repaired
    }

    suspend fun createBlank(): Note {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val mdPath = markdownStore.relativePathFor(id)
        val note = Note(
            id = id,
            title = "",
            body = "",
            createdAt = now,
            updatedAt = now,
            mdPath = mdPath,
        )
        val starter = NoteBlock.paragraph(
            id = UUID.randomUUID().toString(),
            noteId = note.id,
            text = "",
            sortOrder = 0,
        )
        markdownStore.writeDocument(mdPath, "")
        db.withTransaction {
            notes.upsert(note)
            blocks.upsertAll(listOf(starter))
        }
        return note
    }

    /**
     * 勾选/取消一个任务块：以 Markdown 源为准改写内容并整体落盘
     * （文件 + body 镜像 + 派生块）。
     */
    suspend fun setTaskChecked(blockId: String, checked: Boolean) {
        val target = blocks.getAllTasks().firstOrNull { it.id == blockId } ?: return
        val note = notes.getById(target.noteId) ?: return
        val content = loadContent(note)
        val parsed = parseMarkdownBlocks(content).toMutableList()
        val index = when {
            target.sortOrder in parsed.indices && parsed[target.sortOrder].type == BlockType.TASK ->
                target.sortOrder
            else -> parsed.indexOfFirst { it.type == BlockType.TASK && it.text == target.text }
        }
        if (index < 0) return
        parsed[index] = parsed[index].copy(checked = checked)
        saveMarkdown(note.id, note.title, MarkdownCodec.render(title = "", blocks = parsed))
    }

    suspend fun deleteIfBlank(id: String): Boolean {
        val existing = notes.getById(id) ?: return false
        val blockList = blocks.getByNoteId(id)
        val emptyBlocks = blockList.isEmpty() || blockList.all { it.isBlank() }
        if (existing.title.isBlank() && emptyBlocks && existing.body.isBlank()) {
            db.withTransaction {
                blocks.deleteByNoteId(id)
                notes.delete(id)
            }
            markdownStore.deleteDocument(existing.mdPath)
            return true
        }
        return false
    }

    /**
     * Permanently delete one note. Blocks, tag associations and both
     * directions of note links are removed in the same transaction; the
     * foreign keys also cascade, so no dangling relation can survive.
     * The .md document is deleted with it.
     */
    suspend fun deleteNote(id: String): Boolean {
        val existing = notes.getById(id) ?: return false
        db.withTransaction {
            links.deleteOutgoing(id)
            db.tagDao().clearNoteTags(id)
            blocks.deleteByNoteId(id)
            notes.delete(id)
        }
        markdownStore.deleteDocument(existing.mdPath)
        return true
    }

    suspend fun getOutgoingLinks(noteId: String): List<NoteLink> = links.getOutgoing(noteId)

    suspend fun getIncomingLinks(noteId: String): List<NoteLink> = links.getIncoming(noteId)

    suspend fun getAllLinks(): List<NoteLink> = links.getAll()

    /**
     * Rebuild this note's outgoing relations from the `[[note:id]]` markers
     * in [text]. Invalid/self/duplicate targets are ignored, so edits and
     * removals in the editor are reflected immediately.
     */
    private suspend fun syncOutgoingLinksLocked(noteId: String, text: String) {
        val requested = NoteLinkCodec.targetIds(text)
            .filter { it != noteId }
            .distinct()
        val valid = if (requested.isEmpty()) {
            emptySet()
        } else {
            notes.existingIds(requested).toSet()
        }
        links.deleteOutgoing(noteId)
        if (valid.isNotEmpty()) {
            val now = System.currentTimeMillis()
            links.insertAll(
                requested.filter { it in valid }.map { target ->
                    NoteLink(sourceNoteId = noteId, targetNoteId = target, createdAt = now)
                },
            )
        }
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
        val content = loadContent(existing)
        val mdPath = existing.mdPath.ifBlank { markdownStore.relativePathFor(id) }
        val updated = existing.copy(title = title, mdPath = mdPath, updatedAt = System.currentTimeMillis())
        markdownStore.writeDocument(mdPath, documentOf(updated, content))
        notes.upsert(updated)
    }

    /** Outcome of [fillEmptyTitleAndTags]: which empty fields were filled. */
    data class FillResult(val titleFilled: Boolean, val tagsFilled: Boolean)

    /**
     * Fill ONLY blank fields, preserving anything the user wrote or changed:
     * the decision is made inside one transaction at apply time, so user edits
     * that landed while the AI request was in flight are respected.
     * Title is set only if the stored title is still blank; tags are attached
     * only if the note still has none. Nothing is ever overwritten.
     */
    suspend fun fillEmptyTitleAndTags(
        noteId: String,
        suggestedTitle: String,
        suggestedTags: List<String>,
    ): FillResult {
        return db.withTransaction {
            val note = notes.getById(noteId) ?: return@withTransaction FillResult(false, false)
            var titleFilled = false
            if (note.title.isBlank() && suggestedTitle.isNotBlank()) {
                val next = suggestedTitle.trim().take(40)
                if (next.isNotEmpty()) {
                    val content = loadContent(note)
                    val mdPath = note.mdPath.ifBlank { markdownStore.relativePathFor(noteId) }
                    val updated = note.copy(title = next, mdPath = mdPath, updatedAt = System.currentTimeMillis())
                    markdownStore.writeDocument(mdPath, documentOf(updated, content))
                    notes.upsert(updated)
                    titleFilled = true
                }
            }
            var tagsFilled = false
            if (tags.getForNote(noteId).isEmpty() && suggestedTags.isNotEmpty()) {
                val ids = suggestedTags.mapNotNull { path -> upsertTag(path)?.id }.distinct()
                if (ids.isNotEmpty()) {
                    tags.upsertCrossRefs(ids.map { NoteTagCrossRef(noteId, it) })
                    tagsFilled = true
                }
            }
            FillResult(titleFilled = titleFilled, tagsFilled = tagsFilled)
        }
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

    // ---- 卡片盒（0.10.0） ----------------------------------------------

    fun observeBoxes(): Flow<List<NoteBox>> = boxes.observeAll()

    suspend fun getAllBoxes(): List<NoteBox> = boxes.getAll()

    suspend fun getBoxById(id: String): NoteBox? = boxes.getById(id)

    suspend fun getBoxByName(name: String): NoteBox? = boxes.getByName(name)

    /** 每个卡片盒的笔记数（按 boxId 分组；未分配不算在任何盒里）。 */
    suspend fun boxNoteCounts(): Map<String, Int> {
        return boxes.noteCounts().associate { it.boxId to it.noteCount }
    }

    /**
     * 确保「暂存箱」存在（幂等）：应用启动、卡片盒管理、删除卡片盒与
     * AI 自动分配前都会调用。名字被占用（理论上不可能）时按已有盒子返回。
     */
    suspend fun ensureDefaultBox(): NoteBox {
        boxes.getById(NoteBox.DEFAULT_ID)?.let { return it }
        boxes.getByName(NoteBox.DEFAULT_NAME)?.let { return it }
        val created = NoteBox(
            id = NoteBox.DEFAULT_ID,
            name = NoteBox.DEFAULT_NAME,
            createdAt = 0L,
        )
        boxes.insertIfAbsent(created)
        return boxes.getById(NoteBox.DEFAULT_ID) ?: created
    }

    /** 新建卡片盒；名字为空或与已有盒子重名时返回 null（UI 给重名提示）。 */
    suspend fun createBox(name: String): NoteBox? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null
        if (boxes.getByName(normalized) != null) return null
        val box = NoteBox(
            id = UUID.randomUUID().toString(),
            name = normalized,
            createdAt = System.currentTimeMillis(),
        )
        if (boxes.insertIfAbsent(box) == -1L) return null
        return box
    }

    /** 重命名卡片盒；名字为空或与其他盒子重名时抛 [BoxNameConflict]。 */
    suspend fun renameBox(boxId: String, newName: String): NoteBox? {
        val normalized = newName.trim()
        if (normalized.isEmpty()) return null
        val existing = boxes.getById(boxId) ?: return null
        if (existing.name == normalized) return existing
        if (boxes.getByName(normalized) != null) throw BoxNameConflict(normalized)
        boxes.update(existing.copy(name = normalized))
        return boxes.getById(boxId)
    }

    /**
     * 删除卡片盒：盒内笔记全部移入「暂存箱」（同一事务），笔记内容、
     * 标签、链接都不受影响。「暂存箱」本身不可删除，返回 false。
     */
    suspend fun deleteBox(boxId: String): Boolean {
        val box = boxes.getById(boxId) ?: return false
        if (boxId == NoteBox.DEFAULT_ID) return false
        val fallback = ensureDefaultBox()
        db.withTransaction {
            notes.moveNotes(boxId, fallback.id)
            boxes.delete(boxId)
        }
        return true
    }

    /** 把笔记分配到卡片盒；boxId 必须已存在（空串 = 取消分配）。 */
    suspend fun assignNoteToBox(noteId: String, boxId: String) {
        if (boxId.isNotEmpty() && boxes.getById(boxId) == null) return
        notes.assignBox(noteId, boxId)
    }

    /**
     * AI 自动分配的落地：只接受一个已存在卡片盒的名字（大小敏感、去空白）；
     * 匹配不到就返回 null，绝不新建盒子。
     */
    suspend fun assignNoteToBoxByName(noteId: String, boxName: String): NoteBox? {
        val box = boxes.getByName(boxName.trim()) ?: return null
        notes.assignBox(noteId, box.id)
        return box
    }

    class BoxNameConflict(val name: String) : Exception("卡片盒名称已存在：$name")


    suspend fun getAllNotes(): List<Note> = notes.getAll()

    suspend fun getAllBlocks(): List<NoteBlock> = blocks.getAll()

    suspend fun getAllMediaBlocks(): List<NoteBlock> = blocks.getAllWithMedia()

    suspend fun getAllCrossRefs(): List<NoteTagCrossRef> = tags.getAllCrossRefs()

    suspend fun upsertNoteDirect(note: Note) {
        notes.upsert(note)
    }

    class TagPathConflict(val path: String) : Exception("标签路径已存在：$path")

    suspend fun renameTag(tagId: String, newPath: String): Tag? {
        val normalized = Tag.normalizePath(newPath)
        if (normalized.isEmpty()) return null
        val existing = tags.getById(tagId) ?: return null
        if (existing.path == normalized) return existing
        val collision = tags.getByPath(normalized)
        if (collision != null && collision.id != tagId) {
            throw TagPathConflict(normalized)
        }
        val oldPath = existing.path
        val prefix = "$oldPath/"
        val all = tags.getAll()
        db.withTransaction {
            tags.upsert(existing.copy(path = normalized))
            all.filter { it.id != tagId && it.path.startsWith(prefix) }.forEach { child ->
                val suffix = child.path.removePrefix(prefix)
                val nextPath = "$normalized/$suffix"
                val other = tags.getByPath(nextPath)
                if (other != null && other.id != child.id) {
                    throw TagPathConflict(nextPath)
                }
                tags.upsert(child.copy(path = nextPath))
            }
        }
        return tags.getById(tagId)
    }

    suspend fun addChildTag(parentId: String, childName: String, color: Long = Tag.defaultColor()): Tag? {
        val parent = tags.getById(parentId) ?: return null
        val leaf = childName.trim().trim('/')
        if (leaf.isEmpty()) return null
        return upsertTag("${parent.path}/$leaf", color)
    }

    /**
     * Merge a remote note into the local database.
     * Last-write wins by updatedAt; equal timestamps keep the local copy.
     * 0.9.0 起远端 body 就是 Markdown；0.8.0 旧快照（mdPath 为空）则从
     * 远端块重建 Markdown 源。两种情况都会补写本地 .md 文件。
     * Returns true if the local note was inserted or replaced by the remote.
     */
    suspend fun mergeRemoteNote(remote: Note, remoteBlocks: List<NoteBlock>): Boolean {
        val local = notes.getById(remote.id)
        if (remote.mdPath.isNotBlank() || remoteBlocks.isNotEmpty() || remote.body.isNotBlank()) {
            val markdownBlocks = when {
                remote.mdPath.isNotBlank() && remote.body.isNotBlank() ->
                    parseMarkdownBlocks(remote.body)
                remoteBlocks.isNotEmpty() ->
                    MarkdownMigrator.toMarkdownBlocks(remoteBlocks)
                else ->
                    parseMarkdownBlocks(remote.body)
            }
            val content = when {
                remote.mdPath.isNotBlank() -> remote.body
                remoteBlocks.isNotEmpty() ->
                    MarkdownCodec.render(title = "", blocks = markdownBlocks).trimEnd('\n')
                else -> remote.body.replace("\r\n", "\n").trimEnd('\n')
            }
            val mdPath = remote.mdPath.ifBlank { markdownStore.relativePathFor(remote.id) }
            // 0.14.0：WebDAV / S3 同步携带每篇笔记的卡片盒归属。
            // 远端更新或本地未归盒时采纳远端 boxId；否则保留本地已有归属。
            val isNewer = local == null || remote.updatedAt > local.updatedAt
            val resolvedBoxId = when {
                remote.boxId.isNotBlank() && (isNewer || local?.boxId.isNullOrBlank()) -> remote.boxId
                local != null && local.boxId.isNotBlank() -> local.boxId
                else -> remote.boxId
            }
            val remoteMarkdown = remote.copy(body = content, mdPath = mdPath, boxId = resolvedBoxId)
            if (!isNewer && local != null && local.boxId.isBlank() && remote.boxId.isNotBlank()) {
                notes.assignBox(local.id, remote.boxId)
            }
            if (isNewer) {
                markdownStore.writeDocument(mdPath, documentOf(remoteMarkdown, content))
                val previous = local?.let { blocks.getByNoteId(remote.id) }.orEmpty()
                val nextBlocks = withStableIds(remote.id, markdownBlocks, previous)
                db.withTransaction {
                    notes.upsert(remoteMarkdown)
                    blocks.deleteByNoteId(remote.id)
                    if (nextBlocks.isNotEmpty()) blocks.upsertAll(nextBlocks)
                    syncOutgoingLinksLocked(remote.id, content)
                }
                return true
            }
            return false
        }
        // 远端既无 Markdown 也无块：只保留正文镜像（理论上不会出现）。
        if (local == null) {
            db.withTransaction { notes.upsert(remote) }
            return true
        }
        if (remote.updatedAt > local.updatedAt) {
            db.withTransaction { notes.upsert(remote) }
            return true
        }
        return false
    }

    /**
     * 0.14.0：云同步（WebDAV / S3）合并远端卡片盒；按名称/ID 幂等去重，缺失则创建。
     * 返回本机实际落库的 [NoteBox]，供调用方映射远端笔记的 `boxId`。
     */
    suspend fun mergeRemoteBox(remote: NoteBox): NoteBox {
        val normalized = remote.name.trim()
        if (normalized.isEmpty() || remote.id == NoteBox.DEFAULT_ID || normalized == NoteBox.DEFAULT_NAME) {
            return ensureDefaultBox()
        }
        val byId = boxes.getById(remote.id)
        val byName = boxes.getByName(normalized)
        return when {
            byId != null && byId.name == normalized -> byId
            byName != null -> byName
            byId != null -> {
                boxes.update(byId.copy(name = normalized))
                boxes.getById(byId.id) ?: byId.copy(name = normalized)
            }
            else -> {
                val created = NoteBox(
                    id = remote.id.ifBlank { UUID.randomUUID().toString() },
                    name = normalized,
                    createdAt = if (remote.createdAt > 0L) remote.createdAt else System.currentTimeMillis(),
                )
                boxes.insertIfAbsent(created)
                boxes.getById(created.id) ?: boxes.getByName(normalized) ?: created
            }
        }
    }

    suspend fun mergeRemoteTag(remote: Tag): Tag {
        val byId = tags.getById(remote.id)
        val byPath = tags.getByPath(remote.path)
        return when {
            byId != null && byId.path == remote.path -> {
                if (byId.color != remote.color) {
                    val updated = byId.copy(color = remote.color)
                    tags.upsert(updated)
                    updated
                } else {
                    byId
                }
            }
            byId != null && byPath == null -> {
                tags.upsert(byId.copy(path = remote.path, color = remote.color))
                tags.getById(remote.id) ?: byId
            }
            byPath != null && byPath.id != remote.id -> byPath
            else -> {
                tags.upsert(remote)
                remote
            }
        }
    }

    suspend fun attachTag(noteId: String, tagId: String) {
        tags.upsertCrossRef(NoteTagCrossRef(noteId, tagId))
    }

    companion object {
        fun filterNotes(
            items: List<NoteWithDetails>,
            keyword: String = "",
            tagIds: Set<String> = emptySet(),
            startMs: Long? = null,
            endMs: Long? = null,
            boxId: String? = null,
        ): List<NoteWithDetails> {
            val q = keyword.trim()
            return items.filter { item ->
                val keywordOk = q.isEmpty() ||
                    item.note.title.contains(q, ignoreCase = true) ||
                    item.note.body.contains(q, ignoreCase = true) ||
                    item.tags.any { it.path.contains(q, ignoreCase = true) }
                val tagOk = tagIds.isEmpty() || item.tags.any { it.id in tagIds }
                val dateOk = dateMatches(item.note, startMs, endMs)
                // boxId 非空 = 按卡片盒过滤；空串只匹配「未分配」的笔记。
                val boxOk = boxId == null || item.note.boxId == boxId
                keywordOk && tagOk && dateOk && boxOk
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
