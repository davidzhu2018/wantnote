package com.davidzhu.wantnote.transfer

/**
 * 备份 zip 的清单。使用纯 Kotlin 的 `key=value` 文本格式，既方便人工查看，
 * 也能在 JVM 单元测试里直接做读写往返（不依赖 Android 的 org.json）。
 *
 * zip 结构：
 *   manifest.txt                 清单（本类）
 *   data/notes.json              笔记
 *   data/blocks.json             块
 *   data/tags.json               标签
 *   data/note_tags.json          笔记-标签
 *   data/settings.json           AppSettings 全部字段
 *   data/ai_providers.json       AI 提供商与密钥
 *   media/<相对路径>             附件 / 图片 / 视频
 *   fonts/<文件名>               自定义字体
 *
 * 清单里 data 目录下的文件是恢复时的必需文件；media、fonts 允许缺失（会跳过并提示）。
 */
data class BackupManifest(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAt: Long = 0L,
    val appVersion: String = "",
    val notes: Int = 0,
    val blocks: Int = 0,
    val tags: Int = 0,
    val noteTags: Int = 0,
    val links: Int = 0,
    val boxes: Int = 0,
    val dinoxSyncRules: Int = 0,
    val attachments: Int = 0,
    val fonts: Int = 0,
) {
    fun toText(): String = buildString {
        appendLine("format=$format")
        appendLine("version=$version")
        appendLine("createdAt=$createdAt")
        appendLine("appVersion=$appVersion")
        appendLine("notes=$notes")
        appendLine("blocks=$blocks")
        appendLine("tags=$tags")
        appendLine("noteTags=$noteTags")
        appendLine("links=$links")
        appendLine("boxes=$boxes")
        appendLine("dinoxSyncRules=$dinoxSyncRules")
        appendLine("attachments=$attachments")
        appendLine("fonts=$fonts")
        appendLine("required=${RequiredDataFiles.joinToString(",")}")
    }

    class InvalidBackup(message: String) : Exception(message)

    companion object {
        const val FORMAT = "wantnote-backup"
        const val VERSION = 1
        const val MANIFEST_ENTRY = "manifest.txt"

        val RequiredDataFiles = listOf(
            "data/notes.json",
            "data/blocks.json",
            "data/tags.json",
            "data/note_tags.json",
            "data/settings.json",
            "data/ai_providers.json",
        )

        fun fromText(text: String): BackupManifest {
            val values = LinkedHashMap<String, String>()
            text.replace("\r\n", "\n").split('\n').forEach { line ->
                if (line.isBlank()) return@forEach
                val index = line.indexOf('=')
                if (index <= 0) return@forEach
                values[line.substring(0, index).trim()] = line.substring(index + 1).trim()
            }
            val format = values["format"]
                ?: throw InvalidBackup("备份清单缺少 format，可能不是想记的备份文件。")
            if (format != FORMAT) {
                throw InvalidBackup("备份格式不匹配（$format），可能不是想记的备份文件。")
            }
            val version = values["version"]?.toIntOrNull()
                ?: throw InvalidBackup("备份清单缺少版本号，文件可能已损坏。")
            if (version <= 0 || version > VERSION) {
                throw InvalidBackup("备份版本 v$version 高于当前支持的最高版本 v$VERSION，请升级应用后再恢复。")
            }
            val required = values["required"]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            if (required.isNotEmpty() && !required.containsAll(RequiredDataFiles)) {
                throw InvalidBackup("备份清单声明的数据文件不完整，文件可能已损坏。")
            }
            fun number(key: String): Int = values[key]?.toIntOrNull() ?: 0
            return BackupManifest(
                format = format,
                version = version,
                createdAt = values["createdAt"]?.toLongOrNull() ?: 0L,
                appVersion = values["appVersion"].orEmpty(),
                notes = number("notes"),
                blocks = number("blocks"),
                tags = number("tags"),
                noteTags = number("noteTags"),
                links = number("links"),
                boxes = number("boxes"),
                dinoxSyncRules = number("dinoxSyncRules"),
                attachments = number("attachments"),
                fonts = number("fonts"),
            )
        }
    }
}
