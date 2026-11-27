package com.davidzhu.wantnote.ai

import com.davidzhu.wantnote.data.AiModelInfo
import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AiProviderDao
import com.davidzhu.wantnote.data.NoteRepository
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
        if (!settings.aiEnableSummary) {
            return Result.failure(AiException("AI提炼能力已关闭"))
        }
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

    suspend fun generateInsight(corpus: String): Result<String> {
        val settings = settingsRepository.get()
        if (!settings.aiEnableInsight) {
            return Result.failure(AiException("AI洞察能力已关闭"))
        }
        val providerId = settings.aiSummaryProviderId
        val modelId = settings.aiSummaryModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return Result.failure(AiException("请先在设置里配置 AI，并选择用于提炼与洞察的模型"))
        }
        val clipped = corpus.take(12_000)
        val system = "你是笔记洞察助手。根据用户的多篇笔记，发掘跨笔记的隐含关联、高频关注点、潜在风险与下一步行动建议（约 250 到 450 字中文）。不要编造笔记中没有的事实。只输出洞察正文，不要加寒暄。"
        val user = if (clipped.isBlank()) "（目前没有笔记正文）" else clipped
        return complete(providerId, modelId, system, user)
    }

    suspend fun generateTitleAndTags(title: String, body: String): Result<TitleTagSuggestion> {
        val settings = settingsRepository.get()
        if (!settings.aiEnableBox) {
            return Result.failure(AiException("AI分配卡片盒能力已关闭"))
        }
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
    fun enqueueAutoTitleTags(noteId: String) {
        scope.launch {
            runAutoTitleTags(noteId)
        }
    }

    /**
     * Fill-only pass: a generated title is written ONLY if the stored title is
     * still blank, and generated tags are attached ONLY if the note still has
     * none. Never replaces user-written content.
     */
    suspend fun runAutoTitleTags(noteId: String): Result<TitleTagSuggestion> {
        val note = notes.getById(noteId) ?: return Result.failure(AiException("笔记不存在"))
        val existingTags = notes.getTagsForNote(noteId)
        val body = notes.loadContent(note)
        if (body.isBlank() && note.title.isBlank()) {
            return Result.failure(AiException("空笔记不会生成标题和标签"))
        }
        val canTitle = note.title.isBlank()
        val canTags = existingTags.isEmpty()
        if (!canTitle && !canTags) {
            return Result.failure(AiException("标题和标签已有内容，未自动生成"))
        }
        val result = generateTitleAndTags(note.title, body)
        val suggestion = result.getOrElse { return result }
        val newTitle = if (canTitle && suggestion.title.isNotBlank()) suggestion.title.take(40) else note.title
        if (newTitle != note.title) {
            notes.updateTitle(noteId, newTitle)
        }
        if (canTags && suggestion.tags.isNotEmpty()) {
            val tags = suggestion.tags
                .map { Tag.normalizePath(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            notes.setNoteTagPaths(noteId, tags)
        }
        return Result.success(suggestion)
    }

    /**
     * The ONLY title/tag entrypoint allowed to replace existing content, and
     * it is reachable only from the editor's confirmation dialog: the user is
     * shown what will be replaced and explicitly taps 「覆盖标题和标签」.
     * Title is replaced by the AI title; tags are replaced by the AI tags.
     */
    suspend fun runOverwriteTitleTags(noteId: String): Result<TitleTagSuggestion> {
        val note = notes.getById(noteId) ?: return Result.failure(AiException("笔记不存在"))
        val body = notes.loadContent(note)
        if (body.isBlank() && note.title.isBlank()) {
            return Result.failure(AiException("空笔记不会生成标题和标签"))
        }
        val result = generateTitleAndTags(note.title, body)
        result.onSuccess { suggestion ->
            val newTitle = suggestion.title.take(40)
            if (newTitle.isNotBlank()) {
                notes.updateTitle(noteId, newTitle)
            }
            val tags = suggestion.tags
                .map { Tag.normalizePath(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            if (tags.isNotEmpty()) {
                notes.setNoteTagPaths(noteId, tags)
            }
        }
        return result
    }

    /**
     * Non-force counterpart used by the 保存 button: generate a suggestion but
     * apply nothing here. The caller (NoteRepository.fillEmptyTitleAndTags)
     * applies the result to whatever is still empty at apply time, so any
     * user-written title/tags made while the request was in flight survive.
     */
    suspend fun suggestFillTitleTags(noteId: String): Result<TitleTagSuggestion> {
        val note = notes.getById(noteId) ?: return Result.failure(AiException("笔记不存在"))
        val body = notes.loadContent(note)
        if (body.isBlank() && note.title.isBlank()) {
            return Result.failure(AiException("空笔记不会生成标题和标签"))
        }
        val canTitle = note.title.isBlank()
        val canTags = notes.getTagsForNote(noteId).isEmpty()
        if (!canTitle && !canTags) {
            return Result.failure(AiException("标题和标签已有内容，未自动生成"))
        }
        return generateTitleAndTags(note.title, body)
    }

    /**
     * 0.10.0 卡片盒自动分配：让模型从「已存在的卡片盒」里挑一个最合适的。
     * 只能返回候选名单里的名字（服务端校验），绝不在自动分配路径里新建
     * 卡片盒；模型认为没有合适的盒子时返回失败，由调用方提示用户手动选择。
     */
    suspend fun suggestExistingBox(noteId: String): Result<String> {
        val note = notes.getById(noteId) ?: return Result.failure(AiException("笔记不存在"))
        val body = notes.loadContent(note)
        if (body.isBlank() && note.title.isBlank()) {
            return Result.failure(AiException("空笔记不自动分配卡片盒"))
        }
        val boxNames = notes.getAllBoxes().map { it.name }
        if (boxNames.isEmpty()) {
            return Result.failure(AiException("还没有任何卡片盒，请先在设置里创建"))
        }
        val settings = settingsRepository.get()
        val providerId = settings.aiTitleTagProviderId
        val modelId = settings.aiTitleTagModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return Result.failure(AiException("请先在设置里配置 AI，并选择用于生成标题和标签的模型"))
        }
        val system = """
            你是笔记助手。给你一篇笔记和一组「已存在的卡片盒」名称。
            从列表里选出最适合归档这篇笔记的一个卡片盒名称，必须逐字使用列表里的名称。
            如果没有任何一个合适的，返回空字符串。
            只输出 JSON 对象：{"box":"..."}
            不要使用 Markdown 代码块，不要输出其他说明。
        """.trimIndent()
        val user = buildString {
            append("卡片盒列表：")
            append(boxNames.joinToString("、"))
            append('\n')
            if (note.title.isNotBlank()) {
                append("标题：")
                append(note.title.take(80))
                append('\n')
            }
            append(body.take(8_000))
        }
        return complete(providerId, modelId, system, user).mapCatching { raw ->
            parseBoxChoice(raw, boxNames) ?: throw AiException("AI 没有找到合适的卡片盒，可手动选择")
        }
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

        /**
         * 卡片盒自动分配的结果解析：只接受与候选名单逐字一致（trim 后）的
         * 名字；模型编造的名字、空字符串都返回 null。
         */
        fun parseBoxChoice(raw: String, validNames: List<String>): String? {
            val jsonText = extractJsonObject(raw)
            val box = try {
                JSONObject(jsonText).optString("box").trim()
            } catch (_: Exception) {
                return null
            }
            if (box.isEmpty()) return null
            return validNames.firstOrNull { it.trim() == box }
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
