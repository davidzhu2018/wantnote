package com.davidzhu.wantnote.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 0.10.0 卡片盒：笔记的一级归属容器（本地概念，不随 Dinox/云同步分发）。
 *
 * - 「暂存箱」是默认卡片盒：首次启动（或迁移 v7→v8）时自动创建；
 *   删除其他卡片盒时，盒内笔记统一移入「暂存箱」，内容绝不静默丢弃。
 * - 「暂存箱」本身不可删除。
 * - 笔记通过 `Note.boxId` 引用卡片盒；空串表示「未分配」（保存时 AI
 *   会自动分配到一个已存在的卡片盒）。
 */
@Entity(
    tableName = "note_boxes",
    indices = [Index(value = ["name"], unique = true)],
)
data class NoteBox(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
) {
    companion object {
        /** 默认卡片盒名称。 */
        const val DEFAULT_NAME = "暂存箱"

        /** 默认卡片盒的固定 ID（迁移与运行时保持一致，保证幂等）。 */
        const val DEFAULT_ID = "box_default_inbox"
    }
}
