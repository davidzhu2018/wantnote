package com.davidzhu.wantnote.sync

import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteTagCrossRef
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.data.SyncMethod
import com.davidzhu.wantnote.data.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Uploads notes, tags and attachments to a dedicated `wantnote/` folder on the
 * selected remote (WebDAV or S3), and downloads them back with a last-write
 * merge on [Note.updatedAt]. Credentials stay in the local Room settings row
 * and are never written to the remote payload or to logs.
 *
 * 0.9.0：快照携带每篇笔记的 Markdown 路径（mdPath）与 Dinox Token 的
 * 「可同步密文」（凭据派生密钥加密，见 DinoxPortableCipher），下载端
 * 用自己的同步凭据解密后导入本机 Keystore 存储。
 */
class CloudSyncService(
    private val notes: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val media: MediaStorage,
    private val dinox: com.davidzhu.wantnote.sync.dinox.DinoxSyncService? = null,
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
            backend.putText("wantnote/manifest.json", snapshot.toJson())
            "已上传 ${snapshot.notes.size} 条笔记、${snapshot.attachments.size} 个附件"
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
            var applied = 0
            val blocksByNote = snapshot.blocks.groupBy { it.noteId }
            snapshot.notes.forEach { remote ->
                val used = notes.mergeRemoteNote(remote, blocksByNote[remote.id].orEmpty())
                if (used) applied += 1
            }
            runCatching { notes.ensureMarkdownFiles() }
            // 0.9.0：随设置同步的 Dinox Token 密文 —— 存下来并尝试用本地
            // 同步凭据解开（凭据一致才能解开；不一致则静默跳过）。
            if (snapshot.dinoxTokenSealed.isNotBlank()) {
                settingsRepository.update {
                    it.copy(
                        dinoxTokenSealed = snapshot.dinoxTokenSealed,
                        dinoxSealMethod = snapshot.dinoxSealMethod,
                    )
                }
                dinox?.importSealedToken()
            }
            val idMap = mutableMapOf<String, String>()
            snapshot.tags.forEach { remote ->
                val local = notes.mergeRemoteTag(remote)
                idMap[remote.id] = local.id
            }
            snapshot.noteTags.forEach { ref ->
                val localTagId = idMap[ref.tagId] ?: ref.tagId
                notes.attachTag(ref.noteId, localTagId)
            }
            var downloaded = 0
            snapshot.attachments.forEach { item ->
                val dest = media.fileFor(item.relativePath)
                val need = !dest.exists() || dest.length() != item.size
                if (need) {
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
            "已下载合并：更新或新增 $applied 条笔记，写入 $downloaded 个附件（未提及的本地笔记已保留）"
        }.recoverCatching { error ->
            throw SyncException(safe(error, settings))
        }
    }

    private suspend fun exportSnapshot(): SyncSnapshot {
        val settings = settingsRepository.get()
        val allNotes = notes.getAllNotes()
        val allBlocks = notes.getAllBlocks()
        val allTags = notes.getAllTags()
        val refs = notes.getAllCrossRefs()
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
        return SyncSnapshot(
            exportedAt = System.currentTimeMillis(),
            notes = allNotes,
            blocks = allBlocks,
            tags = allTags,
            noteTags = refs,
            attachments = attachments,
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
        return if (error is SyncException) {
            Redacting.message(error, secrets)
        } else {
            Redacting.message(error, secrets)
        }
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

internal data class SyncSnapshot(
    val exportedAt: Long,
    val notes: List<Note>,
    val blocks: List<NoteBlock>,
    val tags: List<Tag>,
    val noteTags: List<NoteTagCrossRef>,
    val attachments: List<SyncAttachment>,
    /** 0.9.0：Dinox Token 的可同步密文（凭据派生密钥加密；明文绝不入快照）。 */
    val dinoxTokenSealed: String = "",
    val dinoxSealMethod: String = "",
) {
    fun toJson(): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("exportedAt", exportedAt)
            .put("dinoxTokenSealed", dinoxTokenSealed)
            .put("dinoxSealMethod", dinoxSealMethod)
        root.put(
            "notes",
            JSONArray().also { arr ->
                notes.forEach { note ->
                    arr.put(
                        JSONObject()
                            .put("id", note.id)
                            .put("title", note.title)
                            .put("body", note.body)
                            .put("mdPath", note.mdPath)
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
                            .put("sortOrder", block.sortOrder),
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
            "attachments",
            JSONArray().also { arr ->
                attachments.forEach { item ->
                    arr.put(
                        JSONObject()
                            .put("relativePath", item.relativePath)
                            .put("size", item.size)
                            .put("sha256", item.sha256),
                    )
                }
            },
        )
        return root.toString()
    }

    companion object {
        const val FORMAT = "wantnote-sync-v1"

        fun fromJson(text: String): SyncSnapshot {
            val root = JSONObject(text)
            val notes = root.optJSONArray("notes").orEmpty().mapObjects { obj ->
                Note(
                    id = obj.getString("id"),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    mdPath = obj.optString("mdPath"),
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
            val attachments = root.optJSONArray("attachments").orEmpty().mapObjects { obj ->
                SyncAttachment(
                    relativePath = obj.getString("relativePath"),
                    size = obj.optLong("size"),
                    sha256 = obj.optString("sha256"),
                )
            }
            return SyncSnapshot(
                exportedAt = root.optLong("exportedAt"),
                notes = notes,
                blocks = blocks,
                tags = tags,
                noteTags = refs,
                attachments = attachments,
                dinoxTokenSealed = root.optString("dinoxTokenSealed"),
                dinoxSealMethod = root.optString("dinoxSealMethod"),
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
