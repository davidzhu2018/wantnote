package com.davidzhu.wantnote.ai

import com.davidzhu.wantnote.data.AiModelInfo
import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AiProviderDao
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.data.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TitleTagSuggestion(
    val title: String,
    val tags: List<String>,
)

class AiException(message: String) : Exception(message)

class AiService(
    private val providerDao: AiProviderDao,
    private val settingsRepository: SettingsRepository,
    private val notes: NoteRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeProviders(): Flow<List<AiProvider>> = providerDao.observeAll()

    suspend fun getProviders(): List<AiProvider> = providerDao.getAll()

    suspend fun getProvider(id: String): AiProvider? = providerDao.getById(id)

    suspend fun upsertProvider(provider: AiProvider) {
        providerDao.upsert(provider.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProvider(id: String) {
        providerDao.delete(id)
        settingsRepository.update { settings ->
            settings.copy(
                aiSummaryProviderId = settings.aiSummaryProviderId.takeIf { it != id }.orEmpty(),
                aiTitleTagProviderId = settings.aiTitleTagProviderId.takeIf { it != id }.orEmpty(),
            )
        }
    }

    suspend fun fetchModels(providerId: String): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        val provider = providerDao.getById(providerId)
            ?: return@withContext Result.failure(AiException("找不到该服务提供商"))
        val key = provider.apiKey.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(AiException("请先填写 API Key"))
        }
        if (provider.baseUrl.isBlank()) {
            return@withContext Result.failure(AiException("请先填写接口地址"))
        }
        runCatching {
            val url = AiHttp.joinUrl(provider.baseUrl, "models")
            val response = AiHttp.get(url, key)
            if (!response.ok) {
                throw AiException("获取模型失败（HTTP ${response.code}）${suffix(response.body, key)}")
            }
            val models = parseModelList(response.body)
            if (models.isEmpty()) {
                throw AiException("没有返回可用模型，请检查接口地址和 Key")
            }
            val previousEnabled = provider.enabledModelIds().toSet()
            val enabled = models.map { it.id }.filter { it in previousEnabled }.ifEmpty {
                models.take(1).map { it.id }
            }
            upsertProvider(
                provider.copy(
                    availableModelsJson = AiProvider.modelsToJson(models),
                    enabledModelsJson = AiProvider.stringListToJson(enabled),
                ),
            )
            maybeSetDefaultModels(provider.id, enabled.first())
            models
        }.recoverCatching { error ->
            throw AiException(AiHttp.userSafeMessage(error, key))
        }
    }

    suspend fun setEnabledModels(providerId: String, modelIds: List<String>) {
        val provider = providerDao.getById(providerId) ?: return
        upsertProvider(provider.copy(enabledModelsJson = AiProvider.stringListToJson(modelIds)))
        val first = modelIds.firstOrNull()
        if (first != null) {
            maybeSetDefaultModels(providerId, first)
        }
    }

    suspend fun complete(providerId: String, modelId: String, system: String, user: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val provider = providerDao.getById(providerId)
                ?: return@withContext Result.failure(AiException("还没有配置可用的模型"))
            val key = provider.apiKey.trim()
            if (key.isEmpty()) {
                return@withContext Result.failure(AiException("还没有配置 API Key"))
            }
            if (modelId.isBlank()) {
                return@withContext Result.failure(AiException("请先选择要使用的模型"))
            }
            runCatching {
                val payload = JSONObject()
                    .put("model", modelId)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user)),
                    )
                    .put("temperature", 0.3)
                val url = AiHttp.joinUrl(provider.baseUrl, "chat/completions")
                val response = AiHttp.postJson(url, key, payload.toString())
                if (!response.ok) {
                    throw AiException("模型请求失败（HTTP ${response.code}）${suffix(response.body, key)}")
                }
                val text = parseChatContent(response.body)
                if (text.isBlank()) throw AiException("模型没有返回内容")
                text
            }.recoverCatching { error ->
                throw AiException(AiHttp.userSafeMessage(error, key))
            }
        }
    }

    suspend fun generateSummary(corpus: String): Result<String> {
        val settings = settingsRepository.get()
        val providerId = settings.aiSummaryProviderId
        val modelId = settings.aiSummaryModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return Result.failure(AiException("请先在设置里配置 AI，并选择用于提炼的模型"))
        }
        val clipped = corpus.take(12_000)
        val system = "你是笔记助手。根据用户提供的笔记内容写一段中文提炼总结（约 200 到 400 字），概括主题、进展、待办和值得注意的点。不要编造笔记中没有的事实。只输出总结正文，不要加标题或前言。"
        val user = if (clipped.isBlank()) "（目前没有笔记正文）" else clipped
        return complete(providerId, modelId, system, user)
    }

    suspend fun generateTitleAndTags(title: String, body: String): Result<TitleTagSuggestion> {
        val settings = settingsRepository.get()
        val providerId = settings.aiTitleTagProviderId
        val modelId = settings.aiTitleTagModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return Result.failure(AiException("请先在设置里配置 AI，并选择用于生成标题和标签的模型"))
        }
        val clipped = buildString {
            if (title.isNotBlank()) {
                append("现有标题：")
                append(title.take(80))
                append('\n')
            }
            append(body.take(8_000))
        }
        val system = """
            你是笔记助手。根据用户的笔记正文，生成一个简短中文标题（不超过 20 个字），以及 1 到 5 个标签。
            标签支持多级，层级用斜杠表示，例如：工作/项目。
            只输出 JSON 对象：{"title":"...","tags":["..."]}
            不要使用 Markdown 代码块，不要输出其他说明。
        """.trimIndent()
        return complete(providerId, modelId, system, clipped.ifBlank { "（空笔记）" }).mapCatching { raw ->
            parseTitleTags(raw)
        }
    }

    /**
     * After a note is saved, try to fill a blank title and missing tags.
     * Never blocks or rewrites the user's existing title/tags. Failures are skipped.
     */
    fun enqueueAutoTitleTags(noteId: String, force: Boolean = false) {
        scope.launch {
            runAutoTitleTags(noteId, force)
        }
    }

    suspend fun runAutoTitleTags(noteId: String, force: Boolean = false): Result<TitleTagSuggestion> {
        val note = notes.getById(noteId) ?: return Result.failure(AiException("笔记不存在"))
        val existingTags = notes.getTagsForNote(noteId)
        val body = SearchText.fromBlocks(notes.getBlocks(noteId))
        if (body.isBlank() && note.title.isBlank()) {
            return Result.failure(AiException("空笔记不会生成标题和标签"))
        }
        val shouldTitle = force || note.title.isBlank()
        val shouldTags = force || existingTags.isEmpty()
        if (!shouldTitle && !shouldTags) {
            return Result.failure(AiException("标题和标签已有内容，未自动覆盖"))
        }
        val result = generateTitleAndTags(note.title, body)
        val suggestion = result.getOrElse { return result }
        val newTitle = if (shouldTitle && suggestion.title.isNotBlank()) suggestion.title.take(40) else note.title
        if (newTitle != note.title) {
            notes.updateTitle(noteId, newTitle)
        }
        if (shouldTags && suggestion.tags.isNotEmpty()) {
            val merged = (existingTags.map { it.path } + suggestion.tags)
                .map { Tag.normalizePath(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            notes.setNoteTagPaths(noteId, merged)
        }
        return Result.success(suggestion)
    }

    private suspend fun maybeSetDefaultModels(providerId: String, modelId: String) {
        settingsRepository.update { settings ->
            var next = settings
            if (next.aiSummaryProviderId.isBlank() || next.aiSummaryModelId.isBlank()) {
                next = next.copy(aiSummaryProviderId = providerId, aiSummaryModelId = modelId)
            }
            if (next.aiTitleTagProviderId.isBlank() || next.aiTitleTagModelId.isBlank()) {
                next = next.copy(aiTitleTagProviderId = providerId, aiTitleTagModelId = modelId)
            }
            next
        }
    }

    private fun suffix(body: String, key: String): String {
        val short = AiHttp.shortErrorBody(body, key)
        return if (short.isBlank()) "" else "：$short"
    }

    companion object {
        fun parseModelList(body: String): List<AiModelInfo> {
            val root = JSONObject(body)
            val arr = when {
                root.has("data") && root.opt("data") is JSONArray -> root.getJSONArray("data")
                root.has("models") && root.opt("models") is JSONArray -> root.getJSONArray("models")
                else -> JSONArray()
            }
            val seen = linkedSetOf<String>()
            val out = mutableListOf<AiModelInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { obj.optString("name") }.trim()
                if (id.isEmpty() || !seen.add(id)) continue
                val name = obj.optString("id").ifBlank { id }.let {
                    obj.optString("owned_by").takeIf { owner -> owner.isNotBlank() }?.let { owner -> "$id" } ?: id
                }
                out.add(AiModelInfo(id, name.ifBlank { id }))
            }
            return out
        }

        fun parseChatContent(body: String): String {
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices") ?: return ""
            val first = choices.optJSONObject(0) ?: return ""
            val message = first.optJSONObject("message")
            val content = message?.optString("content").orEmpty()
            if (content.isNotBlank()) return content.trim()
            return first.optString("text").trim()
        }

        fun parseTitleTags(raw: String): TitleTagSuggestion {
            val jsonText = extractJsonObject(raw)
            val obj = JSONObject(jsonText)
            val title = obj.optString("title").trim()
            val tags = mutableListOf<String>()
            val arr = obj.optJSONArray("tags")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val path = Tag.normalizePath(arr.optString(i))
                    if (path.isNotEmpty()) tags += path
                }
            }
            return TitleTagSuggestion(title = title, tags = tags.distinct().take(5))
        }

        private fun extractJsonObject(raw: String): String {
            val trimmed = raw.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            if (trimmed.startsWith("{")) return trimmed
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
            return trimmed
        }

        fun newProviderId(): String = UUID.randomUUID().toString()
    }
}
