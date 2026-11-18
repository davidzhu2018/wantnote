package com.davidzhu.wantnote.sync.dinox

import org.json.JSONArray
import org.json.JSONObject
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
        val root = JSONObject(json)
        if (root.optString("code") != CODE_SUCCESS) return null
        return root.optJSONObject("data")?.optString("noteId")?.takeIf { it.isNotBlank() }
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
     * 解析 listNotes 响应：data 是按日期分组的数组
     * `[{ "date": "2026-03-04", "notes": [ … ] }]`。
     */
    fun parseListNotes(json: String): DinoxListResult {
        val root = JSONObject(json)
        require(root.optString("code") == CODE_SUCCESS) { errorMessage(root.optString("code")) }
        val data = root.optJSONArray("data") ?: JSONArray()
        val notes = ArrayList<DinoxNote>()
        var latest: String? = null
        for (i in 0 until data.length()) {
            val group = data.optJSONObject(i) ?: continue
            val arr = group.optJSONArray("notes") ?: continue
            for (j in 0 until arr.length()) {
                val obj = arr.optJSONObject(j) ?: continue
                val updateIso = obj.optString("updateTime")
                if (updateIso.isNotBlank() &&
                    (latest == null || epochMsFromIso(updateIso) >= epochMsFromIso(latest))
                ) {
                    latest = updateIso
                }
                notes += DinoxNote(
                    noteId = obj.optString("noteId"),
                    title = obj.optString("title"),
                    content = obj.optString("content"),
                    tags = obj.optJSONArray("tags").toStringList(),
                    zettelBoxes = obj.optJSONArray("zettelBoxes").toStringList(),
                    createTimeIso = obj.optString("createTime"),
                    updateTimeIso = updateIso,
                    isAudio = obj.optBoolean("isAudio"),
                    audioUrl = obj.optString("audioUrl"),
                    parentNoteId = obj.optString("parentNoteId").takeIf {
                        obj.has("parentNoteId") && it.isNotBlank()
                    },
                    isDel = obj.optBoolean("isDel"),
                )
            }
        }
        return DinoxListResult(notes = notes, latestUpdateIso = latest)
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx ->
            val value = optString(idx)
            value.takeIf { it.isNotBlank() }
        }
    }

    // ---- ISO 8601 时间（Dinox 统一返回 `2026-03-04T12:00:00.000Z`） ------

    private val isoWrite = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val isoReadVariants = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    fun isoFromEpochMs(ms: Long): String {
        return isoWrite.format(Date(ms))
    }

    /** 解析失败返回 0，调用方按 0 处理（不中断整批同步）。 */
    fun epochMsFromIso(iso: String): Long {
        if (iso.isBlank()) return 0L
        isoReadVariants.forEach { format ->
            try {
                format.parse(iso)?.time?.let { return it }
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
