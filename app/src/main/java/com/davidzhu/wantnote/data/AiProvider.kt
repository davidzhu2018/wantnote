package com.davidzhu.wantnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

data class AiModelInfo(
    val id: String,
    val name: String,
)

@Entity(tableName = "ai_providers")
data class AiProvider(
    @PrimaryKey val id: String,
    val presetId: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val availableModelsJson: String = "[]",
    val enabledModelsJson: String = "[]",
    val updatedAt: Long = 0L,
) {
    fun availableModels(): List<AiModelInfo> = parseModels(availableModelsJson)

    fun enabledModelIds(): List<String> {
        return parseStringList(enabledModelsJson)
    }

    fun enabledModels(): List<AiModelInfo> {
        val enabled = enabledModelIds().toSet()
        val available = availableModels()
        val byId = available.associateBy { it.id }
        return enabled.map { id -> byId[id] ?: AiModelInfo(id, id) }
    }

    companion object {
        fun parseModels(json: String): List<AiModelInfo> {
            return runCatching {
                val arr = JSONArray(json.ifBlank { "[]" })
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is String -> if (item.isNotBlank()) add(AiModelInfo(item, item))
                            else -> {
                                val obj = arr.optJSONObject(i) ?: continue
                                val id = obj.optString("id").trim()
                                if (id.isEmpty()) continue
                                val name = obj.optString("name").trim().ifEmpty { id }
                                add(AiModelInfo(id, name))
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun modelsToJson(models: List<AiModelInfo>): String {
            val arr = JSONArray()
            models.forEach { model ->
                arr.put(
                    org.json.JSONObject()
                        .put("id", model.id)
                        .put("name", model.name),
                )
            }
            return arr.toString()
        }

        fun parseStringList(json: String): List<String> {
            return runCatching {
                val arr = JSONArray(json.ifBlank { "[]" })
                buildList {
                    for (i in 0 until arr.length()) {
                        val value = arr.optString(i).trim()
                        if (value.isNotEmpty()) add(value)
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun stringListToJson(values: List<String>): String {
            val arr = JSONArray()
            values.distinct().forEach { arr.put(it) }
            return arr.toString()
        }
    }
}
