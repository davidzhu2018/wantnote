package com.davidzhu.wantnote.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidzhu.wantnote.WantNoteApplication
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ReadingSyncResult(
    val imported: Int,
    val skipped: Int,
    val message: String,
)

/**
 * 0.19.0：生态互联外部同步服务：
 * 1. Flomo 官方 Webhook 单向发送（供主页卡片长按与多选队列发送使用）；
 * 2. Readwise 单向同步（API v2 Export，支持按类别映射到不同卡片盒、全量/增量、手动/WorkManager 自动同步）；
 * 3. 微信读书单向同步（API Key，支持划线与高亮同步到指定卡片盒、全量/增量、手动/WorkManager 自动同步）。
 */
object ExternalReadingSyncService {

    /** Readwise 标准类别（支持按类别映射到想记不同卡片盒）。 */
    val READWISE_CATEGORIES = listOf(
        "books" to "书籍 (books)",
        "articles" to "文章 (articles)",
        "tweets" to "推文 (tweets)",
        "podcasts" to "播客 (podcasts)",
        "supplementals" to "补充 (supplementals)",
    )

    // ==================== 1. Flomo Webhook ====================

    suspend fun sendNoteToFlomo(
        webhookUrl: String,
        note: Note,
        tags: List<Tag> = emptyList(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val url = webhookUrl.trim()
        if (url.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("请先在「设置 → 数据管理 → 生态互联 → Flomo」填写 Flomo API URL"))
        }
        val content = formatFlomoPayload(note, tags)
        if (content.isBlank()) {
            return@withContext Result.failure(IllegalStateException("空笔记无需发送到 Flomo"))
        }
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "WantNote-Android/0.19.0")
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(JSONObject().put("content", content).toString())
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.let { s ->
                        BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).use { it.readText() }
                    }.orEmpty().take(120)
                    throw IllegalStateException("Flomo 发送失败（HTTP $code）$err")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    internal fun formatFlomoPayload(note: Note, tags: List<Tag>): String {
        return buildString {
            if (note.title.isNotBlank()) {
                append(note.title.trim())
                if (note.body.isNotBlank()) append("\n\n")
            }
            append(note.body.trim())
            if (tags.isNotEmpty()) {
                append("\n\n")
                append(tags.joinToString(" ") { "#${it.path.trim()}" })
            }
        }.trim()
    }

    // ==================== 2. Readwise 单向同步 ====================

    fun parseCategoryBoxMap(json: String): Map<String, String> {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k).trim()
            if (v.isNotEmpty()) out[k] = v
        }
        return out
    }

    fun serializeCategoryBoxMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> if (v.isNotBlank()) obj.put(k, v.trim()) }
        return obj.toString()
    }

    suspend fun testReadwiseToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.isEmpty()) return@withContext Result.failure(IllegalStateException("请先填写 Readwise Access Token"))
        runCatching {
            val conn = (URL("https://readwise.io/api/v2/auth/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Token $clean")
                setRequestProperty("User-Agent", "WantNote-Android/0.19.0")
            }
            try {
                if (conn.responseCode == 204 || conn.responseCode in 200..299) {
                    "Readwise Token 验证通过"
                } else {
                    throw IllegalStateException("Readwise 验证失败（HTTP ${conn.responseCode}），请检查 Access Token")
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun syncReadwise(
        token: String,
        categoryBoxesJson: String,
        lastSyncAt: Long,
        fullSync: Boolean,
        repository: NoteRepository,
    ): Result<ReadingSyncResult> = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.isEmpty()) return@withContext Result.failure(IllegalStateException("请先填写 Readwise Access Token"))
        runCatching {
            val boxMap = parseCategoryBoxMap(categoryBoxesJson)
            val updatedAfter = if (!fullSync && lastSyncAt > 0L) isoUtc(lastSyncAt) else null
            val existingFingerprints = repository.observeAll().first()
                .map { fingerprint(it.title, it.body) }
                .toMutableSet()

            var cursor: String? = null
            var pages = 0
            var imported = 0
            var skipped = 0

            do {
                val urlStr = buildString {
                    append("https://readwise.io/api/v2/export/")
                    val params = ArrayList<String>()
                    if (!updatedAfter.isNullOrBlank()) {
                        params += "updatedAfter=${URLEncoder.encode(updatedAfter, "UTF-8")}"
                    }
                    if (!cursor.isNullOrBlank()) {
                        params += "pageCursor=${URLEncoder.encode(cursor, "UTF-8")}"
                    }
                    if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
                }
                val raw = httpGet(urlStr, mapOf("Authorization" to "Token $clean"))
                    ?: throw IllegalStateException("Readwise 接口请求失败，请检查网络或 Token")
                val parsed = parseReadwiseExportPage(raw)
                val now = System.currentTimeMillis()
                for (doc in parsed.docs) {
                    val fp = fingerprint(doc.title, doc.markdown)
                    if (fp in existingFingerprints) {
                        skipped += 1
                        continue
                    }
                    val targetBox = boxMap[doc.category.lowercase()].orEmpty()
                    val note = Note(
                        id = UUID.randomUUID().toString(),
                        title = doc.title.take(120),
                        body = doc.markdown,
                        createdAt = now,
                        updatedAt = now,
                        boxId = targetBox,
                    )
                    repository.insertImportedMarkdown(note, doc.markdown)
                    existingFingerprints += fp
                    imported += 1
                }
                cursor = parsed.nextCursor
                pages += 1
            } while (!cursor.isNullOrBlank() && pages < 10)

            val modeLabel = if (fullSync) "全量" else "增量"
            ReadingSyncResult(
                imported = imported,
                skipped = skipped,
                message = "Readwise ${modeLabel}同步完成：新增 $imported 篇，跳过重复 $skipped 篇",
            )
        }
    }

    internal data class ReadwiseDoc(
        val title: String,
        val category: String,
        val markdown: String,
    )

    internal data class ReadwisePage(
        val docs: List<ReadwiseDoc>,
        val nextCursor: String?,
    )

    internal fun parseReadwiseExportPage(json: String): ReadwisePage {
        val root = JSONObject(json)
        val nextCursor = root.optString("nextPageCursor").takeIf { it.isNotBlank() && it != "null" }
        val results = root.optJSONArray("results") ?: JSONArray()
        val docs = ArrayList<ReadwiseDoc>(results.length())
        for (i in 0 until results.length()) {
            val book = results.optJSONObject(i) ?: continue
            val title = book.optString("title").trim().ifBlank { "Readwise 摘录" }
            val author = book.optString("author").trim()
            val category = book.optString("category").trim().ifBlank { "articles" }
            val sourceUrl = book.optString("source_url").trim()
            val highlights = book.optJSONArray("highlights") ?: continue
            if (highlights.length() == 0) continue

            val md = buildString {
                if (author.isNotBlank()) append("作者：$author\n")
                if (sourceUrl.isNotBlank()) append("来源：[$sourceUrl]($sourceUrl)\n")
                append("类别：$category\n\n")
                for (j in 0 until highlights.length()) {
                    val h = highlights.optJSONObject(j) ?: continue
                    val text = h.optString("text").trim()
                    val note = h.optString("note").trim()
                    if (text.isNotEmpty()) {
                        append("> ").append(text.replace("\n", "\n> ")).append("\n\n")
                    }
                    if (note.isNotEmpty()) {
                        append("笔记：").append(note).append("\n\n")
                    }
                }
            }.trim()
            if (md.isNotEmpty()) {
                docs += ReadwiseDoc(title = title, category = category, markdown = md)
            }
        }
        return ReadwisePage(docs, nextCursor)
    }

    // ==================== 3. 微信读书单向同步 ====================

    suspend fun syncWeRead(
        apiKey: String,
        targetBoxId: String,
        lastSyncAt: Long,
        fullSync: Boolean,
        repository: NoteRepository,
    ): Result<ReadingSyncResult> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext Result.failure(IllegalStateException("请先填写微信读书 API Key"))
        runCatching {
            val headers = mapOf(
                "Authorization" to "Bearer $key",
                "x-api-key" to key,
            )
            // 微信读书官方 Skill / 开放接口拉取划线与笔记本列表
            val endpoints = listOf(
                "https://weread.qq.com/api/user/notebook",
                "https://i.weread.qq.com/user/notebooks",
            )
            var rawNotebooks: String? = null
            for (ep in endpoints) {
                rawNotebooks = httpGet(ep, headers)
                if (!rawNotebooks.isNullOrBlank()) break
            }
            if (rawNotebooks.isNullOrBlank()) {
                throw IllegalStateException("微信读书接口暂不可达或 API Key 无效，请在微信读书「设置 → 微信读书Skill」核对 API Key")
            }

            val books = parseWeReadNotebooks(rawNotebooks)
            val existingFingerprints = repository.observeAll().first()
                .map { fingerprint(it.title, it.body) }
                .toMutableSet()

            var imported = 0
            var skipped = 0
            val now = System.currentTimeMillis()
            val sinceSec = if (!fullSync && lastSyncAt > 0L) lastSyncAt / 1000L else 0L

            for (book in books) {
                if (sinceSec > 0L && book.sortTime > 0L && book.sortTime < sinceSec) {
                    skipped += 1
                    continue
                }
                val bmRaw = httpGet(
                    "https://weread.qq.com/web/book/bookmarklist?bookId=${URLEncoder.encode(book.bookId, "UTF-8")}",
                    headers,
                )
                val highlights = if (!bmRaw.isNullOrBlank()) parseWeReadBookmarks(bmRaw) else emptyList()
                val body = buildString {
                    if (book.author.isNotBlank()) append("作者：${book.author}\n\n")
                    if (highlights.isEmpty()) {
                        append("（来自微信读书，共 ${book.noteCount} 条划线/想法）")
                    } else {
                        highlights.forEach { h ->
                            append("> ").append(h.markText.replace("\n", "\n> ")).append("\n\n")
                            if (h.thought.isNotBlank()) {
                                append("想法：").append(h.thought).append("\n\n")
                            }
                        }
                    }
                }.trim()
                val title = "《${book.title}》微信读书划线"
                val fp = fingerprint(title, body)
                if (fp in existingFingerprints) {
                    skipped += 1
                    continue
                }
                val note = Note(
                    id = UUID.randomUUID().toString(),
                    title = title.take(120),
                    body = body,
                    createdAt = now,
                    updatedAt = now,
                    boxId = targetBoxId,
                )
                repository.insertImportedMarkdown(note, body)
                existingFingerprints += fp
                imported += 1
            }

            val modeLabel = if (fullSync) "全量" else "增量"
            ReadingSyncResult(
                imported = imported,
                skipped = skipped,
                message = "微信读书${modeLabel}同步完成：新增 $imported 篇，跳过 $skipped 篇",
            )
        }
    }

    internal data class WeReadBook(
        val bookId: String,
        val title: String,
        val author: String,
        val noteCount: Int,
        val sortTime: Long,
    )

    internal data class WeReadHighlight(
        val markText: String,
        val thought: String = "",
    )

    internal fun parseWeReadNotebooks(json: String): List<WeReadBook> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("books") ?: root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<WeReadBook>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val bookObj = item.optJSONObject("book") ?: item
            val bookId = item.optString("bookId").ifBlank { bookObj.optString("bookId") }.trim()
            val title = bookObj.optString("title").trim()
            if (bookId.isEmpty() || title.isEmpty()) continue
            out += WeReadBook(
                bookId = bookId,
                title = title,
                author = bookObj.optString("author").trim(),
                noteCount = item.optInt("noteCount", item.optInt("reviewCount", 0)),
                sortTime = item.optLong("sort", 0L),
            )
        }
        return out
    }

    internal fun parseWeReadBookmarks(json: String): List<WeReadHighlight> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("updated") ?: root.optJSONArray("bookmarks") ?: return emptyList()
        val out = ArrayList<WeReadHighlight>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val mark = item.optString("markText").ifBlank { item.optString("abstract") }.trim()
            val thought = item.optString("content").trim()
            if (mark.isNotEmpty() || thought.isNotEmpty()) {
                out += WeReadHighlight(markText = mark.ifEmpty { thought }, thought = if (mark.isNotEmpty()) thought else "")
            }
        }
        return out
    }

    private fun httpGet(url: String, headers: Map<String, String>): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WantNote-Android/0.19.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun isoUtc(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(millis))
    }

    private fun fingerprint(title: String, body: String): String {
        return "${title.trim().lowercase()}||${body.trim().take(120).lowercase()}"
    }
}

/**
 * 0.19.0：Readwise / 微信读书 WorkManager 定时自动增量同步（模仿 DinoxBoxSyncWorker 架构）。
 */
class ExternalReadingAutoSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WantNoteApplication ?: return Result.success()
        val target = inputData.getString(KEY_TARGET) ?: return Result.success()
        val settings = app.settingsRepository.get()
        when (target) {
            TARGET_READWISE -> {
                if (settings.readwiseToken.isNotBlank() && settings.readwiseAutoSyncMinutes > 0) {
                    val res = ExternalReadingSyncService.syncReadwise(
                        token = settings.readwiseToken,
                        categoryBoxesJson = settings.readwiseCategoryBoxesJson,
                        lastSyncAt = settings.readwiseLastSyncAt,
                        fullSync = false,
                        repository = app.repository,
                    )
                    if (res.isSuccess) {
                        app.settingsRepository.update { it.copy(readwiseLastSyncAt = System.currentTimeMillis()) }
                    }
                }
            }
            TARGET_WEREAD -> {
                if (settings.wereadApiKey.isNotBlank() && settings.wereadAutoSyncMinutes > 0) {
                    val res = ExternalReadingSyncService.syncWeRead(
                        apiKey = settings.wereadApiKey,
                        targetBoxId = settings.wereadBoxId,
                        lastSyncAt = settings.wereadLastSyncAt,
                        fullSync = false,
                        repository = app.repository,
                    )
                    if (res.isSuccess) {
                        app.settingsRepository.update { it.copy(wereadLastSyncAt = System.currentTimeMillis()) }
                    }
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val KEY_TARGET = "target"
        const val TARGET_READWISE = "readwise"
        const val TARGET_WEREAD = "weread"

        fun schedule(context: Context, target: String, intervalMinutes: Int) {
            val workName = "wantnote_reading_sync_$target"
            val manager = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                manager.cancelUniqueWork(workName)
                return
            }
            val safeMinutes = intervalMinutes.coerceAtLeast(15).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ExternalReadingAutoSyncWorker>(safeMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(Data.Builder().putString(KEY_TARGET, target).build())
                .build()
            manager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
