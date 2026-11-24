package com.davidzhu.wantnote.sync.dinox

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Dinox 开放接口的数据模型与请求构造（纯 JVM，可单测，不发网络请求）。
 *
 * 接口文档：https://dinox.info/guides/api/openapi
 * - 基础域名 `https://aisdk.chatgo.pro`，路径前缀 `/api/openapi`。
 * - 认证：请求头 `Authorization: <Token>`（无 Bearer 前缀）。
 * - 限流：每个 Token 每小时 200 次。
 * - 错误码：000000 成功 / 000001 参数错误 / 000005 服务器内部错误 /
 *   000006 资源不存在 / 000008 认证失败。
 */
object DinoxModels {
    const val BASE_URL = "https://aisdk.chatgo.pro"
    const val PATH_LIST_NOTES = "/api/openapi/listNotes"
    const val PATH_TEXT_INPUT = "/api/openapi/text/input"

    const val CODE_SUCCESS = "000000"
    const val CODE_AUTH_FAILED = "000008"

    private val SUCCESS_CODES = setOf("000000", "0", "200", "ok", "success")

    /** 认证头：文档要求直接放 Token，不加 Bearer 前缀。 */
    fun authHeader(token: String): String = token.trim()

    /** 错误码 → 面向用户的简短提示（不含任何敏感信息）。 */
    fun errorMessage(code: String): String = when (code) {
        "000001" -> "Dinox 参数错误"
        "000005" -> "Dinox 服务器内部错误"
        "000006" -> "Dinox 资源不存在"
        CODE_AUTH_FAILED -> "Token 无效或已过期（认证失败）"
        else -> "Dinox 返回错误（$code）"
    }

    /** 判断响应 code 是否属于成功态（兼容 000000 / 0 / 200 / ok / success）。 */
    fun isSuccessCode(code: String): Boolean {
        return code.trim().lowercase(Locale.US) in SUCCESS_CODES
    }

    /**
     * 校验并解包 Dinox HTTP 响应文本（防御性处理）：
     * - 容忍 UTF-8 BOM 与首尾空白；
     * - 接受标准 `{ code: "000000", data: ... }`、数值/变体成功码（0 / 200 / ok）、
     *   省略 code 但含有效 data/notes/list 的成功对象，以及顶层 JSON 数组；
     * - 遇到明确的业务错误码（如 000008）立即按 [errorMessage] 报错；
     * - 异常消息绝不包含原始响应体或 Token。
     */
    fun validateEnvelope(rawBody: String): Any {
        val cleaned = stripBomAndTrim(rawBody)
        if (cleaned.isEmpty()) {
            throw DinoxApiException("Dinox 返回了空响应，请稍后重试")
        }
        val parsed = try {
            JSONTokener(cleaned).nextValue()
        } catch (_: Throwable) {
            throw DinoxApiException("Dinox 返回了无法解析的数据")
        }
        return when (parsed) {
            is JSONObject -> {
                val code = parsed.safeString("code").trim()
                if (code.isNotEmpty()) {
                    if (!isSuccessCode(code)) {
                        throw DinoxApiException(errorMessage(code), code)
                    }
                    parsed
                } else {
                    val looksLikeSuccess = parsed.has("data") ||
                        parsed.has("notes") ||
                        parsed.has("list") ||
                        parsed.has("records") ||
                        parsed.has("items") ||
                        parsed.safeBoolean("success") ||
                        parsed.safeBoolean("ok")
                    if (!looksLikeSuccess) {
                        throw DinoxApiException("Dinox 返回了无法解析的数据")
                    }
                    parsed
                }
            }
            is JSONArray -> parsed
            else -> throw DinoxApiException("Dinox 返回了无法解析的数据")
        }
    }

    /**
     * `POST /api/openapi/listNotes` 请求体。
     * lastSyncIso 为空 = 全量；boxNames/boxIds 至多传用户配置的卡片盒。
     */
    fun listNotesBody(
        lastSyncIso: String?,
        boxNames: List<String> = emptyList(),
        boxIds: List<String> = emptyList(),
    ): String {
        val obj = JSONObject()
        if (!lastSyncIso.isNullOrBlank()) obj.put("lastSyncTime", lastSyncIso)
        if (boxNames.isNotEmpty()) obj.put("zettelBoxNames", JSONArray(boxNames))
        if (boxIds.isNotEmpty()) obj.put("zettelBoxIds", JSONArray(boxIds))
        return obj.toString()
    }

    /** 连接测试用：按一个必然不存在的卡片盒名过滤，只验证认证，不拉全量。 */
    fun probeBody(): String = listNotesBody(lastSyncIso = null, boxNames = listOf("__wantnote_probe__"))

    /**
     * `POST /api/openapi/text/input` 请求体（发送笔记到 Dinox）。
     * content 是 Markdown 正文；tags/zettelBoxNames 是字符串数组；
     * createdAt 用于保留原始创建时间（ISO 8601）。
     */
    fun textInputBody(
        content: String,
        title: String?,
        tags: List<String> = emptyList(),
        zettelBoxNames: List<String> = emptyList(),
        createdAtIso: String? = null,
    ): String {
        val obj = JSONObject()
            .put("content", content)
        if (!title.isNullOrBlank()) obj.put("title", title)
        if (tags.isNotEmpty()) obj.put("tags", JSONArray(tags))
        if (zettelBoxNames.isNotEmpty()) obj.put("zettelBoxNames", JSONArray(zettelBoxNames))
        if (!createdAtIso.isNullOrBlank()) obj.put("createdAt", createdAtIso)
        return obj.toString()
    }

    /** 解析 text/input 响应 data。 */
    fun parseCreateResult(json: String): String? {
        val root = runCatching { validateEnvelope(json) }.getOrNull() as? JSONObject ?: return null
        val data = root.optJSONObject("data") ?: return root.safeString("noteId", "id").takeIf { it.isNotBlank() }
        return data.safeString("noteId", "id").takeIf { it.isNotBlank() }
    }

    /** listNotes 响应里的一条笔记。 */
    data class DinoxNote(
        val noteId: String,
        val title: String,
        val content: String,
        val tags: List<String>,
        val zettelBoxes: List<String>,
        val createTimeIso: String,
        val updateTimeIso: String,
        val isAudio: Boolean,
        val audioUrl: String,
        val parentNoteId: String?,
        val isDel: Boolean,
    )

    data class DinoxListResult(
        val notes: List<DinoxNote>,
        /** 所有笔记里最新的 updateTime（ISO），用于增量游标；空列表时为 null。 */
        val latestUpdateIso: String?,
    )

    /**
     * 解析 listNotes 响应（防御性兼容多种合法成功形态）：
     * - 标准按日期分组：`{ code: "000000", data: [{ "date": "2026-03-04", "notes": [...] }] }`
     * - 扁平笔记数组：`{ code: "000000", data: [{ "noteId": ... }] }` 或顶层数组 `[...]`
     * - 对象包裹：`{ code: "000000", data: { "list"|"notes"|"records"|"items": [...] } }`
     * - 按日期键映射：`{ code: "000000", data: { "2026-03-04": [...] } }`
     * - 空 data（null / 空串 / 空数组 / 空对象）→ 返回空列表，不抛错
     * - zettelBoxes / tags 兼容字符串数组、对象数组（取 name/boxName 等）与单字符串
     */
    fun parseListNotes(json: String): DinoxListResult {
        val envelope = validateEnvelope(json)
        val noteObjects = ArrayList<JSONObject>()
        when (envelope) {
            is JSONObject -> {
                if (envelope.has("data")) {
                    if (!envelope.isNull("data")) {
                        extractNoteObjects(envelope.opt("data"), noteObjects, depth = 0)
                    }
                } else {
                    extractNoteObjects(envelope, noteObjects, depth = 0)
                }
            }
            is JSONArray -> {
                extractNoteObjects(envelope, noteObjects, depth = 0)
            }
        }

        val notes = ArrayList<DinoxNote>(noteObjects.size)
        var latest: String? = null
        var latestMs = 0L
        for (obj in noteObjects) {
            val noteId = obj.safeString("noteId", "id")
            val title = obj.safeString("title")
            val content = obj.safeString("content", "body", "markdown")
            val tags = obj.extractNameList("tags", "tagNames")
            val zettelBoxes = obj.extractNameList("zettelBoxes", "zettelBoxNames", "boxes")
            if (noteId.isBlank() && title.isBlank() && content.isBlank() && zettelBoxes.isEmpty()) {
                continue
            }
            val createIso = obj.safeTimestampIso("createTime", "createdAt")
            val updateIso = obj.safeTimestampIso("updateTime", "updatedAt")
            if (updateIso.isNotBlank()) {
                val ms = epochMsFromIso(updateIso)
                if (latest == null || ms >= latestMs) {
                    latest = updateIso
                    latestMs = ms
                }
            }
            notes += DinoxNote(
                noteId = noteId,
                title = title,
                content = content,
                tags = tags,
                zettelBoxes = zettelBoxes,
                createTimeIso = createIso,
                updateTimeIso = updateIso,
                isAudio = obj.safeBoolean("isAudio"),
                audioUrl = obj.safeString("audioUrl"),
                parentNoteId = obj.safeString("parentNoteId").takeIf { it.isNotBlank() },
                isDel = obj.safeBoolean("isDel", "isDeleted", "deleted"),
            )
        }
        return DinoxListResult(notes = notes, latestUpdateIso = latest)
    }

    private fun extractNoteObjects(node: Any?, out: MutableList<JSONObject>, depth: Int) {
        if (node == null || node === JSONObject.NULL || depth > 6) return
        when (node) {
            is String -> {
                val trimmed = stripBomAndTrim(node)
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    val inner = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()
                    if (inner is JSONObject || inner is JSONArray) {
                        extractNoteObjects(inner, out, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val item = node.opt(i) ?: continue
                    when (item) {
                        is JSONObject -> {
                            val groupNotes = item.opt("notes")
                            if (groupNotes is JSONArray && !looksLikeSingleNote(item)) {
                                extractNoteObjects(groupNotes, out, depth + 1)
                            } else if (looksLikeContainerObject(item)) {
                                extractFromContainerObject(item, out, depth + 1)
                            } else if (looksLikeSingleNote(item)) {
                                out += item
                            }
                        }
                        is JSONArray -> extractNoteObjects(item, out, depth + 1)
                    }
                }
            }
            is JSONObject -> {
                if (looksLikeContainerObject(node)) {
                    extractFromContainerObject(node, out, depth + 1)
                } else if (looksLikeSingleNote(node)) {
                    out += node
                } else {
                    // 兜底：例如按日期键映射 `{ "2026-03-04": [ ... ] }`。
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (node.isNull(key)) continue
                        val value = node.opt(key)
                        if (value is JSONArray || value is JSONObject) {
                            extractNoteObjects(value, out, depth + 1)
                        }
                    }
                }
            }
        }
    }

    private fun extractFromContainerObject(obj: JSONObject, out: MutableList<JSONObject>, depth: Int) {
        val wrapperKeys = listOf("data", "notes", "list", "records", "items", "rows")
        for (key in wrapperKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val candidate = obj.opt(key)
                if (candidate is JSONArray || candidate is JSONObject || candidate is String) {
                    extractNoteObjects(candidate, out, depth)
                    return
                }
            }
        }
    }

    private fun looksLikeContainerObject(obj: JSONObject): Boolean {
        if (looksLikeSingleNote(obj)) return false
        val wrapperKeys = listOf("data", "notes", "list", "records", "items", "rows")
        return wrapperKeys.any { key ->
            if (!obj.has(key) || obj.isNull(key)) return@any false
            val v = obj.opt(key)
            v is JSONArray || v is JSONObject || v is String
        }
    }

    private fun looksLikeSingleNote(obj: JSONObject): Boolean {
        return obj.has("noteId") ||
            obj.has("content") ||
            obj.has("title") ||
            obj.has("zettelBoxes") ||
            obj.has("zettelBoxNames") ||
            obj.has("body") ||
            obj.has("markdown") ||
            (obj.has("id") && (obj.has("createTime") || obj.has("updateTime") || obj.has("tags")))
    }

    private fun stripBomAndTrim(raw: String): String {
        return raw.removePrefix("\uFEFF").trim()
    }

    private fun JSONObject.safeString(vararg keys: String): String {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key) ?: continue
            if (value === JSONObject.NULL) continue
            if (value is JSONObject || value is JSONArray) continue
            val str = value.toString()
            if (str.isNotEmpty()) return str
        }
        return ""
    }

    private fun JSONObject.safeBoolean(vararg keys: String): Boolean {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            return when (val value = opt(key)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> when (value.trim().lowercase(Locale.US)) {
                    "true", "1", "yes" -> true
                    "false", "0", "no", "" -> false
                    else -> false
                }
                else -> false
            }
        }
        return false
    }

    private fun JSONObject.safeTimestampIso(vararg keys: String): String {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key) ?: continue
            if (value === JSONObject.NULL) continue
            return when (value) {
                is Number -> {
                    val raw = value.toLong()
                    if (raw <= 0L) "" else {
                        val ms = if (raw < 10_000_000_000L) raw * 1000L else raw
                        isoFromEpochMs(ms)
                    }
                }
                is String -> value.trim()
                else -> ""
            }
        }
        return ""
    }

    private fun JSONObject.extractNameList(vararg keys: String): List<String> {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val raw = opt(key) ?: continue
            val extracted = when (raw) {
                is JSONArray -> {
                    val out = ArrayList<String>(raw.length())
                    for (i in 0 until raw.length()) {
                        if (raw.isNull(i)) continue
                        when (val item = raw.opt(i)) {
                            is String -> item.trim().takeIf { it.isNotEmpty() }?.let { out += it }
                            is JSONObject -> {
                                val name = item.safeString(
                                    "name", "boxName", "zettelBoxName", "tagName", "path", "title", "label",
                                ).trim()
                                if (name.isNotEmpty()) out += name
                            }
                        }
                    }
                    out
                }
                is String -> raw.split('、', ',', '，', ';', '；', '\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                is JSONObject -> {
                    val name = raw.safeString(
                        "name", "boxName", "zettelBoxName", "tagName", "path", "title", "label",
                    ).trim()
                    if (name.isNotEmpty()) listOf(name) else emptyList()
                }
                else -> emptyList()
            }
            if (extracted.isNotEmpty()) return extracted.distinct()
        }
        return emptyList()
    }

    // ---- ISO 8601 时间（线程安全，支持毫秒/无毫秒/时区偏移/空格分隔/时间戳） ------

    private val isoWriteFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val isoReadPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
    )

    private val isoReadFormats = ThreadLocal.withInitial {
        isoReadPatterns.map { pattern ->
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }

    private val fracSecondsRegex = Regex("""(\.\d{1,9})""")
    private val colonOffsetRegex = Regex("""([+-]\d{2}):(\d{2})$""")

    fun isoFromEpochMs(ms: Long): String {
        return isoWriteFormat.get()!!.format(Date(ms))
    }

    /** 解析失败返回 0，调用方按 0 处理（不中断整批同步）。 */
    fun epochMsFromIso(iso: String): Long {
        val trimmed = iso.trim()
        if (trimmed.isEmpty()) return 0L
        // 兼容纯数字 epoch 毫秒/秒。
        trimmed.toLongOrNull()?.let { numeric ->
            if (numeric > 0L) {
                return if (numeric < 10_000_000_000L) numeric * 1000L else numeric
            }
        }
        // 把 +08:00 规范为 +0800，把 1..9 位小数秒规范为 3 位毫秒，兼容 SimpleDateFormat。
        var normalized = colonOffsetRegex.replace(trimmed) { match ->
            match.groupValues[1] + match.groupValues[2]
        }
        normalized = fracSecondsRegex.replace(normalized) { match ->
            val digits = match.groupValues[1].drop(1)
            "." + digits.padEnd(3, '0').take(3)
        }
        isoReadFormats.get()!!.forEach { format ->
            try {
                format.parse(normalized)?.time?.let { return it }
            } catch (_: ParseException) {
                // try next pattern
            }
        }
        return 0L
    }

    /** 用户输入的卡片盒名称：支持顿号/逗号/分号/空白分隔多个名称。 */
    fun splitBoxNames(raw: String): List<String> {
        return raw.split('、', ',', '，', ';', '；', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

/** Dinox 调用失败；message 是面向用户的简短非敏感提示。 */
class DinoxApiException(message: String, val code: String = "") : Exception(message)
