package com.davidzhu.wantnote.sync

import android.content.Context
import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.DinoxCachedBox
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteDatabase
import com.davidzhu.wantnote.data.NoteLink
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteTagCrossRef
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.data.SyncMethod
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.sync.dinox.DinoxBoxSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uploads notes, tags, boxes, links, attachments, custom fonts and all user
 * settings (theme, custom themes, editor preferences, AI providers, Dinox sync
 * rules & cached boxes, sealed Dinox token) to a dedicated `wantnote/` folder
 * on the selected remote (WebDAV or S3), and downloads them back with a
 * last-write merge on [Note.updatedAt].
 *
 * WebDAV / S3 credentials themselves stay in the local Room settings row and
 * are never written to the remote payload or overwritten during download.
 */
class CloudSyncService(
    private val notes: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val media: MediaStorage,
    private val dinox: com.davidzhu.wantnote.sync.dinox.DinoxSyncService? = null,
    private val db: NoteDatabase? = null,
    private val context: Context? = null,
) {
    suspend fun testAndSave(draft: AppSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            validate(draft)
            clientFor(draft).test()
            settingsRepository.update { current ->
                current.copy(
                    syncMethod = draft.syncMethod,
                    webdavUrl = draft.webdavUrl.trim(),
                    webdavUsername = draft.webdavUsername.trim(),
                    webdavPassword = draft.webdavPassword,
                    s3Endpoint = draft.s3Endpoint.trim(),
                    s3Bucket = draft.s3Bucket.trim(),
                    s3Region = draft.s3Region.trim(),
                    s3AccessKey = draft.s3AccessKey.trim(),
                    s3SecretKey = draft.s3SecretKey,
                    s3Prefix = draft.s3Prefix.trim(),
                )
            }
            "已测试并保存"
        }.recoverCatching { error ->
            throw SyncException(safe(error, draft))
        }
    }

    suspend fun upload(): Result<String> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.get()
        runCatching {
            validate(settings)
            val backend = clientFor(settings)
            backend.ensureAppFolder()
            val snapshot = exportSnapshot()
            snapshot.attachments.forEach { item ->
                val file = media.fileFor(item.relativePath)
                if (file.exists()) {
                    backend.putFile("wantnote/${item.relativePath}", file)
                }
            }
            snapshot.fonts.forEach { item ->
                val file = media.fileFor(item.relativePath)
                if (file.exists()) {
                    backend.putFile("wantnote/${item.relativePath}", file)
                }
            }
            backend.putText("wantnote/manifest.json", snapshot.toJson())
            "已上传 ${snapshot.notes.size} 条笔记、${snapshot.boxes.size} 个卡片盒、${snapshot.attachments.size} 个附件与全部用户配置"
        }.recoverCatching { error ->
            throw SyncException(safe(error, settings))
        }
    }

    suspend fun download(): Result<String> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.get()
        runCatching {
            validate(settings)
            val backend = clientFor(settings)
            val text = backend.getText("wantnote/manifest.json")
            val snapshot = SyncSnapshot.fromJson(text)

            // 1. 先合并/创建远端卡片盒，建立 remoteBoxId -> localBoxId 映射。
            val boxIdMap = mutableMapOf<String, String>()
            snapshot.boxes.forEach { remoteBox ->
                val localBox = notes.mergeRemoteBox(remoteBox)
                boxIdMap[remoteBox.id] = localBox.id
            }

            // 2. 合并笔记与块（携带映射后的卡片盒归属）。
            var applied = 0
            val blocksByNote = snapshot.blocks.groupBy { it.noteId }
            snapshot.notes.forEach { remote ->
                val mappedBoxId = resolveMappedBoxId(
                    rawBoxId = remote.boxId,
                    fallbackName = snapshot.noteBoxNames[remote.id].orEmpty(),
                    boxIdMap = boxIdMap,
                    exportedAt = snapshot.exportedAt,
                )
                val used = notes.mergeRemoteNote(
                    remote.copy(boxId = mappedBoxId),
                    blocksByNote[remote.id].orEmpty(),
                )
                if (used) applied += 1
            }
            runCatching { notes.ensureMarkdownFiles() }

            // 3. 合并双链（note_links）。
            if (snapshot.noteLinks.isNotEmpty() && db != null) {
                val existingIds = notes.getAllNotes().mapTo(HashSet()) { it.id }
                val validLinks = snapshot.noteLinks.filter {
                    it.sourceNoteId in existingIds && it.targetNoteId in existingIds
                }
                if (validLinks.isNotEmpty()) {
                    db.noteLinkDao().insertAll(validLinks)
                }
            }

            // 4. 恢复全部用户配置（主题、自定义主题、字号、日夜、编辑器、字体、压缩、AI 模型选择、Dinox 密文等），
            //    严格保留本机的 WebDAV / S3 连接地址与凭据不被覆盖。
            val remoteSettings = snapshot.userSettings
            if (remoteSettings != null || snapshot.dinoxTokenSealed.isNotBlank()) {
                settingsRepository.update { current ->
                    val merged = if (remoteSettings != null) {
                        remoteSettings.applyOnto(current)
                    } else {
                        current
                    }
                    if (snapshot.dinoxTokenSealed.isNotBlank()) {
                        merged.copy(
                            dinoxTokenSealed = snapshot.dinoxTokenSealed,
                            dinoxSealMethod = snapshot.dinoxSealMethod,
                        )
                    } else {
                        merged
                    }
                }
                if (snapshot.dinoxTokenSealed.isNotBlank()) {
                    runCatching { dinox?.importSealedToken() }
                }
            }

            // 5. 恢复 AI 提供商配置（设置 → AI设置）。
            if (snapshot.aiProviders.isNotEmpty() && db != null) {
                val dao = db.aiProviderDao()
                snapshot.aiProviders.forEach { provider -> dao.upsert(provider) }
            }

            // 6. 恢复生态互联卡片盒同步规则与 Dinox 卡片盒缓存，并重建自动同步调度。
            if (snapshot.dinoxCachedBoxes.isNotEmpty() && db != null) {
                db.dinoxBoxDao().upsertAll(snapshot.dinoxCachedBoxes)
            }
            if (snapshot.dinoxSyncRules.isNotEmpty() && db != null) {
                val mappedRules = snapshot.dinoxSyncRules.map { rule ->
                    if (rule.followDinoxName) {
                        rule.copy(targetBoxId = "")
                    } else {
                        val mappedTarget = resolveMappedBoxId(
                            rawBoxId = rule.targetBoxId,
                            fallbackName = snapshot.ruleTargetBoxNames[rule.id].orEmpty(),
                            boxIdMap = boxIdMap,
                            exportedAt = snapshot.exportedAt,
                        )
                        rule.copy(targetBoxId = mappedTarget)
                    }
                }
                db.dinoxSyncRuleDao().upsertAll(mappedRules)
                context?.let { ctx ->
                    runCatching {
                        DinoxBoxSyncWorker.syncAllSchedules(ctx, db.dinoxSyncRuleDao().getAll())
                    }
                }
            }

            // 7. 合并标签与笔记-标签关联。
            val idMap = mutableMapOf<String, String>()
            snapshot.tags.forEach { remote ->
                val local = notes.mergeRemoteTag(remote)
                idMap[remote.id] = local.id
            }
            snapshot.noteTags.forEach { ref ->
                val localTagId = idMap[ref.tagId] ?: ref.tagId
                notes.attachTag(ref.noteId, localTagId)
            }

            // 8. 下载附件与自定义字体。
            var downloaded = 0
            (snapshot.attachments + snapshot.fonts).forEach { item ->
                val dest = media.fileFor(item.relativePath)
                val need = !dest.exists() || dest.length() != item.size
                if (need) {
                    dest.parentFile?.mkdirs()
                    val tmp = File(dest.parentFile, dest.name + ".part")
                    backend.getFile("wantnote/${item.relativePath}", tmp)
                    if (dest.exists()) dest.delete()
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    downloaded += 1
                }
            }
            "已下载合并：更新或新增 $applied 条笔记、${snapshot.boxes.size} 个卡片盒，写入 $downloaded 个文件并恢复用户配置（未提及的本地笔记已保留）"
        }.recoverCatching { error ->
            throw SyncException(safe(error, settings))
        }
    }

    private suspend fun resolveMappedBoxId(
        rawBoxId: String,
        fallbackName: String,
        boxIdMap: MutableMap<String, String>,
        exportedAt: Long,
    ): String {
        val cleanId = rawBoxId.trim()
        val cleanName = fallbackName.trim()
        return when {
            cleanId.isNotEmpty() && boxIdMap.containsKey(cleanId) -> boxIdMap.getValue(cleanId)
            cleanName.isNotEmpty() -> {
                val localBox = notes.mergeRemoteBox(
                    NoteBox(id = cleanId, name = cleanName, createdAt = exportedAt),
                )
                if (cleanId.isNotEmpty()) boxIdMap[cleanId] = localBox.id
                localBox.id
            }
            else -> cleanId
        }
    }

    private suspend fun exportSnapshot(): SyncSnapshot {
        val settings = settingsRepository.get()
        val allNotes = notes.getAllNotes()
        val allBlocks = notes.getAllBlocks()
        val allTags = notes.getAllTags()
        val refs = notes.getAllCrossRefs()
        val allLinks = notes.getAllLinks()
        val allBoxes = notes.getAllBoxes()
        val boxNameById = allBoxes.associate { it.id to it.name }
        val allAiProviders = db?.aiProviderDao()?.getAll().orEmpty()
        val allSyncRules = db?.dinoxSyncRuleDao()?.getAll().orEmpty()
        val allDinoxBoxes = db?.dinoxBoxDao()?.getAll().orEmpty()

        val referenced = allBlocks.map { it.mediaPath }.filter { it.isNotBlank() }.toSet()
        val attachments = referenced.mapNotNull { path ->
            val file = media.fileFor(path)
            if (!file.exists()) return@mapNotNull null
            SyncAttachment(
                relativePath = path,
                size = file.length(),
                sha256 = media.sha256(path),
            )
        }
        val fontPaths = LinkedHashSet<String>()
        media.fontsDir().listFiles()?.filter { it.isFile }?.forEach { file ->
            fontPaths += "fonts/${file.name}"
        }
        if (settings.customFontPath.isNotBlank()) {
            fontPaths += settings.customFontPath
        }
        val fonts = fontPaths.mapNotNull { path ->
            val file = media.fileFor(path)
            if (!file.exists() || !file.isFile) return@mapNotNull null
            SyncAttachment(
                relativePath = path,
                size = file.length(),
                sha256 = media.sha256(path),
            )
        }

        return SyncSnapshot(
            exportedAt = System.currentTimeMillis(),
            notes = allNotes,
            blocks = allBlocks,
            tags = allTags,
            noteTags = refs,
            noteLinks = allLinks,
            attachments = attachments,
            fonts = fonts,
            boxes = allBoxes,
            noteBoxNames = allNotes.associate { it.id to boxNameById[it.boxId].orEmpty() },
            aiProviders = allAiProviders,
            dinoxSyncRules = allSyncRules,
            ruleTargetBoxNames = allSyncRules.associate { it.id to boxNameById[it.targetBoxId].orEmpty() },
            dinoxCachedBoxes = allDinoxBoxes,
            userSettings = SyncUserSettings.from(settings),
            dinoxTokenSealed = settings.dinoxTokenSealed,
            dinoxSealMethod = settings.dinoxSealMethod,
        )
    }

    private fun validate(settings: AppSettings) {
        when (settings.syncMethodOption) {
            SyncMethod.WEBDAV -> {
                if (settings.webdavUrl.isBlank()) throw SyncException("请先填写 WebDAV 服务器地址")
                if (settings.webdavUsername.isBlank() && settings.webdavPassword.isBlank()) {
                    throw SyncException("请先填写 WebDAV 用户名和密码")
                }
            }
            SyncMethod.S3 -> {
                if (settings.s3Endpoint.isBlank()) throw SyncException("请先填写 S3 Endpoint")
                if (settings.s3Bucket.isBlank()) throw SyncException("请先填写 S3 Bucket")
                if (settings.s3AccessKey.isBlank() || settings.s3SecretKey.isBlank()) {
                    throw SyncException("请先填写 S3 Access Key 和 Secret Key")
                }
            }
        }
    }

    private fun clientFor(settings: AppSettings): RemoteBackend {
        return when (settings.syncMethodOption) {
            SyncMethod.WEBDAV -> WebDavBackend(
                WebDavClient(settings.webdavUrl, settings.webdavUsername, settings.webdavPassword),
            )
            SyncMethod.S3 -> S3Backend(
                S3Client(
                    endpoint = settings.s3Endpoint,
                    bucket = settings.s3Bucket,
                    region = settings.s3Region,
                    accessKey = settings.s3AccessKey,
                    secretKey = settings.s3SecretKey,
                    prefix = settings.s3Prefix,
                ),
            )
        }
    }

    private fun safe(error: Throwable, settings: AppSettings): String {
        val secrets = listOf(
            settings.webdavPassword,
            settings.s3AccessKey,
            settings.s3SecretKey,
            settings.webdavUsername,
        )
        return Redacting.message(error, secrets)
    }
}

private interface RemoteBackend {
    fun test()
    fun ensureAppFolder()
    fun putFile(relative: String, file: File)
    fun putText(relative: String, text: String)
    fun getText(relative: String): String
    fun getFile(relative: String, dest: File)
}

private class WebDavBackend(private val client: WebDavClient) : RemoteBackend {
    override fun test() = client.test()
    override fun ensureAppFolder() {
        client.ensureFolder("wantnote")
        client.ensureFolder("wantnote/media")
        client.ensureFolder("wantnote/fonts")
    }
    override fun putFile(relative: String, file: File) = client.put(relative, file)
    override fun putText(relative: String, text: String) = client.putText(relative, text)
    override fun getText(relative: String): String = client.getText(relative)
    override fun getFile(relative: String, dest: File) = client.getToFile(relative, dest)
}

private class S3Backend(private val client: S3Client) : RemoteBackend {
    override fun test() = client.test()
    override fun ensureAppFolder() {
        // S3 has no real folders; keys create the prefix on upload.
    }
    override fun putFile(relative: String, file: File) = client.putFile(relative, file)
    override fun putText(relative: String, text: String) = client.putText(relative, text)
    override fun getText(relative: String): String = client.getText(relative)
    override fun getFile(relative: String, dest: File) = client.getToFile(relative, dest)
}

internal data class SyncAttachment(
    val relativePath: String,
    val size: Long,
    val sha256: String,
)

/**
 * 0.14.0：可随云同步分发的用户设置子集（覆盖通用设置、主题/自建主题、编辑器设置、
 * 附件压缩、AI 默认模型、Dinox 参数），刻意不含 WebDAV/S3 凭据本身。
 */
internal data class SyncUserSettings(
    val themeName: String = AppSettings.DEFAULT.themeName,
    val fontScale: String = AppSettings.DEFAULT.fontScale,
    val dayNight: String = AppSettings.DEFAULT.dayNight,
    val compressImages: Boolean = AppSettings.DEFAULT.compressImages,
    val compressVideos: Boolean = AppSettings.DEFAULT.compressVideos,
    val homeView: String = AppSettings.DEFAULT.homeView,
    val editorFontId: String = AppSettings.DEFAULT.editorFontId,
    val customFontPath: String = AppSettings.DEFAULT.customFontPath,
    val editorMode: String = AppSettings.DEFAULT.editorMode,
    val themeCustomJson: String = AppSettings.DEFAULT.themeCustomJson,
    val aiSummaryProviderId: String = AppSettings.DEFAULT.aiSummaryProviderId,
    val aiSummaryModelId: String = AppSettings.DEFAULT.aiSummaryModelId,
    val aiTitleTagProviderId: String = AppSettings.DEFAULT.aiTitleTagProviderId,
    val aiTitleTagModelId: String = AppSettings.DEFAULT.aiTitleTagModelId,
    val aiSummaryText: String = AppSettings.DEFAULT.aiSummaryText,
    val aiSummaryUpdatedAt: Long = AppSettings.DEFAULT.aiSummaryUpdatedAt,
    val aiSummaryScope: String = AppSettings.DEFAULT.aiSummaryScope,
    val dinoxLastSyncAt: Long = AppSettings.DEFAULT.dinoxLastSyncAt,
    val dinoxSyncBox: String = AppSettings.DEFAULT.dinoxSyncBox,
    val dinoxAutoSyncMinutes: Int = AppSettings.DEFAULT.dinoxAutoSyncMinutes,
    val dinoxBoxesFetchedAt: Long = AppSettings.DEFAULT.dinoxBoxesFetchedAt,
    val aiEnableTitle: Boolean = AppSettings.DEFAULT.aiEnableTitle,
    val aiEnableTag: Boolean = AppSettings.DEFAULT.aiEnableTag,
    val aiEnableBox: Boolean = AppSettings.DEFAULT.aiEnableBox,
    val aiEnableSummary: Boolean = AppSettings.DEFAULT.aiEnableSummary,
    val aiEnableInsight: Boolean = AppSettings.DEFAULT.aiEnableInsight,
    val autoCheckUpdate: Boolean = AppSettings.DEFAULT.autoCheckUpdate,
    val profileNickname: String = AppSettings.DEFAULT.profileNickname,
    val profileAvatarPath: String = AppSettings.DEFAULT.profileAvatarPath,
    val heatmapColorHex: String = AppSettings.DEFAULT.heatmapColorHex,
    val uiStyle: String = AppSettings.DEFAULT.uiStyle,
) {
    fun applyOnto(local: AppSettings): AppSettings = local.copy(
        themeName = themeName,
        fontScale = fontScale,
        dayNight = dayNight,
        compressImages = compressImages,
        compressVideos = compressVideos,
        homeView = homeView,
        editorFontId = editorFontId,
        customFontPath = customFontPath,
        editorMode = editorMode,
        themeCustomJson = themeCustomJson,
        aiSummaryProviderId = aiSummaryProviderId,
        aiSummaryModelId = aiSummaryModelId,
        aiTitleTagProviderId = aiTitleTagProviderId,
        aiTitleTagModelId = aiTitleTagModelId,
        aiSummaryText = aiSummaryText,
        aiSummaryUpdatedAt = aiSummaryUpdatedAt,
        aiSummaryScope = aiSummaryScope,
        dinoxLastSyncAt = maxOf(local.dinoxLastSyncAt, dinoxLastSyncAt),
        dinoxSyncBox = dinoxSyncBox,
        dinoxAutoSyncMinutes = dinoxAutoSyncMinutes,
        dinoxBoxesFetchedAt = maxOf(local.dinoxBoxesFetchedAt, dinoxBoxesFetchedAt),
        aiEnableTitle = aiEnableTitle,
        aiEnableTag = aiEnableTag,
        aiEnableBox = aiEnableBox,
        aiEnableSummary = aiEnableSummary,
        aiEnableInsight = aiEnableInsight,
        autoCheckUpdate = autoCheckUpdate,
        profileNickname = profileNickname,
        profileAvatarPath = profileAvatarPath,
        heatmapColorHex = heatmapColorHex,
        uiStyle = uiStyle,
    )

    fun toJsonObject(): JSONObject = JSONObject()
        .put("themeName", themeName)
        .put("fontScale", fontScale)
        .put("dayNight", dayNight)
        .put("compressImages", compressImages)
        .put("compressVideos", compressVideos)
        .put("homeView", homeView)
        .put("editorFontId", editorFontId)
        .put("customFontPath", customFontPath)
        .put("editorMode", editorMode)
        .put("themeCustomJson", themeCustomJson)
        .put("aiSummaryProviderId", aiSummaryProviderId)
        .put("aiSummaryModelId", aiSummaryModelId)
        .put("aiTitleTagProviderId", aiTitleTagProviderId)
        .put("aiTitleTagModelId", aiTitleTagModelId)
        .put("aiSummaryText", aiSummaryText)
        .put("aiSummaryUpdatedAt", aiSummaryUpdatedAt)
        .put("aiSummaryScope", aiSummaryScope)
        .put("dinoxLastSyncAt", dinoxLastSyncAt)
        .put("dinoxSyncBox", dinoxSyncBox)
        .put("dinoxAutoSyncMinutes", dinoxAutoSyncMinutes)
        .put("dinoxBoxesFetchedAt", dinoxBoxesFetchedAt)
        .put("aiEnableTitle", aiEnableTitle)
        .put("aiEnableTag", aiEnableTag)
        .put("aiEnableBox", aiEnableBox)
        .put("aiEnableSummary", aiEnableSummary)
        .put("aiEnableInsight", aiEnableInsight)
        .put("autoCheckUpdate", autoCheckUpdate)
        .put("profileNickname", profileNickname)
        .put("profileAvatarPath", profileAvatarPath)
        .put("heatmapColorHex", heatmapColorHex)
        .put("uiStyle", uiStyle)

    companion object {
        fun from(settings: AppSettings): SyncUserSettings = SyncUserSettings(
            themeName = settings.themeName,
            fontScale = settings.fontScale,
            dayNight = settings.dayNight,
            compressImages = settings.compressImages,
            compressVideos = settings.compressVideos,
            homeView = settings.homeView,
            editorFontId = settings.editorFontId,
            customFontPath = settings.customFontPath,
            editorMode = settings.editorMode,
            themeCustomJson = settings.themeCustomJson,
            aiSummaryProviderId = settings.aiSummaryProviderId,
            aiSummaryModelId = settings.aiSummaryModelId,
            aiTitleTagProviderId = settings.aiTitleTagProviderId,
            aiTitleTagModelId = settings.aiTitleTagModelId,
            aiSummaryText = settings.aiSummaryText,
            aiSummaryUpdatedAt = settings.aiSummaryUpdatedAt,
            aiSummaryScope = settings.aiSummaryScope,
            dinoxLastSyncAt = settings.dinoxLastSyncAt,
            dinoxSyncBox = settings.dinoxSyncBox,
            dinoxAutoSyncMinutes = settings.dinoxAutoSyncMinutes,
            dinoxBoxesFetchedAt = settings.dinoxBoxesFetchedAt,
            aiEnableTitle = settings.aiEnableTitle,
            aiEnableTag = settings.aiEnableTag,
            aiEnableBox = settings.aiEnableBox,
            aiEnableSummary = settings.aiEnableSummary,
            aiEnableInsight = settings.aiEnableInsight,
            autoCheckUpdate = settings.autoCheckUpdate,
            profileNickname = settings.profileNickname,
            profileAvatarPath = settings.profileAvatarPath,
            heatmapColorHex = settings.heatmapColorHex,
            uiStyle = settings.uiStyle,
        )

        fun fromJsonObject(obj: JSONObject): SyncUserSettings {
            val d = AppSettings.DEFAULT
            return SyncUserSettings(
                themeName = obj.optString("themeName", d.themeName),
                fontScale = obj.optString("fontScale", d.fontScale),
                dayNight = obj.optString("dayNight", d.dayNight),
                compressImages = obj.optBoolean("compressImages", d.compressImages),
                compressVideos = obj.optBoolean("compressVideos", d.compressVideos),
                homeView = obj.optString("homeView", d.homeView),
                editorFontId = obj.optString("editorFontId", d.editorFontId),
                customFontPath = obj.optString("customFontPath", d.customFontPath),
                editorMode = obj.optString("editorMode", d.editorMode),
                themeCustomJson = obj.optString("themeCustomJson", d.themeCustomJson),
                aiSummaryProviderId = obj.optString("aiSummaryProviderId", d.aiSummaryProviderId),
                aiSummaryModelId = obj.optString("aiSummaryModelId", d.aiSummaryModelId),
                aiTitleTagProviderId = obj.optString("aiTitleTagProviderId", d.aiTitleTagProviderId),
                aiTitleTagModelId = obj.optString("aiTitleTagModelId", d.aiTitleTagModelId),
                aiSummaryText = obj.optString("aiSummaryText", d.aiSummaryText),
                aiSummaryUpdatedAt = obj.optLong("aiSummaryUpdatedAt", d.aiSummaryUpdatedAt),
                aiSummaryScope = obj.optString("aiSummaryScope", d.aiSummaryScope),
                dinoxLastSyncAt = obj.optLong("dinoxLastSyncAt", d.dinoxLastSyncAt),
                dinoxSyncBox = obj.optString("dinoxSyncBox", d.dinoxSyncBox),
                dinoxAutoSyncMinutes = obj.optInt("dinoxAutoSyncMinutes", d.dinoxAutoSyncMinutes),
                dinoxBoxesFetchedAt = obj.optLong("dinoxBoxesFetchedAt", d.dinoxBoxesFetchedAt),
                aiEnableTitle = obj.optBoolean("aiEnableTitle", d.aiEnableTitle),
                aiEnableTag = obj.optBoolean("aiEnableTag", d.aiEnableTag),
                aiEnableBox = obj.optBoolean("aiEnableBox", d.aiEnableBox),
                aiEnableSummary = obj.optBoolean("aiEnableSummary", d.aiEnableSummary),
                aiEnableInsight = obj.optBoolean("aiEnableInsight", d.aiEnableInsight),
                autoCheckUpdate = obj.optBoolean("autoCheckUpdate", d.autoCheckUpdate),
                profileNickname = obj.optString("profileNickname", d.profileNickname),
                profileAvatarPath = obj.optString("profileAvatarPath", d.profileAvatarPath),
                heatmapColorHex = obj.optString("heatmapColorHex", d.heatmapColorHex),
                uiStyle = obj.optString("uiStyle", d.uiStyle),
            )
        }
    }
}

internal data class SyncSnapshot(
    val exportedAt: Long,
    val notes: List<Note>,
    val blocks: List<NoteBlock>,
    val tags: List<Tag>,
    val noteTags: List<NoteTagCrossRef>,
    val attachments: List<SyncAttachment>,
    val noteLinks: List<NoteLink> = emptyList(),
    val fonts: List<SyncAttachment> = emptyList(),
    /** 0.14.0：随 WebDAV / S3 同步的卡片盒清单与笔记盒名兜底映射。 */
    val boxes: List<NoteBox> = emptyList(),
    val noteBoxNames: Map<String, String> = emptyMap(),
    /** 0.14.0：AI 提供商配置、生态互联卡片盒同步规则、Dinox 卡片盒缓存与全部用户设置。 */
    val aiProviders: List<AiProvider> = emptyList(),
    val dinoxSyncRules: List<DinoxSyncRule> = emptyList(),
    val ruleTargetBoxNames: Map<String, String> = emptyMap(),
    val dinoxCachedBoxes: List<DinoxCachedBox> = emptyList(),
    val userSettings: SyncUserSettings? = null,
    /** 0.9.0：Dinox Token 的可同步密文（凭据派生密钥加密；明文绝不入快照）。 */
    val dinoxTokenSealed: String = "",
    val dinoxSealMethod: String = "",
) {
    fun toJson(): String {
        val boxNameById = boxes.associate { it.id to it.name }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("exportedAt", exportedAt)
            .put("dinoxTokenSealed", dinoxTokenSealed)
            .put("dinoxSealMethod", dinoxSealMethod)
        if (userSettings != null) {
            root.put("userSettings", userSettings.toJsonObject())
        }
        root.put(
            "notes",
            JSONArray().also { arr ->
                notes.forEach { note ->
                    val boxName = noteBoxNames[note.id].orEmpty().ifBlank { boxNameById[note.boxId].orEmpty() }
                    arr.put(
                        JSONObject()
                            .put("id", note.id)
                            .put("title", note.title)
                            .put("body", note.body)
                            .put("mdPath", note.mdPath)
                            .put("boxId", note.boxId)
                            .put("boxName", boxName)
                            .put("createdAt", note.createdAt)
                            .put("updatedAt", note.updatedAt),
                    )
                }
            },
        )
        root.put(
            "blocks",
            JSONArray().also { arr ->
                blocks.forEach { block ->
                    arr.put(
                        JSONObject()
                            .put("id", block.id)
                            .put("noteId", block.noteId)
                            .put("type", block.type)
                            .put("text", block.text)
                            .put("indent", block.indent)
                            .put("checked", block.checked)
                            .put("fontSize", block.fontSize)
                            .put("mediaPath", block.mediaPath)
                            .put("sortOrder", block.sortOrder)
                            .put("headingLevel", block.headingLevel),
                    )
                }
            },
        )
        root.put(
            "tags",
            JSONArray().also { arr ->
                tags.forEach { tag ->
                    arr.put(
                        JSONObject()
                            .put("id", tag.id)
                            .put("path", tag.path)
                            .put("color", tag.color)
                            .put("createdAt", tag.createdAt),
                    )
                }
            },
        )
        root.put(
            "noteTags",
            JSONArray().also { arr ->
                noteTags.forEach { ref ->
                    arr.put(JSONObject().put("noteId", ref.noteId).put("tagId", ref.tagId))
                }
            },
        )
        root.put(
            "noteLinks",
            JSONArray().also { arr ->
                noteLinks.forEach { link ->
                    arr.put(
                        JSONObject()
                            .put("sourceNoteId", link.sourceNoteId)
                            .put("targetNoteId", link.targetNoteId)
                            .put("createdAt", link.createdAt),
                    )
                }
            },
        )
        root.put(
            "boxes",
            JSONArray().also { arr ->
                boxes.forEach { box ->
                    arr.put(
                        JSONObject()
                            .put("id", box.id)
                            .put("name", box.name)
                            .put("createdAt", box.createdAt),
                    )
                }
            },
        )
        root.put(
            "aiProviders",
            JSONArray().also { arr ->
                aiProviders.forEach { p ->
                    arr.put(
                        JSONObject()
                            .put("id", p.id)
                            .put("presetId", p.presetId)
                            .put("name", p.name)
                            .put("baseUrl", p.baseUrl)
                            .put("apiKey", p.apiKey)
                            .put("availableModelsJson", p.availableModelsJson)
                            .put("enabledModelsJson", p.enabledModelsJson)
                            .put("updatedAt", p.updatedAt),
                    )
                }
            },
        )
        root.put(
            "dinoxSyncRules",
            JSONArray().also { arr ->
                dinoxSyncRules.forEach { rule ->
                    val targetName = ruleTargetBoxNames[rule.id].orEmpty().ifBlank { boxNameById[rule.targetBoxId].orEmpty() }
                    arr.put(
                        JSONObject()
                            .put("id", rule.id)
                            .put("ruleType", rule.ruleType)
                            .put("sourceBoxesJson", rule.sourceBoxesJson)
                            .put("targetBoxId", rule.targetBoxId)
                            .put("targetBoxName", targetName)
                            .put("followDinoxName", rule.followDinoxName)
                            .put("syncMode", rule.syncMode)
                            .put("autoIntervalMinutes", rule.autoIntervalMinutes)
                            .put("lastSyncAt", rule.lastSyncAt)
                            .put("lastRunAt", rule.lastRunAt)
                            .put("lastStatus", rule.lastStatus)
                            .put("createdAt", rule.createdAt)
                            .put("updatedAt", rule.updatedAt),
                    )
                }
            },
        )
        root.put(
            "dinoxCachedBoxes",
            JSONArray().also { arr ->
                dinoxCachedBoxes.forEach { box ->
                    arr.put(
                        JSONObject()
                            .put("name", box.name)
                            .put("fetchedAt", box.fetchedAt)
                            .put("remoteId", box.remoteId),
                    )
                }
            },
        )
        root.put("attachments", attachmentsToJson(attachments))
        root.put("fonts", attachmentsToJson(fonts))
        return root.toString()
    }

    private fun attachmentsToJson(items: List<SyncAttachment>): JSONArray = JSONArray().also { arr ->
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("relativePath", item.relativePath)
                    .put("size", item.size)
                    .put("sha256", item.sha256),
            )
        }
    }

    companion object {
        const val FORMAT = "wantnote-sync-v1"

        fun fromJson(text: String): SyncSnapshot {
            val root = JSONObject(text)
            val noteBoxNames = LinkedHashMap<String, String>()
            val notes = root.optJSONArray("notes").orEmpty().mapObjects { obj ->
                val id = obj.getString("id")
                val boxName = obj.optString("boxName")
                if (boxName.isNotBlank()) noteBoxNames[id] = boxName
                Note(
                    id = id,
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    mdPath = obj.optString("mdPath"),
                    boxId = obj.optString("boxId"),
                )
            }
            val blocks = root.optJSONArray("blocks").orEmpty().mapObjects { obj ->
                NoteBlock(
                    id = obj.getString("id"),
                    noteId = obj.getString("noteId"),
                    type = obj.optString("type"),
                    text = obj.optString("text"),
                    indent = obj.optInt("indent"),
                    checked = obj.optBoolean("checked"),
                    fontSize = obj.optString("fontSize", "NORMAL"),
                    mediaPath = obj.optString("mediaPath"),
                    sortOrder = obj.optInt("sortOrder"),
                    headingLevel = obj.optInt("headingLevel"),
                )
            }
            val tags = root.optJSONArray("tags").orEmpty().mapObjects { obj ->
                Tag(
                    id = obj.getString("id"),
                    path = obj.optString("path"),
                    color = obj.optLong("color", Tag.defaultColor()),
                    createdAt = obj.optLong("createdAt"),
                )
            }
            val refs = root.optJSONArray("noteTags").orEmpty().mapObjects { obj ->
                NoteTagCrossRef(noteId = obj.getString("noteId"), tagId = obj.getString("tagId"))
            }
            val noteLinks = root.optJSONArray("noteLinks").orEmpty().mapObjects { obj ->
                NoteLink(
                    sourceNoteId = obj.getString("sourceNoteId"),
                    targetNoteId = obj.getString("targetNoteId"),
                    createdAt = obj.optLong("createdAt"),
                )
            }
            val boxes = root.optJSONArray("boxes").orEmpty().mapObjects { obj ->
                NoteBox(
                    id = obj.getString("id"),
                    name = obj.optString("name"),
                    createdAt = obj.optLong("createdAt"),
                )
            }
            val aiProviders = root.optJSONArray("aiProviders").orEmpty().mapObjects { obj ->
                AiProvider(
                    id = obj.getString("id"),
                    presetId = obj.optString("presetId"),
                    name = obj.optString("name"),
                    baseUrl = obj.optString("baseUrl"),
                    apiKey = obj.optString("apiKey"),
                    availableModelsJson = obj.optString("availableModelsJson", "[]"),
                    enabledModelsJson = obj.optString("enabledModelsJson", "[]"),
                    updatedAt = obj.optLong("updatedAt"),
                )
            }
            val ruleTargetBoxNames = LinkedHashMap<String, String>()
            val dinoxSyncRules = root.optJSONArray("dinoxSyncRules").orEmpty().mapObjects { obj ->
                val id = obj.getString("id")
                val targetName = obj.optString("targetBoxName")
                if (targetName.isNotBlank()) ruleTargetBoxNames[id] = targetName
                DinoxSyncRule(
                    id = id,
                    ruleType = obj.optString("ruleType"),
                    sourceBoxesJson = obj.optString("sourceBoxesJson", "[]"),
                    targetBoxId = obj.optString("targetBoxId"),
                    followDinoxName = obj.optBoolean("followDinoxName", false),
                    syncMode = obj.optString("syncMode"),
                    autoIntervalMinutes = obj.optInt("autoIntervalMinutes", 0),
                    lastSyncAt = obj.optLong("lastSyncAt", 0L),
                    lastRunAt = obj.optLong("lastRunAt", 0L),
                    lastStatus = obj.optString("lastStatus"),
                    createdAt = obj.optLong("createdAt", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L),
                )
            }
            val dinoxCachedBoxes = root.optJSONArray("dinoxCachedBoxes").orEmpty().mapObjects { obj ->
                DinoxCachedBox(
                    name = obj.getString("name"),
                    fetchedAt = obj.optLong("fetchedAt", 0L),
                    remoteId = obj.optString("remoteId"),
                )
            }
            val attachments = parseAttachments(root.optJSONArray("attachments"))
            val fonts = parseAttachments(root.optJSONArray("fonts"))
            val userSettings = root.optJSONObject("userSettings")?.let {
                SyncUserSettings.fromJsonObject(it)
            }
            return SyncSnapshot(
                exportedAt = root.optLong("exportedAt"),
                notes = notes,
                blocks = blocks,
                tags = tags,
                noteTags = refs,
                noteLinks = noteLinks,
                attachments = attachments,
                fonts = fonts,
                boxes = boxes,
                noteBoxNames = noteBoxNames,
                aiProviders = aiProviders,
                dinoxSyncRules = dinoxSyncRules,
                ruleTargetBoxNames = ruleTargetBoxNames,
                dinoxCachedBoxes = dinoxCachedBoxes,
                userSettings = userSettings,
                dinoxTokenSealed = root.optString("dinoxTokenSealed"),
                dinoxSealMethod = root.optString("dinoxSealMethod"),
            )
        }

        private fun parseAttachments(arr: JSONArray?): List<SyncAttachment> =
            arr.orEmpty().mapObjects { obj ->
                SyncAttachment(
                    relativePath = obj.getString("relativePath"),
                    size = obj.optLong("size"),
                    sha256 = obj.optString("sha256"),
                )
            }

        private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

        private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
            val out = ArrayList<T>(length())
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                out += transform(obj)
            }
            return out
        }
    }
}
