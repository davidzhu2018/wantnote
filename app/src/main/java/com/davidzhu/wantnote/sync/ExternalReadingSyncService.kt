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
 * 0.19.0 / 0.20.0：生态互联外部同步服务：
 * 1. Flomo 官方 Webhook 单向发送（供主页卡片长按与多选队列发送使用）；
 * 2. Readwise 单向同步（API v2 Export，支持按类别映射到不同卡片盒或沿用 Readwise 分类名、
 *    按类别自动打标签默认 Readwise、全量/增量、手动/WorkManager 自动同步、实时进度回调）；
 * 3. 微信读书单向同步（官方微信读书 Skill Agent API Gateway: POST https://i.weread.qq.com/api/agent/gateway，
 *    携带 Authorization: Bearer <API_KEY> 与 skill_version=1.0.6，拉取 /user/notebooks、
 *    /book/bookmarklist 与 /review/list/mine，支持全量/增量、自动同步与实时进度回调）。
 */
object ExternalReadingSyncService {

    /** 特殊占位值：表示沿用 Readwise 分类名称作为本地卡片盒名称（不存在时自动创建）。 */
    const val USE_CATEGORY_BOX = "__USE_CATEGORY__"

    /** Readwise 同步笔记的默认标签。 */
    const val DEFAULT_READWISE_TAG = "Readwise"

    /** 微信读书官方 Skill Agent Gateway 地址与协议版本（参考 Tencent/WeChatReading）。 */
    internal const val WEREAD_GATEWAY_URL = "https://i.weread.qq.com/api/agent/gateway"
    internal const val WEREAD_SKILL_VERSION = "1.0.6"

    /** Readwise 标准类别（支持按类别映射到想记不同卡片盒或沿用分类名）。 */
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
                setRequestProperty("User-Agent", "WantNote-Android/0.20.0")
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

    fun parseCategoryTagMap(json: String): Map<String, String> = parseCategoryBoxMap(json)

    fun serializeCategoryTagMap(map: Map<String, String>): String = serializeCategoryBoxMap(map)

    fun resolveReadwiseTagForCategory(tagMap: Map<String, String>, category: String): String {
        val key = category.trim().lowercase()
        return tagMap[key]?.trim()?.ifBlank { null } ?: DEFAULT_READWISE_TAG
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
                setRequestProperty("User-Agent", "WantNote-Android/0.20.0")
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
        categoryTagsJson: String = "",
        lastSyncAt: Long,
        fullSync: Boolean,
        repository: NoteRepository,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ): Result<ReadingSyncResult> = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.isEmpty()) return@withContext Result.failure(IllegalStateException("请先填写 Readwise Access Token"))
        runCatching {
            val boxMap = parseCategoryBoxMap(categoryBoxesJson)
            val tagMap = parseCategoryTagMap(categoryTagsJson)
            val updatedAfter = if (!fullSync && lastSyncAt > 0L) isoUtc(lastSyncAt) else null
            val existingFingerprints = repository.observeAll().first()
                .map { fingerprint(it.title, it.body) }
                .toMutableSet()

            onProgress(0, 0, "正在连接 Readwise 拉取摘录列表…")
            val allDocs = ArrayList<ReadwiseDoc>()
            var cursor: String? = null
            var pages = 0

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
                allDocs.addAll(parsed.docs)
                cursor = parsed.nextCursor
                pages += 1
                onProgress(0, allDocs.size, "已拉取第 $pages 页，共 ${allDocs.size} 篇摘录…")
            } while (!cursor.isNullOrBlank() && pages < 12)

            var imported = 0
            var skipped = 0
            val now = System.currentTimeMillis()
            val total = allDocs.size
            val resolvedCategoryBoxCache = HashMap<String, String>()

            allDocs.forEachIndexed { index, doc ->
                onProgress(index + 1, total, "正在写入 (${index + 1}/$total)：${doc.title}")
                val fp = fingerprint(doc.title, doc.markdown)
                if (fp in existingFingerprints) {
                    skipped += 1
                    return@forEachIndexed
                }
                val catKey = doc.category.lowercase()
                val mappedSetting = boxMap[catKey].orEmpty()
                val targetBox = when {
                    mappedSetting == USE_CATEGORY_BOX -> {
                        resolvedCategoryBoxCache.getOrPut(catKey) {
                            val boxName = doc.category.trim().ifBlank { "Readwise" }
                            (repository.getBoxByName(boxName) ?: repository.createBox(boxName))?.id.orEmpty()
                        }
                    }
                    else -> mappedSetting
                }
                val note = Note(
                    id = UUID.randomUUID().toString(),
                    title = doc.title.take(120),
                    body = doc.markdown,
                    createdAt = now,
                    updatedAt = now,
                    boxId = targetBox,
                )
                repository.insertImportedMarkdown(note, doc.markdown)
                val tagPath = resolveReadwiseTagForCategory(tagMap, doc.category)
                if (tagPath.isNotBlank()) {
                    repository.addTagToNote(note.id, tagPath)
                }
                existingFingerprints += fp
                imported += 1
            }

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

    // ==================== 3. 微信读书单向同步（官方 Agent Gateway） ====================

    internal fun buildWeReadGatewayPayload(apiName: String, params: Map<String, Any> = emptyMap()): JSONObject {
        val body = JSONObject()
        body.put("api_name", apiName)
        body.put("skill_version", WEREAD_SKILL_VERSION)
        params.forEach { (k, v) -> body.put(k, v) }
        return body
    }

    suspend fun testWeReadApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext Result.failure(IllegalStateException("请先填写微信读书 API Key"))
        runCatching {
            val payload = buildWeReadGatewayPayload("/user/notebooks", mapOf("count" to 1))
            val raw = postWeReadGateway(key, payload)
            val root = JSONObject(raw)
            val errcode = root.optInt("errcode", 0)
            if (errcode != 0) {
                val errmsg = root.optString("errmsg").ifBlank { "错误码 $errcode" }
                throw IllegalStateException("微信读书 API Key 验证失败：$errmsg")
            }
            val totalBooks = root.optInt("totalBookCount", -1)
            if (totalBooks >= 0) {
                "微信读书连接成功（检测到 $totalBooks 本有笔记的书籍）"
            } else {
                "微信读书 API Key 验证通过"
            }
        }
    }

    suspend fun syncWeRead(
        apiKey: String,
        targetBoxId: String,
        lastSyncAt: Long,
        fullSync: Boolean,
        repository: NoteRepository,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ): Result<ReadingSyncResult> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext Result.failure(IllegalStateException("请先填写微信读书 API Key"))
        runCatching {
            onProgress(0, 0, "正在连接微信读书拉取书架笔记列表…")
            val allBooks = ArrayList<WeReadBook>()
            var lastSort: Long? = null
            var pages = 0
            do {
                val params = LinkedHashMap<String, Any>()
                params["count"] = 50
                if (lastSort != null) params["lastSort"] = lastSort
                val raw = postWeReadGateway(key, buildWeReadGatewayPayload("/user/notebooks", params))
                val root = JSONObject(raw)
                val errcode = root.optInt("errcode", 0)
                if (errcode != 0) {
                    val errmsg = root.optString("errmsg").ifBlank { "错误码 $errcode" }
                    throw IllegalStateException("微信读书拉取笔记本失败：$errmsg")
                }
                val pageBooks = parseWeReadNotebooks(raw)
                allBooks.addAll(pageBooks)
                val hasMore = root.optInt("hasMore", 0) == 1
                val nextSort = pageBooks.lastOrNull()?.sortTime?.takeIf { it > 0L }
                if (!hasMore || nextSort == null || nextSort == lastSort) break
                lastSort = nextSort
                pages += 1
                onProgress(0, allBooks.size, "已获取 ${allBooks.size} 本书籍…")
            } while (pages < 20)

            val sinceSec = if (!fullSync && lastSyncAt > 0L) lastSyncAt / 1000L else 0L
            val targetBooks = allBooks.filter { book ->
                sinceSec <= 0L || book.sortTime <= 0L || book.sortTime >= sinceSec
            }
            val skippedByIncremental = allBooks.size - targetBooks.size

            val existingFingerprints = repository.observeAll().first()
                .map { fingerprint(it.title, it.body) }
                .toMutableSet()

            var imported = 0
            var skipped = skippedByIncremental
            val now = System.currentTimeMillis()
            val total = targetBooks.size

            targetBooks.forEachIndexed { index, book ->
                onProgress(index + 1, total, "正在同步 (${index + 1}/$total)：《${book.title}》")
                val bmRaw = runCatching {
                    postWeReadGateway(
                        key,
                        buildWeReadGatewayPayload("/book/bookmarklist", mapOf("bookId" to book.bookId)),
                    )
                }.getOrNull()
                val reviewRaw = runCatching {
                    postWeReadGateway(
                        key,
                        buildWeReadGatewayPayload(
                            "/review/list/mine",
                            mapOf("bookid" to book.bookId, "count" to 50, "synckey" to 0),
                        ),
                    )
                }.getOrNull()

                val highlights = if (!bmRaw.isNullOrBlank()) parseWeReadBookmarks(bmRaw) else emptyList()
                val reviews = if (!reviewRaw.isNullOrBlank()) parseWeReadReviews(reviewRaw) else emptyList()

                val body = formatWeReadBookMarkdown(book, highlights, reviews)
                val title = "《${book.title}》微信读书划线"
                val fp = fingerprint(title, body)
                if (fp in existingFingerprints) {
                    skipped += 1
                    return@forEachIndexed
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

    private fun postWeReadGateway(apiKey: String, payload: JSONObject): String {
        val conn = (URL(WEREAD_GATEWAY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WantNote-Android/0.20.0")
        }
        return try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                it.write(payload.toString())
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                val hint = runCatching { JSONObject(text).optString("errmsg") }.getOrNull().orEmpty()
                throw IllegalStateException(
                    if (hint.isNotBlank()) "微信读书网关错误（HTTP $code）：$hint"
                    else "微信读书接口返回 HTTP $code，请核对 API Key（wrk- 开头）是否有效",
                )
            }
            text
        } finally {
            conn.disconnect()
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
        val chapterTitle: String = "",
        val thought: String = "",
    )

    internal data class WeReadReview(
        val content: String,
        val abstractText: String = "",
        val chapterName: String = "",
    )

    internal fun parseWeReadNotebooks(json: String): List<WeReadBook> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("books") ?: root.optJSONArray("data") ?: JSONArray()
        val out = ArrayList<WeReadBook>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val bookObj = item.optJSONObject("book") ?: item
            val bookId = item.optString("bookId").ifBlank { bookObj.optString("bookId") }.trim()
            val title = bookObj.optString("title").trim()
            if (bookId.isEmpty() || title.isEmpty()) continue
            val author = bookObj.optString("author").trim()
            val noteCount = item.optInt("noteCount", 0) + item.optInt("reviewCount", 0)
            val sortTime = item.optLong("sort", 0L)
            out += WeReadBook(bookId, title, author, noteCount, sortTime)
        }
        return out
    }

    internal fun parseWeReadBookmarks(json: String): List<WeReadHighlight> {
        val root = JSONObject(json)
        val chapterMap = HashMap<Int, String>()
        val chapters = root.optJSONArray("chapters") ?: JSONArray()
        for (i in 0 until chapters.length()) {
            val ch = chapters.optJSONObject(i) ?: continue
            val uid = ch.optInt("chapterUid", -1)
            val title = ch.optString("title").trim()
            if (uid >= 0 && title.isNotEmpty()) chapterMap[uid] = title
        }
        val arr = root.optJSONArray("updated") ?: root.optJSONArray("bookmarks") ?: JSONArray()
        val out = ArrayList<WeReadHighlight>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val text = item.optString("markText").trim()
            val chTitle = chapterMap[item.optInt("chapterUid", -1)].orEmpty()
            val thought = item.optString("abstract").trim()
            if (text.isNotEmpty()) out += WeReadHighlight(text, chTitle, thought)
        }
        return out
    }

    internal fun parseWeReadReviews(json: String): List<WeReadReview> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("reviews") ?: JSONArray()
        val out = ArrayList<WeReadReview>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val rev = item.optJSONObject("review") ?: item
            val content = rev.optString("content").trim()
            val abs = rev.optString("abstract").trim()
            val chName = rev.optString("chapterName").trim()
            if (content.isNotEmpty() || abs.isNotEmpty()) {
                out += WeReadReview(content = content, abstractText = abs, chapterName = chName)
            }
        }
        return out
    }

    internal fun formatWeReadBookMarkdown(
        book: WeReadBook,
        highlights: List<WeReadHighlight>,
        reviews: List<WeReadReview>,
    ): String = buildString {
        if (book.author.isNotBlank()) append("作者：${book.author}\n\n")
        if (highlights.isEmpty() && reviews.isEmpty()) {
            append("（来自微信读书，共 ${book.noteCount} 条划线/想法）")
        } else {
            if (highlights.isNotEmpty()) {
                val grouped = highlights.groupBy { it.chapterTitle }
                grouped.forEach { (chapter, list) ->
                    if (chapter.isNotBlank()) append("### $chapter\n\n")
                    list.forEach { h ->
                        append("> ").append(h.markText.replace("\n", "\n> ")).append("\n\n")
                        if (h.thought.isNotBlank()) {
                            append("想法：").append(h.thought).append("\n\n")
                        }
                    }
                }
            }
            if (reviews.isNotEmpty()) {
                append("### 个人想法与书评\n\n")
                reviews.forEach { r ->
                    if (r.abstractText.isNotBlank()) {
                        append("> ").append(r.abstractText.replace("\n", "\n> ")).append("\n\n")
                    }
                    if (r.content.isNotBlank()) {
                        append(r.content).append("\n\n")
                    }
                }
            }
        }
    }.trim()

    // ==================== Helpers ====================

    private fun httpGet(urlStr: String, headers: Map<String, String>): String? {
        return runCatching {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "WantNote-Android/0.20.0")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (conn.responseCode !in 200..299) return null
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun isoUtc(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(ms))
    }

    private fun fingerprint(title: String, body: String): String {
        val t = title.trim().lowercase()
        val b = body.trim().take(240).lowercase()
        return "$t||$b"
    }
}

/**
 * 0.19.0 / 0.20.0：Readwise 与微信读书后台定时自动同步 Worker（WorkManager）。
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
                        categoryTagsJson = settings.readwiseCategoryTagsJson,
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
