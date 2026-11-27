package com.davidzhu.wantnote.sync

import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteRepository
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
import java.nio.charset.StandardCharsets
import java.util.UUID

/** 生态互联 MCP 平台类型。 */
enum class McpPlatform(
    val label: String,
    val docUrl: String,
    val defaultEndpointHint: String,
) {
    FLOMO(
        label = "Flomo",
        docUrl = "https://flomoapp.com/mine?source=incoming_webhook",
        defaultEndpointHint = "https://your-flomo-mcp-server/mcp",
    ),
    DEDAO(
        label = "得到大脑",
        docUrl = "https://doc.biji.com/docs/FmYfw0yXsiXdTtkVzhLcuX8lnxd/",
        defaultEndpointHint = "https://your-getnote-mcp-server/mcp",
    ),
}

data class McpConfig(
    val platform: McpPlatform,
    val endpointUrl: String,
    val token: String,
    val webhookUrl: String = "",
    val lastSyncAt: Long = 0L,
)

data class McpToolInfo(
    val name: String,
    val description: String = "",
)

data class McpRemoteNote(
    val title: String,
    val body: String,
)

data class McpConnectionResult(
    val ok: Boolean,
    val tools: List<McpToolInfo>,
    val message: String,
)

data class McpSyncSummary(
    val pulled: Int,
    val pushed: Int,
    val skipped: Int,
    val message: String,
)

/**
 * 0.18.2：标准远程 MCP（Streamable HTTP / JSON-RPC 2.0）客户端与双向同步服务，
 * 同时兼容 Flomo 官方 Incoming Webhook 推送，服务于「Flomo」与「得到大脑（Get笔记）」生态互联。
 */
object McpSyncService {

    private val READ_TOOL_KEYWORDS = listOf("list", "get", "search", "fetch", "query", "read", "memo", "note")
    private val WRITE_TOOL_KEYWORDS = listOf("create", "add", "save", "write", "new", "post", "append", "put")

    /** 执行标准 MCP `initialize` + `tools/list` 握手并发现远端可用工具。 */
    suspend fun testConnection(config: McpConfig): McpConnectionResult = withContext(Dispatchers.IO) {
        val url = config.endpointUrl.trim()
        if (url.isEmpty()) {
            if (config.platform == McpPlatform.FLOMO && config.webhookUrl.isNotBlank()) {
                return@withContext McpConnectionResult(
                    ok = true,
                    tools = emptyList(),
                    message = "已配置 Flomo Webhook（支持单向推送；配置 Flomo MCP 地址后可开启双向同步）",
                )
            }
            return@withContext McpConnectionResult(false, emptyList(), "请先填写 MCP 服务器地址（HTTPS URL）")
        }
        val initRes = callJsonRpc(
            url = url,
            token = config.token.trim(),
            sessionId = null,
            id = 1,
            method = "initialize",
            params = JSONObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject())
                .put(
                    "clientInfo",
                    JSONObject().put("name", "WantNote-Android").put("version", "0.18.2"),
                ),
        )
        if (initRes.error != null) {
            return@withContext McpConnectionResult(false, emptyList(), "连接失败：${initRes.error}")
        }
        val listRes = callJsonRpc(
            url = url,
            token = config.token.trim(),
            sessionId = initRes.sessionId,
            id = 2,
            method = "tools/list",
            params = JSONObject(),
        )
        if (listRes.error != null) {
            return@withContext McpConnectionResult(false, emptyList(), "获取工具列表失败：${listRes.error}")
        }
        val tools = parseToolsList(listRes.result)
        McpConnectionResult(
            ok = true,
            tools = tools,
            message = if (tools.isEmpty()) {
                "连接成功（未发现工具声明）"
            } else {
                "连接成功，发现 ${tools.size} 个 MCP 工具：${tools.take(5).joinToString("、") { it.name }}"
            },
        )
    }

    /** 执行拉取 / 推送 / 双向同步。 */
    suspend fun sync(
        config: McpConfig,
        repository: NoteRepository,
        pull: Boolean,
        push: Boolean,
    ): Result<McpSyncSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = config.endpointUrl.trim()
            val token = config.token.trim()
            val webhook = config.webhookUrl.trim()

            if (endpoint.isEmpty() && !(config.platform == McpPlatform.FLOMO && webhook.isNotEmpty() && push && !pull)) {
                throw IllegalStateException("请先填写 ${config.platform.label} MCP 服务器地址")
            }

            var sessionId: String? = null
            var tools: List<McpToolInfo> = emptyList()
            if (endpoint.isNotEmpty()) {
                val initRes = callJsonRpc(
                    url = endpoint,
                    token = token,
                    sessionId = null,
                    id = 1,
                    method = "initialize",
                    params = JSONObject()
                        .put("protocolVersion", "2025-03-26")
                        .put("capabilities", JSONObject())
                        .put("clientInfo", JSONObject().put("name", "WantNote-Android").put("version", "0.18.2")),
                )
                if (initRes.error != null) throw IllegalStateException("MCP 握手失败：${initRes.error}")
                sessionId = initRes.sessionId
                val listRes = callJsonRpc(endpoint, token, sessionId, 2, "tools/list", JSONObject())
                if (listRes.error == null) {
                    tools = parseToolsList(listRes.result)
                }
            }

            var pulled = 0
            var pushed = 0
            var skipped = 0

            val existingNotes = repository.observeAll().first()
            val existingFingerprints = existingNotes.map { fingerprint(it.title, it.body) }.toMutableSet()

            // 1. 拉取（远端 → 想记）
            if (pull) {
                val readTool = pickReadTool(tools)
                    ?: throw IllegalStateException("远端 MCP 未提供读取/列表工具（已发现：${tools.joinToString { it.name }.ifBlank { "无" }}）")
                val args = JSONObject()
                    .put("limit", 30)
                    .put("page_size", 30)
                    .put("query", "")
                val callRes = callJsonRpc(
                    url = endpoint,
                    token = token,
                    sessionId = sessionId,
                    id = 3,
                    method = "tools/call",
                    params = JSONObject().put("name", readTool.name).put("arguments", args),
                )
                if (callRes.error != null) throw IllegalStateException("拉取失败：${callRes.error}")
                val remoteNotes = extractRemoteNotes(callRes.result)
                val now = System.currentTimeMillis()
                for (rn in remoteNotes) {
                    val fp = fingerprint(rn.title, rn.body)
                    if (fp in existingFingerprints) {
                        skipped += 1
                        continue
                    }
                    val note = Note(
                        id = UUID.randomUUID().toString(),
                        title = rn.title.take(60),
                        body = rn.body,
                        createdAt = now,
                        updatedAt = now,
                    )
                    repository.insertImportedMarkdown(note, rn.body)
                    existingFingerprints += fp
                    pulled += 1
                }
            }

            // 2. 推送（想记 → 远端，仅推送上次同步之后新建/修改的笔记，最多单次 20 篇避免限流）
            if (push) {
                val candidates = existingNotes
                    .filter { it.updatedAt > config.lastSyncAt && (it.title.isNotBlank() || it.body.isNotBlank()) }
                    .sortedByDescending { it.updatedAt }
                    .take(20)
                val writeTool = pickWriteTool(tools)
                if (writeTool == null && !(config.platform == McpPlatform.FLOMO && webhook.isNotEmpty())) {
                    throw IllegalStateException("远端 MCP 未提供写入工具，且未配置 Flomo Webhook")
                }
                var reqId = 10
                for (note in candidates) {
                    val content = buildString {
                        if (note.title.isNotBlank()) {
                            append(note.title.trim())
                            if (note.body.isNotBlank()) append("\n\n")
                        }
                        append(note.body.trim())
                    }.trim()
                    if (content.isEmpty()) continue
                    if (writeTool != null && endpoint.isNotEmpty()) {
                        val args = JSONObject()
                            .put("title", note.title.ifBlank { content.lineSequence().firstOrNull().orEmpty().take(30) })
                            .put("content", content)
                            .put("text", content)
                            .put("memo", content)
                        val res = callJsonRpc(
                            url = endpoint,
                            token = token,
                            sessionId = sessionId,
                            id = reqId++,
                            method = "tools/call",
                            params = JSONObject().put("name", writeTool.name).put("arguments", args),
                        )
                        if (res.error == null) pushed += 1
                    } else if (config.platform == McpPlatform.FLOMO && webhook.isNotEmpty()) {
                        if (postFlomoWebhook(webhook, content)) pushed += 1
                    }
                }
            }

            val summaryText = buildString {
                append("${config.platform.label} 同步完成：")
                val parts = ArrayList<String>()
                if (pull) parts += "拉取 $pulled 篇"
                if (push) parts += "推送 $pushed 篇"
                if (skipped > 0) parts += "跳过重复 $skipped 篇"
                append(parts.joinToString("，"))
            }
            McpSyncSummary(pulled = pulled, pushed = pushed, skipped = skipped, message = summaryText)
        }
    }

    private fun postFlomoWebhook(webhookUrl: String, content: String): Boolean {
        return runCatching {
            val conn = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "WantNote-Android/0.18.2")
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(JSONObject().put("content", content).toString())
                }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    private data class JsonRpcOutcome(
        val result: JSONObject? = null,
        val sessionId: String? = null,
        val error: String? = null,
    )

    private fun callJsonRpc(
        url: String,
        token: String,
        sessionId: String?,
        id: Int,
        method: String,
        params: JSONObject,
    ): JsonRpcOutcome {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12_000
                readTimeout = 25_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json, text/event-stream")
                setRequestProperty("User-Agent", "WantNote-Android/0.18.2")
                if (token.isNotEmpty()) {
                    setRequestProperty("Authorization", if (token.startsWith("Bearer ", true)) token else "Bearer $token")
                }
                if (!sessionId.isNullOrBlank()) {
                    setRequestProperty("Mcp-Session-Id", sessionId)
                }
            }
            try {
                val payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("method", method)
                    .put("params", params)
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(payload.toString()) }
                val code = conn.responseCode
                val respSession = conn.getHeaderField("Mcp-Session-Id") ?: sessionId
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.let { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
                if (code !in 200..299) {
                    return@runCatching JsonRpcOutcome(error = "HTTP $code ${raw.take(120)}")
                }
                val jsonBody = unwrapSseOrJson(raw)
                val root = JSONObject(jsonBody)
                val err = root.optJSONObject("error")
                if (err != null) {
                    return@runCatching JsonRpcOutcome(error = err.optString("message").ifBlank { err.toString() })
                }
                JsonRpcOutcome(result = root.optJSONObject("result"), sessionId = respSession)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e ->
            JsonRpcOutcome(error = e.message ?: e.javaClass.simpleName)
        }
    }

    /** 兼容 Streamable HTTP 的 SSE (`data: {...}`) 与纯 JSON 响应。 */
    internal fun unwrapSseOrJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        val dataLines = trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.startsWith("{") }
            .toList()
        return dataLines.lastOrNull() ?: trimmed
    }

    internal fun parseToolsList(result: JSONObject?): List<McpToolInfo> {
        val arr = result?.optJSONArray("tools") ?: return emptyList()
        val out = ArrayList<McpToolInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isNotEmpty()) {
                out += McpToolInfo(name = name, description = obj.optString("description").trim())
            }
        }
        return out
    }

    internal fun pickReadTool(tools: List<McpToolInfo>): McpToolInfo? {
        return tools.firstOrNull { t ->
            val n = t.name.lowercase()
            (n.contains("list") || n.contains("search") || n.contains("get") || n.contains("fetch")) &&
                !WRITE_TOOL_KEYWORDS.any { n.contains(it) }
        } ?: tools.firstOrNull { t -> READ_TOOL_KEYWORDS.any { t.name.lowercase().contains(it) } }
    }

    internal fun pickWriteTool(tools: List<McpToolInfo>): McpToolInfo? {
        return tools.firstOrNull { t ->
            val n = t.name.lowercase()
            WRITE_TOOL_KEYWORDS.any { n.contains(it) }
        }
    }

    /** 从 MCP `tools/call` 的 `content` 数组中提取远端笔记列表（兼容 JSON 数组/对象及纯文本）。 */
    internal fun extractRemoteNotes(result: JSONObject?): List<McpRemoteNote> {
        val contentArr = result?.optJSONArray("content") ?: return emptyList()
        val out = ArrayList<McpRemoteNote>()
        for (i in 0 until contentArr.length()) {
            val item = contentArr.optJSONObject(i) ?: continue
            val text = item.optString("text").trim()
            if (text.isEmpty()) continue
            val parsed = parseNotesFromPayload(text)
            if (parsed.isNotEmpty()) {
                out += parsed
            } else {
                val firstLine = text.lineSequence().firstOrNull().orEmpty().take(40)
                out += McpRemoteNote(title = firstLine, body = text)
            }
        }
        return out
    }

    private fun parseNotesFromPayload(text: String): List<McpRemoteNote> {
        val trimmed = text.trim()
        val array: JSONArray? = when {
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()
            trimmed.startsWith("{") -> runCatching {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("notes")
                    ?: obj.optJSONArray("memos")
                    ?: obj.optJSONArray("items")
                    ?: obj.optJSONArray("data")
                    ?: obj.optJSONObject("data")?.optJSONArray("list")
            }.getOrNull()
            else -> null
        }
        if (array == null) return emptyList()
        val out = ArrayList<McpRemoteNote>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val body = obj.optString("content").ifBlank {
                obj.optString("body").ifBlank { obj.optString("text").ifBlank { obj.optString("summary") } }
            }.trim()
            val title = obj.optString("title").trim()
                .ifBlank { body.lineSequence().firstOrNull().orEmpty().take(40) }
            if (title.isNotEmpty() || body.isNotEmpty()) {
                out += McpRemoteNote(title = title, body = body)
            }
        }
        return out
    }

    private fun fingerprint(title: String, body: String): String {
        val t = title.trim().lowercase()
        val b = body.trim().take(120).lowercase()
        return "$t||$b"
    }
}
