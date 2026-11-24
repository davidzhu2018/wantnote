package com.davidzhu.wantnote.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

enum class DinoxRuleType(val label: String) {
    ONE_TO_ONE("一对一"),
    MANY_TO_ONE("多对一"),
    ;

    companion object {
        fun fromStorage(value: String?): DinoxRuleType {
            return entries.firstOrNull { it.name == value } ?: ONE_TO_ONE
        }
    }
}

enum class DinoxRuleSyncMode(val label: String) {
    MANUAL("手动"),
    AUTO("自动"),
    ;

    companion object {
        fun fromStorage(value: String?): DinoxRuleSyncMode {
            return entries.firstOrNull { it.name == value } ?: MANUAL
        }
    }
}

/**
 * 0.11.0 生态互联「卡片盒同步」规则（持久化在 Room，进程被杀后仍在）。
 *
 * - 一对一：1 个 Dinox 卡片盒 → 1 个本机卡片盒；可选「沿用 Dinox 卡片盒名称」
 *   （同步时若本机无同名盒则自动新建，已有同名盒则直接写入）。
 * - 多对一：多个 Dinox 卡片盒 → 本机某一个已有卡片盒（不提供「沿用名称」）。
 * - 同步方式：手动（支持全量/增量）或自动（按用户设定间隔由 WorkManager 周期执行）。
 */
@Entity(tableName = "dinox_sync_rules")
data class DinoxSyncRule(
    @PrimaryKey val id: String,
    val ruleType: String = DinoxRuleType.ONE_TO_ONE.name,
    /** JSON 字符串数组，记录来源 Dinox 卡片盒名称。 */
    val sourceBoxesJson: String = "[]",
    /** 本机目标 NoteBox 的 ID；仅当一对一且 followDinoxName == true 时为空串。 */
    val targetBoxId: String = "",
    /** 仅一对一可用：同步时检查本机是否有同名盒，没有就新建，再同步进去。 */
    val followDinoxName: Boolean = false,
    val syncMode: String = DinoxRuleSyncMode.MANUAL.name,
    /** 自动同步间隔（分钟；WorkManager 最小 15 分钟）。手动模式时存 0。 */
    val autoIntervalMinutes: Int = 0,
    /** 本规则独立的 Dinox 增量游标（来自 Dinox 最新 updateTime，epoch ms）。 */
    val lastSyncAt: Long = 0L,
    /** 上次执行同步的本地时间（epoch ms）。 */
    val lastRunAt: Long = 0L,
    /** 上次执行的简短非敏感状态说明。 */
    val lastStatus: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val typeOption: DinoxRuleType get() = DinoxRuleType.fromStorage(ruleType)
    val modeOption: DinoxRuleSyncMode get() = DinoxRuleSyncMode.fromStorage(syncMode)
    val sourceBoxes: List<String> get() = DinoxSyncRuleCodec.decodeSourceBoxes(sourceBoxesJson)
}

object DinoxSyncRuleCodec {
    fun encodeSourceBoxes(names: List<String>): String {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val arr = JSONArray()
        cleaned.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decodeSourceBoxes(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val result = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val value = arr.optString(i).trim()
            if (value.isNotEmpty() && value !in result) {
                result += value
            }
        }
        return result
    }

    fun formatInterval(minutes: Int): String {
        if (minutes <= 0) return "未设置"
        return when {
            minutes % 1440 == 0 -> "${minutes / 1440} 天"
            minutes % 60 == 0 -> "${minutes / 60} 小时"
            else -> "$minutes 分钟"
        }
    }
}

@Dao
interface DinoxSyncRuleDao {
    @Query("SELECT * FROM dinox_sync_rules ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DinoxSyncRule>>

    @Query("SELECT * FROM dinox_sync_rules ORDER BY createdAt ASC")
    suspend fun getAll(): List<DinoxSyncRule>

    @Query("SELECT * FROM dinox_sync_rules WHERE id = :id")
    suspend fun getById(id: String): DinoxSyncRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: DinoxSyncRule)

    @Query("DELETE FROM dinox_sync_rules WHERE id = :id")
    suspend fun delete(id: String)
}
