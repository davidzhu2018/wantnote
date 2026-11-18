package com.davidzhu.wantnote.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * 数据存放策略（0.6.0）：
 *
 * 笔记数据库和附件不再只放在应用私有目录（context.filesDir / databases，
 * 会随卸载一起删除），而是优先放到用户可见、卸载后仍能找回的
 * 「Documents/WantNote」目录：
 *
 *   Documents/WantNote/wantnote.db   —— Room 数据库（笔记、块、标签、设置、AI 配置）
 *   Documents/WantNote/media/…       —— 笔记附件（图片、视频、文件）
 *   Documents/WantNote/fonts/…       —— 自定义字体
 *
 * - 有存储权限时：数据库与附件都落在 Documents/WantNote。重装后路径固定，
 *   首次启动即可自动发现并加载同一位置的数据，无需任何导入操作。
 * - 没有存储权限时：回退到与 0.5.0 完全相同的私有目录（filesDir + databases），
 *   功能不受影响；界面会引导用户授权，授权后下次启动自动迁移。
 * - 首次启动若私有目录里还有旧库/附件（从 0.5.0 升级），会迁移到
 *   Documents/WantNote（见 [prepare]）。
 */
object AppStorage {
    const val ROOT_DIR_NAME = "WantNote"
    const val DB_NAME = "wantnote.db"
    const val FALLBACK_DIR_NAME = "wantnote-data"

    /** Documents/WantNote（仅在已获得存储权限时可读写）。 */
    fun documentsRoot(context: Context): File {
        val documents = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS,
        )
        return File(documents, ROOT_DIR_NAME)
    }

    /** 是否已拥有写入 Documents 所需的存储权限。 */
    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 当前生效的数据根目录：已授权 → Documents/WantNote；
     * 未授权 → 应用私有目录（与旧版本位置一致，保证旧数据仍可访问）。
     */
    fun baseDir(context: Context): File {
        return if (hasStorageAccess(context)) {
            documentsRoot(context)
        } else {
            context.filesDir
        }
    }

    /** Room 数据库文件位置。 */
    fun dbFile(context: Context): File {
        return if (hasStorageAccess(context)) {
            File(documentsRoot(context), DB_NAME)
        } else {
            // 与 0.5.0 的 Room 默认位置完全相同，旧升级用户直接沿用。
            context.getDatabasePath(DB_NAME)
        }
    }

    /**
     * 启动时的迁移：把私有目录里的旧库和旧附件搬到 Documents/WantNote。
     * 只在已授权、Documents 里还没有库、且私有目录里存在旧库时做真实拷贝。
     * 返回是否发生了数据库迁移（供日志/说明使用）。
     */
    fun prepare(context: Context): Boolean {
        val migrated = migrateDatabase(context)
        migrateDir(File(context.filesDir, "media"), File(documentsRoot(context), "media"))
        migrateDir(File(context.filesDir, "fonts"), File(documentsRoot(context), "fonts"))
        return migrated
    }

    /**
     * 同步迁移旧数据库（0.5.0 及以前存在 databases/wantnote.db）到
     * Documents/WantNote。必须在任何 Room 访问之前调用，避免新库先被
     * 建出来导致旧库被跳过。
     */
    fun migrateDatabase(context: Context): Boolean {
        if (!hasStorageAccess(context)) return false
        val root = documentsRoot(context)
        val legacyDb = context.getDatabasePath(DB_NAME)
        val targetDb = File(root, DB_NAME)
        if (!legacyDb.exists() || targetDb.exists()) return false
        root.mkdirs()
        copyIfPossible(legacyDb, targetDb)
        copyIfPossible(File(legacyDb.parentFile, "$DB_NAME-wal"), File(root, "$DB_NAME-wal"))
        copyIfPossible(File(legacyDb.parentFile, "$DB_NAME-shm"), File(root, "$DB_NAME-shm"))
        return targetDb.exists()
    }

    /** 把旧目录里目标还缺少的文件补拷过去；已存在的不覆盖。 */
    private fun migrateDir(source: File, target: File) {
        if (!source.isDirectory) return
        target.mkdirs()
        source.listFiles()?.filter { it.isFile }?.forEach { file ->
            val dest = File(target, file.name)
            if (!dest.exists() || dest.length() == 0L) {
                copyIfPossible(file, dest)
            }
        }
    }

    private fun copyIfPossible(source: File, target: File): Boolean {
        if (!source.exists()) return false
        return try {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 附件/字体所在的相对路径根目录（media/xxx、fonts/xxx 都相对这里）。 */
    fun relativeRoot(context: Context): String {
        return baseDir(context).absolutePath.trimEnd('/') + "/"
    }
}
