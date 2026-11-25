package com.davidzhu.wantnote.data

/**
 * 0.13.0 详情页阅读态「改卡片盒 / 加删标签」的纯逻辑层。
 *
 * 这一层不碰 Room、不碰网络，只描述：
 * - 卡片盒选择窗的关键字过滤、以及「关键字没有匹配」时是否给出「新建卡片盒」；
 * - 新建卡片盒 → 赋给当前笔记 的结果（空名 / 重名 / 成功）；
 * - 从**当前笔记**移除一个或多个标签（只调用 `removeTagFromNote`，绝不做
 *   全局 `deleteTag`）。
 *
 * 之所以单独抽出来，是因为 UI 层（阅读态详情页与编辑器共用同一批弹窗）需要
 * 可单测的判定，且必须保证两条路径不会漂移。
 */
object BoxPickerQuery {
    /**
     * 按关键字过滤本地卡片盒：名字包含关键字（忽略大小写、忽略首尾空白）。
     * 关键字为空时原样返回，保持列表顺序（调用方负责排序）。
     */
    fun filter(boxes: List<NoteBox>, query: String): List<NoteBox> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return boxes
        return boxes.filter { it.name.contains(keyword, ignoreCase = true) }
    }

    /**
     * 是否应该给出「新建卡片盒」入口：关键字非空，且没有同名卡片盒
     * （与 [NoteRepository.createBox] 的判重规则一致：trim 后完全相同即重名）。
     */
    fun canCreate(boxes: List<NoteBox>, query: String): Boolean {
        val name = query.trim()
        if (name.isEmpty()) return false
        return boxes.none { it.name == name }
    }

    /** 新建弹窗里可直接确认的名字（trim 后），空名返回 null。 */
    fun createName(query: String): String? = query.trim().ifEmpty { null }
}

/** 「新建卡片盒并赋给当前笔记」的结果。 */
sealed interface BoxCreateResult {
    data class Created(val box: NoteBox) : BoxCreateResult
    data object EmptyName : BoxCreateResult
    data object DuplicateName : BoxCreateResult
}

object NoteBoxActions {
    /**
     * 新建卡片盒并把它赋给 [noteId]。
     *
     * 空名直接返回 [BoxCreateResult.EmptyName]（不调仓储）；仓储返回 null 说明
     * 重名（[NoteRepository.createBox] 的约定），返回 [BoxCreateResult.DuplicateName]；
     * 成功时**先落库再赋值**，赋值成功后才返回 [BoxCreateResult.Created]。
     */
    suspend fun createAndAssign(
        createBox: suspend (String) -> NoteBox?,
        assignNoteToBox: suspend (String, String) -> Unit,
        noteId: String,
        name: String,
    ): BoxCreateResult {
        val normalized = name.trim()
        if (normalized.isEmpty()) return BoxCreateResult.EmptyName
        val created = createBox(normalized) ?: return BoxCreateResult.DuplicateName
        assignNoteToBox(noteId, created.id)
        return BoxCreateResult.Created(created)
    }
}

/** 只暴露「从笔记上摘掉标签」这一能力，从类型上杜绝误用全局 deleteTag。 */
interface NoteTagRemover {
    suspend fun removeTagFromNote(noteId: String, tagId: String)
}

object NoteTagActions {
    /**
     * 从 [noteId] 移除 [tagIds]（去重、忽略空白 id），返回实际移除的条数。
     * 只调用 [NoteTagRemover.removeTagFromNote]，标签本身（及其颜色/层级）保留，
     * 其他笔记上的同一标签不受影响。
     */
    suspend fun removeTagsFromNote(
        remover: NoteTagRemover,
        noteId: String,
        tagIds: Collection<String>,
    ): Int {
        if (noteId.isBlank()) return 0
        val ids = tagIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        ids.forEach { remover.removeTagFromNote(noteId, it) }
        return ids.size
    }
}
