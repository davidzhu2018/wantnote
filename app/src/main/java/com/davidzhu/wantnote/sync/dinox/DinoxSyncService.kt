package com.davidzhu.wantnote.sync.dinox

import com.davidzhu.wantnote.data.DinoxBoxDao
import com.davidzhu.wantnote.data.DinoxCachedBox
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.DinoxSyncRuleDao
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteLinkCodec
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** 本地缓存的 Dinox 卡片盒快照（名称列表 + 上次成功拉取时间）。 */
data class DinoxBoxCacheState(
    val names: List<String> = emptyList(),
    val lastFetchedAt: Long = 0L,
)

/**
 * Dinox 生态互联服务（0.11.0）：
 *
 * - Token 测试成功后才加密落盘（Keystore → [DinoxTokenVault]）；
 *   同时生成「可同步密文」（凭据派生密钥 → [DinoxPortableCipher]）存进
 *   设置，随 WebDAV/S3 设置同步分发给其他设备。永远不把 Token 打印或记录到日志/UI。
 * - Dinox 卡片盒缓存（0.11.0 覆盖 0.10.0 M16）：冷启动后台预加载到本地库，
 *   最多每两小时刷新一次；刷新失败保留上次成功缓存。
 * - 从 Dinox 卡片盒导入本机：单向、用户发起。
 * - 卡片盒同步规则（0.11.0 新增）：一对一（可选沿用 Dinox 卡片盒名称）/ 多对一，
 *   支持手动全量/增量或 WorkManager 定时自动同步，规则存 Room 持久化。
 * - 发送到 Dinox：`POST /api/openapi/text/input`。
 */
class DinoxSyncService(
    private val notes: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val vault: DinoxTokenVault,
    private val boxDao: DinoxBoxDao,
    private val ruleDao: DinoxSyncRuleDao,
    private val client: DinoxClient = DinoxClient(),
) {
    private val boxRefreshMutex = Mutex()
    private var inFlightBoxRefresh: CompletableDeferred<Result<List<String>>>? = null

    private val localBoxLookup = object : LocalBoxLookup {
        override suspend fun findById(id: String): NoteBox? = notes.getBoxById(id)
        override suspend fun findByName(name: String): NoteBox? = notes.getBoxByName(name)
        override suspend fun create(name: String): NoteBox? = notes.createBox(name)
    }

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

    /** 清除本机 Token、可同步密文与 Dinox 卡片盒本地缓存。 */
    suspend fun clearToken() = withContext(Dispatchers.IO) {
        vault.clear()
        boxDao.deleteAll()
        settingsRepository.update {
            it.copy(
                dinoxTokenSealed = "",
                dinoxSealMethod = "",
                dinoxBoxesFetchedAt = 0L,
            )
        }
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
            settingsRepository.update { it.copy(dinoxTokenSealed = "", dinoxSealMethod = "") }
            return
        }
        val sealed = DinoxPortableCipher.seal(token, secret) ?: return
        settingsRepository.update {
            it.copy(dinoxTokenSealed = sealed, dinoxSealMethod = it.syncMethod)
        }
    }

    // ---- Dinox 卡片盒本地缓存 + 每两小时刷新（0.11.0） -----------------------

    fun observeCachedBoxes(): Flow<List<String>> {
        return boxDao.observeAll().map { rows -> rows.map { it.name } }
    }

    suspend fun getBoxCacheState(): DinoxBoxCacheState = withContext(Dispatchers.IO) {
        val names = boxDao.getAll().map { it.name }
        val fetchedAt = settingsRepository.get().dinoxBoxesFetchedAt
        DinoxBoxCacheState(names = names, lastFetchedAt = fetchedAt)
    }

    /**
     * 冷启动预加载或按两小时策略按需刷新：
     * - 未保存 Token：直接跳过；
     * - 缓存未满两小时：不发网络请求，直接返回本地缓存；
     * - 从未成功或已满两小时：拉取一次并落库（并发调用自动合并为单次请求）。
     */
    suspend fun preloadBoxesIfNeeded(nowMs: Long = System.currentTimeMillis()): Result<List<String>> {
        if (!hasToken()) return Result.success(emptyList())
        val state = getBoxCacheState()
        if (!DinoxBoxCachePolicy.isStale(state.lastFetchedAt, nowMs)) {
            return Result.success(state.names)
        }
        return refreshRemoteBoxes(nowMs)
    }

    /**
     * 从 Dinox 拉取最新卡片盒名单并持久化到本地数据库。
     * - 并发调用自动复用同一在途请求，避免冷启动预加载与选择器同时重复请求；
     * - 失败时绝不清空本地缓存，也不改动上次成功时间，只返回不含敏感信息的短错误。
     */
    suspend fun refreshRemoteBoxes(nowMs: Long = System.currentTimeMillis()): Result<List<String>> {
        val existing: CompletableDeferred<Result<List<String>>>?
        val created: CompletableDeferred<Result<List<String>>>?
        boxRefreshMutex.withLock {
            val active = inFlightBoxRefresh
            if (active != null && active.isActive) {
                existing = active
                created = null
            } else {
                val deferred = CompletableDeferred<Result<List<String>>>()
                inFlightBoxRefresh = deferred
                existing = null
                created = deferred
            }
        }
        if (existing != null) {
            return existing.await()
        }
        val deferred = created!!
        val outcome = try {
            fetchAndPersistBoxes(nowMs)
        } catch (error: Throwable) {
            Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        boxRefreshMutex.withLock {
            if (inFlightBoxRefresh === deferred) {
                inFlightBoxRefresh = null
            }
        }
        deferred.complete(outcome)
        return outcome
    }

    private suspend fun fetchAndPersistBoxes(nowMs: Long): Result<List<String>> = withContext(Dispatchers.IO) {
        val token = vault.load()
            ?: return@withContext Result.failure(DinoxApiException("尚未保存 Dinox Token，请先测试并保存"))
        val json = try {
            client.listNotes(token, DinoxModels.listNotesBody(lastSyncIso = null))
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        val result = try {
            DinoxModels.parseListNotes(json)
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        val names = result.notes
            .filter { !it.isDel }
            .flatMap { it.zettelBoxes }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        boxDao.replaceAll(names.map { DinoxCachedBox(name = it, fetchedAt = nowMs) })
        settingsRepository.update { it.copy(dinoxBoxesFetchedAt = nowMs) }
        Result.success(names)
    }

    /** 导入/同步顺带发现的新卡片盒名补充进本地表（不推进全量刷新时间戳）。 */
    private suspend fun cacheDiscoveredBoxes(notesList: List<DinoxModels.DinoxNote>, nowMs: Long) {
        val discovered = notesList
            .filter { !it.isDel }
            .flatMap { it.zettelBoxes }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (discovered.isNotEmpty()) {
            boxDao.insertIfAbsent(discovered.map { DinoxCachedBox(name = it, fetchedAt = nowMs) })
        }
    }

    // ---- 单向导入：从 Dinox 卡片盒导入本机 --------------------------------

    /** 从 Dinox 导入时的目标卡片盒策略。 */
    sealed class ImportTarget {
        /** 沿用 Dinox 卡片盒名字：来源盒叫 ABC 就导入本地 ABC（没有就建）。 */
        data object FollowDinoxNames : ImportTarget()

        /** 选中的内容全部进这一个本地卡片盒。 */
        data class LocalBox(val boxId: String) : ImportTarget()
    }

    /**
     * 生态互联单向导入：把用户选中的 Dinox 卡片盒内容一次性导入本机。
     */
    suspend fun importFromBoxes(
        sourceBoxes: List<String>,
        target: ImportTarget,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (sourceBoxes.isEmpty()) {
            return@withContext Result.failure(DinoxApiException("请先选择至少一个 Dinox 卡片盒"))
        }
        val token = vault.load()
            ?: return@withContext Result.failure(DinoxApiException("尚未保存 Dinox Token，请先测试并保存"))
        val body = DinoxModels.listNotesBody(lastSyncIso = null, boxNames = sourceBoxes)
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
        cacheDiscoveredBoxes(result.notes, System.currentTimeMillis())
        var targetBox: NoteBox? = null
        if (target is ImportTarget.LocalBox) {
            targetBox = notes.getBoxById(target.boxId)
                ?: return@withContext Result.failure(DinoxApiException("目标卡片盒不存在，请重新选择"))
        }
        var imported = 0
        result.notes
            .filter { !it.isDel && it.noteId.isNotBlank() }
            .forEach { remote ->
                val merged = mergeDinoxNote(remote)
                if (merged) {
                    val boxId = when (target) {
                        is ImportTarget.LocalBox -> targetBox?.id ?: return@forEach
                        ImportTarget.FollowDinoxNames -> {
                            val name = resolveFollowBoxName(remote.zettelBoxes, sourceBoxes)
                                ?: return@forEach
                            notes.getBoxByName(name)?.id ?: notes.createBox(name)?.id ?: return@forEach
                        }
                    }
                    notes.assignNoteToBox(remote.noteId, boxId)
                    if (remote.tags.isNotEmpty()) {
                        notes.setNoteTagPaths(remote.noteId, remote.tags)
                    }
                    imported += 1
                }
            }
        val label = when (target) {
            is ImportTarget.LocalBox -> "本地卡片盒「${targetBox?.name.orEmpty().ifBlank { "已选卡片盒" }}」"
            ImportTarget.FollowDinoxNames -> "同名本地卡片盒"
        }
        Result.success("已从 ${sourceBoxes.size} 个 Dinox 卡片盒导入 $imported 篇到$label")
    }

    // ---- 0.11.0 卡片盒同步规则（CRUD + 手动全量/增量 + 自动执行） --------------

    fun observeSyncRules(): Flow<List<DinoxSyncRule>> = ruleDao.observeAll()

    suspend fun getSyncRules(): List<DinoxSyncRule> = withContext(Dispatchers.IO) {
        ruleDao.getAll()
    }

    suspend fun getSyncRule(ruleId: String): DinoxSyncRule? = withContext(Dispatchers.IO) {
        ruleDao.getById(ruleId)
    }

    suspend fun saveSyncRule(draft: DinoxSyncRule): Result<DinoxSyncRule> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val withId = if (draft.id.isBlank()) {
            draft.copy(id = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
        } else {
            val existing = ruleDao.getById(draft.id)
            draft.copy(
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
                updatedAt = now,
                lastSyncAt = existing?.lastSyncAt ?: draft.lastSyncAt,
                lastRunAt = existing?.lastRunAt ?: draft.lastRunAt,
                lastStatus = existing?.lastStatus ?: draft.lastStatus,
            )
        }
        val normalized = DinoxSyncRuleTargetResolver.normalize(withId)
        val error = DinoxSyncRuleTargetResolver.validate(normalized)
        if (error != null) {
            return@withContext Result.failure(DinoxApiException(error))
        }
        ruleDao.upsert(normalized)
        Result.success(normalized)
    }

    suspend fun deleteSyncRule(ruleId: String) = withContext(Dispatchers.IO) {
        ruleDao.delete(ruleId)
    }

    /**
     * 按一条「卡片盒同步」规则执行同步（[full] = true 全量；false 增量）。
     * - 增量游标沿用该规则的 [DinoxSyncRule.lastSyncAt]（首次为 0 时等同全量拉取）；
     * - 一对一且启用「沿用 Dinox 卡片盒名称」时：同步时检查本机有没有同名盒，
     *   没有就新建同名盒，有则写入该盒；
     * - 除上述沿用名称情形外，绝不发明新本地盒名。
     */
    suspend fun runSyncRule(ruleId: String, full: Boolean): Result<String> = withContext(Dispatchers.IO) {
        val rule = ruleDao.getById(ruleId)
            ?: return@withContext Result.failure(DinoxApiException("同步规则不存在"))
        val token = vault.load()
            ?: return@withContext recordRuleFailure(rule, "尚未保存 Dinox Token，请先测试并保存")

        val sourceBoxes = rule.sourceBoxes
        if (sourceBoxes.isEmpty()) {
            return@withContext recordRuleFailure(rule, "同步规则未配置 Dinox 来源卡片盒")
        }

        // 非「沿用名称」模式先确认本机目标盒仍存在，避免无意义的网络请求。
        val isFollowName = rule.typeOption == com.davidzhu.wantnote.data.DinoxRuleType.ONE_TO_ONE && rule.followDinoxName
        if (!isFollowName) {
            if (rule.targetBoxId.isBlank() || notes.getBoxById(rule.targetBoxId) == null) {
                return@withContext recordRuleFailure(rule, "目标本机卡片盒不存在，请编辑规则重新选择")
            }
        }

        val lastSyncIso = if (full || rule.lastSyncAt <= 0L) {
            null
        } else {
            DinoxModels.isoFromEpochMs(rule.lastSyncAt)
        }
        val body = DinoxModels.listNotesBody(lastSyncIso = lastSyncIso, boxNames = sourceBoxes)
        val json = try {
            client.listNotes(token, body)
        } catch (error: Throwable) {
            return@withContext recordRuleFailure(rule, dinoxShortMessage(error))
        }
        val parsed = try {
            DinoxModels.parseListNotes(json)
        } catch (error: Throwable) {
            return@withContext recordRuleFailure(rule, dinoxShortMessage(error))
        }
        val nowMs = System.currentTimeMillis()
        cacheDiscoveredBoxes(parsed.notes, nowMs)

        val targetBox = DinoxSyncRuleTargetResolver.resolveTargetBox(rule, localBoxLookup).getOrElse { error ->
            return@withContext recordRuleFailure(rule, dinoxShortMessage(error))
        }

        var syncedCount = 0
        parsed.notes
            .filter { !it.isDel && it.noteId.isNotBlank() }
            .forEach { remote ->
                val merged = mergeDinoxNote(remote)
                if (merged) {
                    notes.assignNoteToBox(remote.noteId, targetBox.id)
                    if (remote.tags.isNotEmpty()) {
                        notes.setNoteTagPaths(remote.noteId, remote.tags)
                    }
                    syncedCount += 1
                }
            }

        val latestMs = parsed.latestUpdateIso?.let { DinoxModels.epochMsFromIso(it) } ?: 0L
        val nextCursor = if (latestMs > 0L) maxOf(rule.lastSyncAt, latestMs) else rule.lastSyncAt
        val modeLabel = if (full) "全量同步" else "增量同步"
        val summary = "$modeLabel 完成：新增或更新 $syncedCount 篇 → 「${targetBox.name}」"
        ruleDao.upsert(
            rule.copy(
                lastSyncAt = nextCursor,
                lastRunAt = nowMs,
                lastStatus = summary,
                updatedAt = nowMs,
            ),
        )
        Result.success(summary)
    }

    private suspend fun recordRuleFailure(rule: DinoxSyncRule, message: String): Result<String> {
        val nowMs = System.currentTimeMillis()
        runCatching {
            ruleDao.upsert(
                rule.copy(
                    lastRunAt = nowMs,
                    lastStatus = "失败：$message",
                    updatedAt = nowMs,
                ),
            )
        }
        return Result.failure(DinoxApiException(message))
    }

    private suspend fun mergeDinoxNote(remote: DinoxModels.DinoxNote): Boolean {
        val createdAt = DinoxModels.epochMsFromIso(remote.createTimeIso)
        val updatedAt = DinoxModels.epochMsFromIso(remote.updateTimeIso)
        val title = remote.title.ifBlank {
            if (remote.parentNoteId != null) "Dinox 批注" else "Dinox 笔记"
        }
        val content = stripLeadingTitle(title, remote.content)
        val note = Note(
            id = remote.noteId,
            title = title,
            body = content,
            createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
            updatedAt = if (updatedAt > 0) updatedAt else System.currentTimeMillis(),
            mdPath = "notes/${remote.noteId}.md",
        )
        return notes.mergeRemoteNote(note, emptyList())
    }

    // ---- 本地 → Dinox ---------------------------------------------------

    /**
     * 长按卡片「发送到Dinox」：把这篇笔记按 Markdown 发送到 Dinox。
     * [zettelBoxName] 是用户在发送对话框里单选的目标 Dinox 卡片盒名；
     * 传 null 表示不放入卡片盒（Dinox 端不归盒）。
     */
    suspend fun sendNote(noteId: String, zettelBoxName: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val token = vault.load()
            ?: return@withContext Result.failure(DinoxApiException("尚未保存 Dinox Token，请先测试并保存"))
        val note = notes.getById(noteId)
            ?: return@withContext Result.failure(DinoxApiException("笔记不存在"))
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
            zettelBoxNames = listOfNotNull(zettelBoxName?.trim()?.takeIf { it.isNotEmpty() }),
            createdAtIso = DinoxModels.isoFromEpochMs(note.createdAt),
        )
        try {
            client.createNote(token, body)
        } catch (error: Throwable) {
            return@withContext Result.failure(DinoxApiException(dinoxShortMessage(error)))
        }
        val boxLabel = zettelBoxName?.trim()?.takeIf { it.isNotEmpty() }?.let { "「$it」" }
        Result.success("已发送到 Dinox$boxLabel${if (tagPaths.isNotEmpty()) "（含 ${tagPaths.size} 个标签）" else ""}")
    }

    companion object {
        /**
         * 「沿用 Dinox 卡片盒名字」时，决定一篇远端笔记应落入哪个来源盒名。
         * 只从用户选中的来源盒里挑：取笔记 zettelBoxes 中第一个命中的选中盒；
         * 若没有交集但只选了一个来源盒，则用该来源盒（应对 API 过滤后未回传盒名的情况）。
         * 绝不使用未选中的 Dinox 盒名，避免在本机误建/误导入到其他盒子。
         */
        fun resolveFollowBoxName(remoteBoxes: List<String>, sourceBoxes: List<String>): String? {
            val selected = sourceBoxes.map { it.trim() }.filter { it.isNotBlank() }
            if (selected.isEmpty()) return null
            val selectedSet = selected.toSet()
            return remoteBoxes.map { it.trim() }.firstOrNull { it.isNotBlank() && it in selectedSet }
                ?: selected.singleOrNull()
        }

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
