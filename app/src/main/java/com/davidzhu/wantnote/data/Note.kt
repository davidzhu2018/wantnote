package com.davidzhu.wantnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 0.9.0：正文 Markdown 文档的相对路径（notes/<id>.md，相对
     * AppStorage.baseDir）。空串表示尚未生成 .md 文件（升级迁移前 /
     * 旧备份恢复后，由修复流程补齐）。body 始终保存同一份 Markdown
     * 内容（不含标题 H1），供搜索与云同步快照使用；磁盘上的 .md 文件
     * 是正文唯一可信来源。
     */
    val mdPath: String = "",
    /**
     * 0.10.0：所属卡片盒 [NoteBox] 的 ID。空串 = 未分配（保存时 AI 会
     * 自动分配到一个已存在的卡片盒）。卡片盒是本地组织概念，不随
     * Dinox / 云同步快照分发；远端合并进来的笔记保留本地原归属。
     */
    val boxId: String = "",
    /** 0.18.0：笔记关联的地点 POI（高德/腾讯地图或自定义位置，朋友圈视图展示）。 */
    val locationName: String = "",
    val locationAddress: String = "",
    val locationLat: Double = 0.0,
    val locationLng: Double = 0.0,
    /** 0.19.0：置顶（任意视图始终排在最前面）与星标（搜索页支持星标筛选）。 */
    val pinned: Boolean = false,
    val starred: Boolean = false,
    val reviewed: Boolean = false,
)
