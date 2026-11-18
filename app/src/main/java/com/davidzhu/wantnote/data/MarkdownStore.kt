package com.davidzhu.wantnote.data

import android.content.Context
import java.io.File

/**
 * 0.9.0：笔记正文的 Markdown 文档存储。
 *
 * - 每篇笔记正文对应一个 `.md` 文件：`<数据根目录>/notes/<noteId>.md`，
 *   相对路径记录在 `Note.mdPath`。
 * - 磁盘上的 .md 文件是正文唯一可信来源；`Note.body` 保存同一份内容
 *   （不含标题 H1 行），仅供 SQL 搜索与云同步快照使用。
 * - 完整文档 = 标题 H1 + 内容（见 [MarkdownMigrator.documentOf]，在
 *   transfer 包，纯 JVM）。
 */
class MarkdownStore(private val context: Context) {
    private fun baseDir(): File = AppStorage.baseDir(context)

    /** 一篇笔记的 Markdown 相对路径（相对数据根目录）。 */
    fun relativePathFor(noteId: String): String = "$NOTES_DIR/$noteId.md"

    fun notesDir(): File = File(baseDir(), NOTES_DIR)

    fun fileFor(relativePath: String): File {
        // mdPath 只能指向 notes/ 目录内，防止恢复的数据把路径指到别处。
        val clean = relativePath.replace('\\', '/').trim().trimStart('/')
        val safe = if (clean.startsWith("$NOTES_DIR/") && !clean.contains("..")) {
            clean
        } else {
            ""
        }
        return File(baseDir(), safe)
    }

    /** 读取一篇笔记的完整 Markdown 文档（含标题 H1）；没有文件返回 null。 */
    fun readDocument(mdPath: String): String? {
        if (mdPath.isBlank()) return null
        val file = fileFor(mdPath)
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    /** 写入完整文档；返回实际使用的相对路径，失败返回空串。 */
    fun writeDocument(relativePath: String, document: String): String {
        val safe = relativePath.trim()
        if (!safe.startsWith("$NOTES_DIR/") || safe.contains("..")) return ""
        return runCatching {
            val file = File(baseDir(), safe)
            file.parentFile?.mkdirs()
            file.writeText(document, Charsets.UTF_8)
            safe
        }.getOrDefault("")
    }

    /** 删除一篇笔记的 Markdown 文件（笔记删除 / 清空时调用）。 */
    fun deleteDocument(relativePath: String) {
        if (relativePath.isBlank()) return
        runCatching { fileFor(relativePath).delete() }
    }

    companion object {
        const val NOTES_DIR = "notes"
    }
}
