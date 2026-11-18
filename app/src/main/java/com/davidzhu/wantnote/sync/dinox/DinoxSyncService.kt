package com.davidzhu.wantnote.sync.dinox

import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.data.NoteLinkCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dinox ↔ 本地 双向同步（0.9.0 「生态互联」唯一实现的第三方集成）。
 *
 * - Token 测试成功后才加密落盘（Keystore → [DinoxTokenVault]）；
 *   同时生成「可同步密文」（凭据派生密钥 → [DinoxPortableCipher]）存进
 *   设置，随 WebDAV/S3 设置同步分发给其他设备。
 * - 全量 / 增量拉取：`POST /api/openapi/listNotes`，卡片盒按用户配置过滤，
 *   增量游标是 Dinox 返回的最新 updateTime（ISO 8601）。
 * - 发送到 Dinox：`POST /api/openapi/text/input`，正文 Markdown 原样发送
 *   （`[[note:id]]` 链接标记转成「→ 标题」可读文本），保留原创建时间。
 *
 * 扩展点：后续第三方应用（Obsidian 同步、Web 剪藏等）按同样形状接入
 * 「生态互联」Tab —— 各自实现 Token 存储 + test/save + pull/push，
 * 不要在这里堆专用分支。
 */
class DinoxSyncService(
    private val notes: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val vault: DinoxTokenVault,
    private val client: DinoxClient = DinoxClient(),
) {
    // ---- Token ----------------------------------------------------------

    /**
     * 测试连接；仅当 Dinox 返回成功时才保存 Token（加密落盘 + 可同步密文）。
     * 失败不保存任何东西，返回面向用户的简短错误。
     */
    suspend fun testAndSaveToken(tokenInput: String): Result<String> = withContext(Dispatchers.IO) {
        val token = tokenInput.trim()
        if (token.isEmpty()) {
            return@withContext Result.failure(DinoxApiException("请先粘贴 Token"))
        }
        try {
            client.listNotes(token, DinoxModels.probeBody())
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        vault.save(token)
        sealTokenForSync(token)
        Result.success("连接成功，Token 已加密保存")
    }

    /** 用已保存的 Token 重新测试（设置恢复 / 换 Token 后排查用）。 */
    suspend fun testSavedToken(): Result<String> = withContext(Dispatchers.IO) {
        val token = vault.load()
            ?: return@withContext Result.failure(DinoxApiException("尚未保存 Token，请先粘贴并测试"))
        try {
            client.listNotes(token, DinoxModels.probeBody())
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        Result.success("连接正常")
    }

    fun hasToken(): Boolean = vault.hasToken()

    /** 清除本机 Token 与可同步密文。 */
    suspend fun clearToken() {
        vault.clear()
        settingsRepository.update { it.copy(dinoxTokenSealed = "", dinoxSealMethod = "") }
    }

    /**
     * 设置同步（WebDAV/S3 备份恢复或云同步下载）之后调用：尝试用本地
     * 同步凭据解开 dinoxTokenSealed。解密失败（凭据不同）静默跳过。
     */
    suspend fun importSealedToken() = withContext(Dispatchers.IO) {
        val settings = settingsRepository.get()
        if (settings.dinoxTokenSealed.isBlank() || vault.hasToken()) return@withContext
        val secret = DinoxPortableCipher.syncSecret(
            settings.dinoxSealMethod.ifBlank { settings.syncMethod },
            settings.webdavPassword,
            settings.s3SecretKey,
        )
        val token = DinoxPortableCipher.open(settings.dinoxTokenSealed, secret) ?: return@withContext
        runCatching { vault.save(token) }
    }

    private suspend fun sealTokenForSync(token: String) {
        val settings = settingsRepository.get()
        val secret = DinoxPortableCipher.syncSecret(
            settings.syncMethod,
            settings.webdavPassword,
            settings.s3SecretKey,
        )
        if (secret.isBlank()) {
            // 没有云同步凭据：没有可派生的密钥，仅保留本机加密 Token。
            settingsRepository.update { it.copy(dinoxTokenSealed = "", dinoxSealMethod = "") }
            return
        }
        val sealed = DinoxPortableCipher.seal(token, secret) ?: return
        settingsRepository.update {
            it.copy(dinoxTokenSealed = sealed, dinoxSealMethod = it.syncMethod)
        }
    }

    // ---- Dinox → 本地 ---------------------------------------------------

    /** 全量同步：不传增量游标，拉取（卡片盒过滤后）全部笔记。 */
    suspend fun syncFull(): Result<String> = pull(lastSyncIso = null)

    /** 增量同步：只拉取上次同步之后有更新的笔记。 */
    suspend fun syncIncremental(): Result<String> {
        val settings = settingsRepository.get()
        val cursor = settings.dinoxLastSyncAt
        return pull(lastSyncIso = if (cursor > 0L) DinoxModels.isoFromEpochMs(cursor) else null)
    }

    private suspend fun pull(lastSyncIso: String?): Result<String> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.get()
        val token = vault.load()
            ?: return@withContext Result.failure(DinoxApiException("尚未保存 Dinox Token，请先测试并保存"))
        val boxes = DinoxModels.splitBoxNames(settings.dinoxSyncBox)
        val body = DinoxModels.listNotesBody(lastSyncIso = lastSyncIso, boxNames = boxes)
        val json = try {
            client.listNotes(token, body)
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        val result = try {
            DinoxModels.parseListNotes(json)
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        val applied = ArrayList<String>()
        result.notes
            .filter { !it.isDel }
            .forEach { remote ->
                val createdAt = DinoxModels.epochMsFromIso(remote.createTimeIso)
                val updatedAt = DinoxModels.epochMsFromIso(remote.updateTimeIso)
                val title = remote.title.ifBlank {
                    if (remote.parentNoteId != null) "Dinox 批注" else "Dinox 笔记"
                }
                // 远端 content 就是 Markdown 正文；若以同名 H1 开头则剥掉，避免与标题字段重复。
                val content = stripLeadingTitle(title, remote.content)
                val note = Note(
                    id = remote.noteId,
                    title = title,
                    body = content,
                    createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
                    updatedAt = if (updatedAt > 0) updatedAt else System.currentTimeMillis(),
                    mdPath = "notes/${remote.noteId}.md",
                )
                val merged = notes.mergeRemoteNote(note, emptyList())
                if (merged) {
                    if (remote.tags.isNotEmpty()) {
                        notes.setNoteTagPaths(note.id, remote.tags)
                    }
                    applied += SearchText.displayTitle(notes.getById(note.id) ?: note)
                }
            }
        // 游标只在整批成功后推进（含空列表 → 推进到服务端返回的最新时间）。
        val latestMs = result.latestUpdateIso?.let { DinoxModels.epochMsFromIso(it) } ?: 0L
        if (latestMs > 0) {
            settingsRepository.update {
                it.copy(dinoxLastSyncAt = maxOf(it.dinoxLastSyncAt, latestMs))
            }
        }
        val scopeLabel = if (boxes.isEmpty()) "全部卡片盒" else "卡片盒 ${boxes.joinToString("、")}"
        val modeLabel = if (lastSyncIso == null) "全量" else "增量"
        Result.success("$modeLabel 同步完成（$scopeLabel）：新增或更新 ${applied.size} 篇")
    }

    // ---- 本地 → Dinox ---------------------------------------------------

    /** 长按卡片「发送到Dinox」：把这篇笔记按 Markdown 发送到 Dinox。 */
    suspend fun sendNote(noteId: String): Result<String> = withContext(Dispatchers.IO) {
        val token = vault.load()
            ?: return@withContext Result.failure(DinoxApiException("尚未保存 Dinox Token，请先测试并保存"))
        val note = notes.getById(noteId)
            ?: return@withContext Result.failure(DinoxApiException("笔记不存在"))
        val settings = settingsRepository.get()
        // 双链标记转成可读文本：Dinox 端没有本地图谱，[[note:id]] 无意义。
        val titles = notes.getAllNotes().associate { it.id to SearchText.displayTitle(it) }
        val content = NoteLinkCodec.renderPlain(
            notes.loadContent(note),
            resolveTitle = { titles[it] },
            missingLabel = "笔记链接",
        )
        val tagPaths = notes.getTagsForNote(note.id).map { it.path }
        val body = DinoxModels.textInputBody(
            content = content,
            title = SearchText.displayTitle(note).take(120),
            tags = tagPaths,
            zettelBoxNames = DinoxModels.splitBoxNames(settings.dinoxSyncBox),
            createdAtIso = DinoxModels.isoFromEpochMs(note.createdAt),
        )
        try {
            client.createNote(token, body)
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        Result.success("已发送到 Dinox${if (tagPaths.isNotEmpty()) "（含 ${tagPaths.size} 个标签）" else ""}")
    }

    companion object {
        /** 若正文以 `# 标题` 开头且与笔记标题相同（或标题为空），剥掉该 H1，只保留正文。 */
        fun stripLeadingTitle(title: String, raw: String): String {
            val content = raw.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
            val lines = content.split("\n")
            val first = lines.firstOrNull() ?: return content
            if (!first.startsWith("# ")) return content
            val heading = first.removePrefix("# ").trim()
            if (heading.isEmpty()) return content
            if (title.isBlank() || heading == title.trim()) {
                return lines.drop(1).joinToString("\n").trimStart('\n')
            }
            return content
        }
    }
}
