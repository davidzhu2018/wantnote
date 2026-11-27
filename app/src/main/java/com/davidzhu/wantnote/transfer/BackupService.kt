package com.davidzhu.wantnote.transfer

import android.content.Context
import androidx.room.withTransaction
import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.DinoxCachedBox
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.sync.dinox.DinoxBoxSyncWorker
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteDatabase
import com.davidzhu.wantnote.data.NoteLink
import com.davidzhu.wantnote.data.NoteLinkCodec
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteTagCrossRef
import com.davidzhu.wantnote.data.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class TransferException(message: String) : Exception(message)

data class BackupResult(
    val notes: Int,
    val blocks: Int,
    val tags: Int,
    val attachments: Int,
    val fonts: Int,
)

data class RestoreResult(
    val notes: Int,
    val blocks: Int,
    val tags: Int,
    val attachments: Int,
    val fonts: Int,
    val missingAttachments: Int,
)

/**
 * 本地备份 / 恢复。
 *
 * 恢复策略：**结构化导入，不替换正在使用的 db 文件**。先完整解压/解析并
 * 校验 zip（必需数据文件、清单格式版本），确认无误后再在一个 Room 事务里
 * 清空并写回笔记、块、标签、笔记-标签、设置和 AI 提供商；附件/字体通过
 * MediaStorage 写到当前默认数据目录（AppStorage.baseDir）。这样 Room 无需
 * 关闭，也不会出现换库后旧连接指向已删除 inode 的损坏问题。
 */
class BackupService(
    private val context: Context,
    private val db: NoteDatabase,
    private val notes: NoteRepository,
    private val media: MediaStorage,
    private val appVersion: String,
    /** 0.9.0：恢复后补写 Markdown 文件、导入 Dinox 可同步密文用。 */
    private val dinox: com.davidzhu.wantnote.sync.dinox.DinoxSyncService? = null,
) {
    suspend fun createBackup(
        output: OutputStream,
        onProgress: (String) -> Unit = {},
    ): BackupResult = withContext(Dispatchers.IO) {
        try {
            val settings = db.settingsDao().get() ?: AppSettings.DEFAULT
            val providers = db.aiProviderDao().getAll()
            val allNotes = notes.getAllNotes()
            val allBlocks = notes.getAllBlocks()
            val allTags = notes.getAllTags()
            val refs = notes.getAllCrossRefs()
            val allLinks = notes.getAllLinks()
            val allBoxes = notes.getAllBoxes()
            val allSyncRules = db.dinoxSyncRuleDao().getAll()
            val allDinoxBoxes = db.dinoxBoxDao().getAll()

            val files = LinkedHashMap<String, File>()
            val included = LinkedHashSet<String>()

            // 所有 media 目录里的文件（含未被引用但用户仍可能想保留的附件）。
            media.listMediaFiles().forEach { file ->
                val relative = safeRelative(media.relativePathOf(file)) ?: return@forEach
                files[relative] = file
                included += relative
            }
            // 引用到 media/ 之外相对路径的附件也一并收集。
            allBlocks.map { it.mediaPath }.filter { it.isNotBlank() }.distinct().forEach { path ->
                val relative = safeRelative(path) ?: return@forEach
                if (relative in included) return@forEach
                val file = media.fileFor(relative)
                if (file.exists() && file.isFile) {
                    files[relative] = file
                    included += relative
                }
            }
            // 自定义字体：设置里引用的 + fonts 目录里的全部字体。
            val fontFiles = LinkedHashMap<String, File>()
            media.fontsDir().listFiles()?.filter { it.isFile }?.forEach { file ->
                safeRelative("fonts/${file.name}")?.let { fontFiles[it] = file }
            }
            if (settings.customFontPath.isNotBlank()) {
                safeRelative(settings.customFontPath)?.let { relative ->
                    val file = media.fileFor(relative)
                    if (file.exists() && file.isFile) fontFiles[relative] = file
                }
            }
            fontFiles.forEach { (relative, file) -> files[relative] = file }

            val manifest = BackupManifest(
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion,
                notes = allNotes.size,
                blocks = allBlocks.size,
                tags = allTags.size,
                noteTags = refs.size,
                links = allLinks.size,
                boxes = allBoxes.size,
                dinoxSyncRules = allSyncRules.size,
                attachments = included.size,
                fonts = fontFiles.size,
            )

            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                fun putBytes(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }

                putBytes(BackupManifest.MANIFEST_ENTRY, manifest.toText())
                putBytes("data/notes.json", notesToJson(allNotes, allBoxes))
                putBytes("data/blocks.json", blocksToJson(allBlocks))
                putBytes("data/tags.json", tagsToJson(allTags))
                putBytes("data/note_tags.json", refsToJson(refs))
                putBytes("data/note_links.json", linksToJson(allLinks))
                putBytes("data/note_boxes.json", boxesToJson(allBoxes))
                putBytes("data/dinox_sync_rules.json", syncRulesToJson(allSyncRules, allBoxes))
                putBytes("data/dinox_cached_boxes.json", dinoxBoxesToJson(allDinoxBoxes))
                putBytes("data/settings.json", settingsToJson(settings))
                putBytes("data/ai_providers.json", providersToJson(providers))

                var done = 0
                files.forEach { (entryName, file) ->
                    onProgress("正在写入文件 ${done + 1}/${files.size}")
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                    done += 1
                }
            }
            BackupResult(
                notes = allNotes.size,
                blocks = allBlocks.size,
                tags = allTags.size,
                attachments = included.size,
                fonts = fontFiles.size,
            )
        } catch (error: TransferException) {
            throw error
        } catch (error: Exception) {
            throw TransferException(
                "备份失败：" + (error.message?.take(120)?.ifBlank { "无法写入文件" } ?: "无法写入文件"),
            )
        }
    }

    suspend fun restoreBackup(
        input: InputStream,
        onProgress: (String) -> Unit = {},
    ): RestoreResult = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("wantnote-restore-", ".zip", context.cacheDir)
        try {
            input.use { source -> temp.outputStream().use { dest -> source.copyTo(dest) } }
            ZipFile(temp).use { zip -> restoreFromZip(zip, onProgress) }
        } catch (error: BackupManifest.InvalidBackup) {
            throw TransferException(error.message ?: "备份文件无效。")
        } catch (error: TransferException) {
            throw error
        } catch (error: Exception) {
            throw TransferException(
                "恢复失败：备份文件损坏或无法读取（" +
                    (error.message?.take(120)?.ifBlank { "格式错误" } ?: "格式错误") + "）",
            )
        } finally {
            runCatching { temp.delete() }
        }
    }

    private suspend fun restoreFromZip(
        zip: ZipFile,
        onProgress: (String) -> Unit,
    ): RestoreResult {
        val entries = java.util.Collections.list(zip.entries())
        val byName = entries.associateBy { it.name }

        val manifestEntry = byName[BackupManifest.MANIFEST_ENTRY]
            ?: throw BackupManifest.InvalidBackup("备份缺少清单文件 manifest.txt，可能不是想记的备份。")
        val manifest = BackupManifest.fromText(readText(zip, manifestEntry))
        BackupManifest.RequiredDataFiles.forEach { required ->
            if (!byName.containsKey(required)) {
                throw BackupManifest.InvalidBackup("备份缺少数据文件 $required，文件可能不完整。")
            }
        }

        val rawNotes = parseNotes(readText(zip, byName.getValue("data/notes.json")))
        // note_boxes.json 为可选文件，兼容 0.13.0 及更早未含卡片盒的旧备份。
        val storedBoxes = byName["data/note_boxes.json"]?.let { entry ->
            parseBoxes(readText(zip, entry))
        }.orEmpty()
        val rawSyncRules = byName["data/dinox_sync_rules.json"]?.let { entry ->
            parseSyncRules(readText(zip, entry))
        }.orEmpty()
        val storedDinoxBoxes = byName["data/dinox_cached_boxes.json"]?.let { entry ->
            parseDinoxBoxes(readText(zip, entry))
        }.orEmpty()
        val boxPlan = BackupBoxPlan.prepare(
            boxes = storedBoxes,
            notes = rawNotes.map { it.note },
            noteBoxNames = rawNotes.associate { it.note.id to it.boxName },
            syncRules = rawSyncRules.map { it.rule },
            ruleTargetBoxNames = rawSyncRules.associate { it.rule.id to it.targetBoxName },
            now = System.currentTimeMillis(),
        )
        val noteList = boxPlan.notes
        val syncRuleList = boxPlan.syncRules
        val blockList = parseBlocks(readText(zip, byName.getValue("data/blocks.json")))
        val tagList = parseTags(readText(zip, byName.getValue("data/tags.json")))
        val refList = parseRefs(readText(zip, byName.getValue("data/note_tags.json")))
        val settings = parseSettings(readText(zip, byName.getValue("data/settings.json")))
        val providers = parseProviders(readText(zip, byName.getValue("data/ai_providers.json")))

        val noteIds = noteList.map { it.id }.toSet()
        val validBlocks = blockList.filter { it.noteId in noteIds }
        val tagIds = tagList.map { it.id }.toSet()
        val validRefs = refList.filter { it.noteId in noteIds && it.tagId in tagIds }

        // note_links.json is optional so backups made by 0.7.0 still restore.
        val storedLinks = byName["data/note_links.json"]?.let { entry ->
            parseLinks(readText(zip, entry))
        }.orEmpty()
        val validLinks = if (storedLinks.isNotEmpty()) {
            storedLinks.filter { it.sourceNoteId in noteIds && it.targetNoteId in noteIds }
        } else {
            // Derive relations from block markers when restoring an old backup.
            val now = System.currentTimeMillis()
            validBlocks.flatMap { block ->
                NoteLinkCodec.targetIds(block.text)
                    .filter { it != block.noteId && it in noteIds }
                    .map { target ->
                        NoteLink(block.noteId, target, now)
                    }
            }.distinctBy { it.sourceNoteId to it.targetNoteId }
        }

        // 先写附件/字体，再做数据库事务。缺失的附件只跳过并计数。
        var attachmentCount = 0
        var fontCount = 0
        var missing = 0
        val blockMediaPaths = validBlocks.map { it.mediaPath }.filter { it.isNotBlank() }.distinct()
        val zipMediaEntries = entries.filter { it.name.startsWith("media/") && !it.isDirectory }
        zipMediaEntries.forEach { entry ->
            val relative = safeRelative(entry.name)
            if (relative == null) {
                missing += 1
            } else {
                onProgress("正在恢复附件 ${attachmentCount + 1}/${zipMediaEntries.size}")
                copyEntry(zip, entry, media.fileFor(relative))
                attachmentCount += 1
            }
        }
        blockMediaPaths.forEach { path ->
            val relative = safeRelative(path) ?: return@forEach
            val exact = byName[relative]
            val exists = when {
                exact != null && !exact.isDirectory -> true
                else -> zipMediaEntries.any { it.name.substringAfterLast('/') == relative.substringAfterLast('/') }
            }
            if (!exists && !media.fileFor(relative).exists()) missing += 1
        }

        entries.filter { it.name.startsWith("fonts/") && !it.isDirectory }.forEach { entry ->
            val relative = safeRelative(entry.name) ?: return@forEach
            onProgress("正在恢复字体 ${fontCount + 1}")
            copyEntry(zip, entry, media.fileFor(relative))
            fontCount += 1
        }

        onProgress("正在写回笔记与设置")
        db.withTransaction {
            db.tagDao().deleteAllCrossRefs()
            db.noteLinkDao().deleteAll()
            db.blockDao().deleteAll()
            db.noteDao().deleteAll()
            db.noteBoxDao().deleteAll()
            db.dinoxSyncRuleDao().deleteAll()
            db.dinoxBoxDao().deleteAll()
            db.tagDao().deleteAll()
            db.aiProviderDao().deleteAll()

            if (boxPlan.boxes.isNotEmpty()) db.noteBoxDao().upsertAll(boxPlan.boxes)
            if (syncRuleList.isNotEmpty()) db.dinoxSyncRuleDao().upsertAll(syncRuleList)
            if (storedDinoxBoxes.isNotEmpty()) db.dinoxBoxDao().upsertAll(storedDinoxBoxes)
            if (noteList.isNotEmpty()) db.noteDao().upsertAll(noteList)
            if (validBlocks.isNotEmpty()) {
                db.blockDao().upsertAll(
                    validBlocks.mapIndexed { index, block ->
                        block.copy(sortOrder = index)
                    },
                )
            }
            if (validLinks.isNotEmpty()) db.noteLinkDao().insertAll(validLinks)
            tagList.forEach { db.tagDao().upsert(it) }
            if (validRefs.isNotEmpty()) db.tagDao().upsertCrossRefs(validRefs)
            providers.forEach { db.aiProviderDao().upsert(it) }
            db.settingsDao().upsert(settings.copy(id = 1))
        }

        // 0.9.0：旧备份的笔记没有 mdPath/Markdown 文件 —— 恢复后统一补写，
        // 保证正文源文件与数据库一致；再尝试导入随设置恢复的 Dinox Token。
        runCatching { notes.ensureMarkdownFiles() }
        runCatching { dinox?.importSealedToken() }
        runCatching { DinoxBoxSyncWorker.syncAllSchedules(context, syncRuleList) }

        return RestoreResult(
            notes = noteList.size,
            blocks = validBlocks.size,
            tags = tagList.size,
            attachments = attachmentCount,
            fonts = fontCount,
            missingAttachments = missing,
        )
    }

    private fun copyEntry(zip: ZipFile, entry: ZipEntry, dest: File) {
        dest.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
    }

    private fun readText(zip: ZipFile, entry: ZipEntry): String {
        val size = entry.size
        if (size > MAX_JSON_BYTES) {
            throw BackupManifest.InvalidBackup("备份中的数据文件过大，无法恢复。")
        }
        return zip.getInputStream(entry).use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
    }

    // ---- JSON 序列化 -----------------------------------------------------

    private fun notesToJson(items: List<Note>, boxes: List<NoteBox>): String {
        val boxNameById = boxes.associate { it.id to it.name }
        return JSONArray().also { arr ->
            items.forEach { note ->
                arr.put(
                    JSONObject()
                        .put("id", note.id)
                        .put("title", note.title)
                        .put("body", note.body)
                        .put("mdPath", note.mdPath)
                        .put("boxId", note.boxId)
                        .put("boxName", boxNameById[note.boxId].orEmpty())
                        .put("createdAt", note.createdAt)
                        .put("updatedAt", note.updatedAt),
                )
            }
        }.toString()
    }

    private fun boxesToJson(items: List<NoteBox>): String = JSONArray().also { arr ->
        items.forEach { box ->
            arr.put(
                JSONObject()
                    .put("id", box.id)
                    .put("name", box.name)
                    .put("createdAt", box.createdAt),
            )
        }
    }.toString()

    private fun syncRulesToJson(items: List<DinoxSyncRule>, boxes: List<NoteBox>): String {
        val boxNameById = boxes.associate { it.id to it.name }
        return JSONArray().also { arr ->
            items.forEach { rule ->
                arr.put(
                    JSONObject()
                        .put("id", rule.id)
                        .put("ruleType", rule.ruleType)
                        .put("sourceBoxesJson", rule.sourceBoxesJson)
                        .put("targetBoxId", rule.targetBoxId)
                        .put("targetBoxName", boxNameById[rule.targetBoxId].orEmpty())
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
        }.toString()
    }

    private fun dinoxBoxesToJson(items: List<DinoxCachedBox>): String = JSONArray().also { arr ->
        items.forEach { box ->
            arr.put(
                JSONObject()
                    .put("name", box.name)
                    .put("fetchedAt", box.fetchedAt)
                    .put("remoteId", box.remoteId),
            )
        }
    }.toString()

    private fun blocksToJson(items: List<NoteBlock>): String = JSONArray().also { arr ->
        items.forEach { block ->
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
    }.toString()

    private fun tagsToJson(items: List<Tag>): String = JSONArray().also { arr ->
        items.forEach { tag ->
            arr.put(
                JSONObject()
                    .put("id", tag.id)
                    .put("path", tag.path)
                    .put("color", tag.color)
                    .put("createdAt", tag.createdAt),
            )
        }
    }.toString()

    private fun refsToJson(items: List<NoteTagCrossRef>): String = JSONArray().also { arr ->
        items.forEach { ref ->
            arr.put(JSONObject().put("noteId", ref.noteId).put("tagId", ref.tagId))
        }
    }.toString()

    private fun linksToJson(items: List<NoteLink>): String = JSONArray().also { arr ->
        items.forEach { link ->
            arr.put(
                JSONObject()
                    .put("sourceNoteId", link.sourceNoteId)
                    .put("targetNoteId", link.targetNoteId)
                    .put("createdAt", link.createdAt),
            )
        }
    }.toString()

    private fun providersToJson(items: List<AiProvider>): String = JSONArray().also { arr ->
        items.forEach { provider ->
            arr.put(
                JSONObject()
                    .put("id", provider.id)
                    .put("presetId", provider.presetId)
                    .put("name", provider.name)
                    .put("baseUrl", provider.baseUrl)
                    .put("apiKey", provider.apiKey)
                    .put("availableModelsJson", provider.availableModelsJson)
                    .put("enabledModelsJson", provider.enabledModelsJson)
                    .put("updatedAt", provider.updatedAt),
            )
        }
    }.toString()

    private fun settingsToJson(settings: AppSettings): String = JSONObject()
        .put("id", settings.id)
        .put("themeName", settings.themeName)
        .put("fontScale", settings.fontScale)
        .put("dayNight", settings.dayNight)
        .put("compressImages", settings.compressImages)
        .put("homeView", settings.homeView)
        .put("webdavUrl", settings.webdavUrl)
        .put("webdavUsername", settings.webdavUsername)
        .put("webdavPassword", settings.webdavPassword)
        .put("s3Endpoint", settings.s3Endpoint)
        .put("s3Bucket", settings.s3Bucket)
        .put("s3Region", settings.s3Region)
        .put("s3AccessKey", settings.s3AccessKey)
        .put("s3SecretKey", settings.s3SecretKey)
        .put("s3Prefix", settings.s3Prefix)
        .put("aiSummaryProviderId", settings.aiSummaryProviderId)
        .put("aiSummaryModelId", settings.aiSummaryModelId)
        .put("aiTitleTagProviderId", settings.aiTitleTagProviderId)
        .put("aiTitleTagModelId", settings.aiTitleTagModelId)
        .put("aiSummaryText", settings.aiSummaryText)
        .put("aiSummaryUpdatedAt", settings.aiSummaryUpdatedAt)
        .put("aiSummaryScope", settings.aiSummaryScope)
        .put("compressVideos", settings.compressVideos)
        .put("editorFontId", settings.editorFontId)
        .put("customFontPath", settings.customFontPath)
        .put("syncMethod", settings.syncMethod)
        .put("editorMode", settings.editorMode)
        .put("themeCustomJson", settings.themeCustomJson)
        .put("dinoxTokenSealed", settings.dinoxTokenSealed)
        .put("dinoxSealMethod", settings.dinoxSealMethod)
        .put("dinoxLastSyncAt", settings.dinoxLastSyncAt)
        .put("dinoxSyncBox", settings.dinoxSyncBox)
        .put("dinoxAutoSyncMinutes", settings.dinoxAutoSyncMinutes)
        .put("markdownMigrationDone", settings.markdownMigrationDone)
        .put("dinoxBoxesFetchedAt", settings.dinoxBoxesFetchedAt)
        .put("aiEnableTitle", settings.aiEnableTitle)
        .put("aiEnableTag", settings.aiEnableTag)
        .put("aiEnableBox", settings.aiEnableBox)
        .put("aiEnableSummary", settings.aiEnableSummary)
        .put("aiEnableInsight", settings.aiEnableInsight)
        .toString()

    // ---- JSON 反序列化（缺字段用当前默认值，兼容未来/旧版备份） -------------

    private data class ParsedBackupNote(
        val note: Note,
        val boxName: String,
    )

    private fun parseNotes(text: String): List<ParsedBackupNote> =
        JSONArray(text).objects { obj ->
            ParsedBackupNote(
                note = Note(
                    id = obj.getString("id"),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    mdPath = obj.optString("mdPath"),
                    boxId = obj.optString("boxId"),
                ),
                boxName = obj.optString("boxName"),
            )
        }

    private fun parseBoxes(text: String): List<NoteBox> =
        JSONArray(text).objects { obj ->
            NoteBox(
                id = obj.getString("id"),
                name = obj.optString("name"),
                createdAt = obj.optLong("createdAt"),
            )
        }

    private data class ParsedSyncRule(
        val rule: DinoxSyncRule,
        val targetBoxName: String,
    )

    private fun parseSyncRules(text: String): List<ParsedSyncRule> =
        JSONArray(text).objects { obj ->
            ParsedSyncRule(
                rule = DinoxSyncRule(
                    id = obj.getString("id"),
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
                ),
                targetBoxName = obj.optString("targetBoxName"),
            )
        }

    private fun parseDinoxBoxes(text: String): List<DinoxCachedBox> =
        JSONArray(text).objects { obj ->
            DinoxCachedBox(
                name = obj.getString("name"),
                fetchedAt = obj.optLong("fetchedAt", 0L),
                remoteId = obj.optString("remoteId"),
            )
        }

    private fun parseBlocks(text: String): List<NoteBlock> =
        JSONArray(text).objects { obj ->
            NoteBlock(
                id = obj.getString("id"),
                noteId = obj.getString("noteId"),
                type = obj.optString("type", BlockType.PARAGRAPH.name),
                text = obj.optString("text"),
                indent = obj.optInt("indent"),
                checked = obj.optBoolean("checked"),
                fontSize = obj.optString("fontSize", "NORMAL"),
                mediaPath = obj.optString("mediaPath"),
                sortOrder = obj.optInt("sortOrder"),
                headingLevel = obj.optInt("headingLevel"),
            )
        }

    private fun parseTags(text: String): List<Tag> =
        JSONArray(text).objects { obj ->
            Tag(
                id = obj.getString("id"),
                path = obj.optString("path"),
                color = obj.optLong("color", Tag.defaultColor()),
                createdAt = obj.optLong("createdAt"),
            )
        }

    private fun parseRefs(text: String): List<NoteTagCrossRef> =
        JSONArray(text).objects { obj ->
            NoteTagCrossRef(
                noteId = obj.getString("noteId"),
                tagId = obj.getString("tagId"),
            )
        }

    private fun parseLinks(text: String): List<NoteLink> =
        JSONArray(text).objects { obj ->
            NoteLink(
                sourceNoteId = obj.getString("sourceNoteId"),
                targetNoteId = obj.getString("targetNoteId"),
                createdAt = obj.optLong("createdAt"),
            )
        }

    private fun parseProviders(text: String): List<AiProvider> =
        JSONArray(text).objects { obj ->
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

    private fun parseSettings(text: String): AppSettings {
        val obj = JSONObject(text)
        val default = AppSettings.DEFAULT
        return AppSettings(
            id = 1,
            themeName = obj.optString("themeName", default.themeName),
            fontScale = obj.optString("fontScale", default.fontScale),
            dayNight = obj.optString("dayNight", default.dayNight),
            compressImages = obj.optBoolean("compressImages", default.compressImages),
            homeView = obj.optString("homeView", default.homeView),
            webdavUrl = obj.optString("webdavUrl", default.webdavUrl),
            webdavUsername = obj.optString("webdavUsername", default.webdavUsername),
            webdavPassword = obj.optString("webdavPassword", default.webdavPassword),
            s3Endpoint = obj.optString("s3Endpoint", default.s3Endpoint),
            s3Bucket = obj.optString("s3Bucket", default.s3Bucket),
            s3Region = obj.optString("s3Region", default.s3Region),
            s3AccessKey = obj.optString("s3AccessKey", default.s3AccessKey),
            s3SecretKey = obj.optString("s3SecretKey", default.s3SecretKey),
            s3Prefix = obj.optString("s3Prefix", default.s3Prefix),
            aiSummaryProviderId = obj.optString("aiSummaryProviderId", default.aiSummaryProviderId),
            aiSummaryModelId = obj.optString("aiSummaryModelId", default.aiSummaryModelId),
            aiTitleTagProviderId = obj.optString("aiTitleTagProviderId", default.aiTitleTagProviderId),
            aiTitleTagModelId = obj.optString("aiTitleTagModelId", default.aiTitleTagModelId),
            aiSummaryText = obj.optString("aiSummaryText", default.aiSummaryText),
            aiSummaryUpdatedAt = obj.optLong("aiSummaryUpdatedAt", default.aiSummaryUpdatedAt),
            aiSummaryScope = obj.optString("aiSummaryScope", default.aiSummaryScope),
            compressVideos = obj.optBoolean("compressVideos", default.compressVideos),
            editorFontId = obj.optString("editorFontId", default.editorFontId),
            customFontPath = obj.optString("customFontPath", default.customFontPath),
            syncMethod = obj.optString("syncMethod", default.syncMethod),
            editorMode = obj.optString("editorMode", default.editorMode),
            themeCustomJson = obj.optString("themeCustomJson", default.themeCustomJson),
            dinoxTokenSealed = obj.optString("dinoxTokenSealed", default.dinoxTokenSealed),
            dinoxSealMethod = obj.optString("dinoxSealMethod", default.dinoxSealMethod),
            dinoxLastSyncAt = obj.optLong("dinoxLastSyncAt", default.dinoxLastSyncAt),
            dinoxSyncBox = obj.optString("dinoxSyncBox", default.dinoxSyncBox),
            dinoxAutoSyncMinutes = obj.optInt("dinoxAutoSyncMinutes", default.dinoxAutoSyncMinutes),
            markdownMigrationDone = obj.optBoolean("markdownMigrationDone", default.markdownMigrationDone),
            dinoxBoxesFetchedAt = obj.optLong("dinoxBoxesFetchedAt", default.dinoxBoxesFetchedAt),
            aiEnableTitle = obj.optBoolean("aiEnableTitle", default.aiEnableTitle),
            aiEnableTag = obj.optBoolean("aiEnableTag", default.aiEnableTag),
            aiEnableBox = obj.optBoolean("aiEnableBox", default.aiEnableBox),
            aiEnableSummary = obj.optBoolean("aiEnableSummary", default.aiEnableSummary),
            aiEnableInsight = obj.optBoolean("aiEnableInsight", default.aiEnableInsight),
        )
    }

    private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> {
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            out += transform(obj)
        }
        return out
    }

    /** 防止 zip 路径穿越；返回 null 表示不能安全写入。 */
    private fun safeRelative(raw: String): String? {
        val cleaned = raw.replace('\\', '/').trim().trimStart('/')
        val parts = cleaned.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    companion object {
        private const val MAX_JSON_BYTES = 64L * 1024 * 1024
    }
}

internal data class PreparedBoxRestore(
    val boxes: List<NoteBox>,
    val notes: List<Note>,
    val syncRules: List<DinoxSyncRule> = emptyList(),
)

/**
 * 0.14.0：备份恢复时的卡片盒规划器（纯 Kotlin，可直接 JVM 单测）。
 *
 * - 始终保证默认「暂存箱」（[NoteBox.DEFAULT_ID]）存在；
 * - 按卡片盒名称去重，避免违反 `note_boxes.name` 唯一索引，并把重名盒子的笔记重映射到存活 ID；
 * - 若笔记携带了 `boxName` 但 `note_boxes.json` 缺失该盒，则自动补建该卡片盒，确保归属不丢失。
 */
internal object BackupBoxPlan {
    fun prepare(
        boxes: List<NoteBox>,
        notes: List<Note>,
        noteBoxNames: Map<String, String> = emptyMap(),
        syncRules: List<DinoxSyncRule> = emptyList(),
        ruleTargetBoxNames: Map<String, String> = emptyMap(),
        now: Long = 0L,
    ): PreparedBoxRestore {
        val byName = LinkedHashMap<String, NoteBox>()
        val idRemap = HashMap<String, String>()

        // 先纳入所有备份里的卡片盒（规范化名称并按名称去重）。
        boxes.forEach { raw ->
            val name = raw.name.trim()
            if (name.isEmpty()) return@forEach
            val existing = byName[name]
            if (existing != null) {
                idRemap[raw.id] = existing.id
            } else {
                val normalized = if (name == NoteBox.DEFAULT_NAME) {
                    raw.copy(id = NoteBox.DEFAULT_ID, name = NoteBox.DEFAULT_NAME)
                } else {
                    raw.copy(name = name)
                }
                byName[name] = normalized
                idRemap[raw.id] = normalized.id
            }
        }

        // 确保默认「暂存箱」一定存在。
        if (!byName.containsKey(NoteBox.DEFAULT_NAME)) {
            byName[NoteBox.DEFAULT_NAME] = NoteBox(
                id = NoteBox.DEFAULT_ID,
                name = NoteBox.DEFAULT_NAME,
                createdAt = 0L,
            )
        }
        idRemap[NoteBox.DEFAULT_ID] = byName.getValue(NoteBox.DEFAULT_NAME).id

        val validIds = byName.values.mapTo(HashSet()) { it.id }

        // 遍历笔记：映射 boxId；若 note_boxes.json 漏了某个盒子但笔记带了 boxName，则就地补建。
        val remappedNotes = notes.map { note ->
            val rawBoxId = note.boxId.trim()
            val rawBoxName = noteBoxNames[note.id].orEmpty().trim()
            val targetBoxId = when {
                rawBoxId.isNotEmpty() && idRemap.containsKey(rawBoxId) -> idRemap.getValue(rawBoxId)
                rawBoxId.isNotEmpty() && rawBoxId in validIds -> rawBoxId
                rawBoxName.isNotEmpty() -> {
                    val existing = byName[rawBoxName]
                    if (existing != null) {
                        existing.id
                    } else {
                        val createdId = rawBoxId.ifEmpty { "box_restored_${byName.size}" }
                        val created = NoteBox(
                            id = createdId,
                            name = rawBoxName,
                            createdAt = now,
                        )
                        byName[rawBoxName] = created
                        validIds += created.id
                        if (rawBoxId.isNotEmpty()) idRemap[rawBoxId] = created.id
                        created.id
                    }
                }
                else -> ""
            }
            if (targetBoxId == note.boxId) note else note.copy(boxId = targetBoxId)
        }

        val remappedRules = syncRules.map { rule ->
            if (rule.followDinoxName) {
                rule.copy(targetBoxId = "")
            } else {
                val rawTargetId = rule.targetBoxId.trim()
                val rawTargetName = ruleTargetBoxNames[rule.id].orEmpty().trim()
                val mappedTargetId = when {
                    rawTargetId.isNotEmpty() && idRemap.containsKey(rawTargetId) -> idRemap.getValue(rawTargetId)
                    rawTargetId.isNotEmpty() && rawTargetId in validIds -> rawTargetId
                    rawTargetName.isNotEmpty() -> {
                        val existing = byName[rawTargetName]
                        if (existing != null) {
                            existing.id
                        } else {
                            val createdId = rawTargetId.ifEmpty { "box_restored_${byName.size}" }
                            val created = NoteBox(id = createdId, name = rawTargetName, createdAt = now)
                            byName[rawTargetName] = created
                            validIds += created.id
                            if (rawTargetId.isNotEmpty()) idRemap[rawTargetId] = created.id
                            created.id
                        }
                    }
                    else -> rawTargetId
                }
                if (mappedTargetId == rule.targetBoxId) rule else rule.copy(targetBoxId = mappedTargetId)
            }
        }

        return PreparedBoxRestore(
            boxes = byName.values.toList(),
            notes = remappedNotes,
            syncRules = remappedRules,
        )
    }
}
