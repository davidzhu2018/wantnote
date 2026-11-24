package com.davidzhu.wantnote.sync.dinox

import com.davidzhu.wantnote.data.DinoxRuleSyncMode
import com.davidzhu.wantnote.data.DinoxRuleType
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.DinoxSyncRuleCodec
import com.davidzhu.wantnote.data.NoteBox

/**
 * 本地卡片盒查询与创建抽象，供 [DinoxSyncRuleTargetResolver] 在 JVM 单元测试
 * 与 Android 运行时共用。
 */
interface LocalBoxLookup {
    suspend fun findById(id: String): NoteBox?
    suspend fun findByName(name: String): NoteBox?
    suspend fun create(name: String): NoteBox?
}

/**
 * 0.11.0 卡片盒同步规则的校验、规范化与目标盒解析（纯 JVM，可单测）。
 *
 * 规则约束：
 * - 多对一：只允许同步到本机某一个已存在的卡片盒，不支持也不应用「沿用名称」。
 * - 一对一：
 *   - 选「沿用 Dinox 卡片盒名称」时：同步时检查本机有没有同名盒，有则写入该盒，
 *     没有就用该 Dinox 卡片盒名新建一个再写入（不另造名字）。
 *   - 选指定本机卡片盒时：写入该已有盒；若盒子已被删除则报错，不发明新盒名。
 */
object DinoxSyncRuleTargetResolver {
    /** Android WorkManager 周期任务允许的最小间隔（分钟）。 */
    const val MIN_AUTO_INTERVAL_MINUTES = 15

    fun normalize(rule: DinoxSyncRule): DinoxSyncRule {
        val type = rule.typeOption
        val mode = rule.modeOption
        val cleanedSources = rule.sourceBoxes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val finalSources = if (type == DinoxRuleType.ONE_TO_ONE) {
            cleanedSources.take(1)
        } else {
            cleanedSources
        }
        val follow = type == DinoxRuleType.ONE_TO_ONE && rule.followDinoxName
        val targetId = if (follow) "" else rule.targetBoxId.trim()
        val interval = if (mode == DinoxRuleSyncMode.AUTO) {
            rule.autoIntervalMinutes.coerceAtLeast(MIN_AUTO_INTERVAL_MINUTES)
        } else {
            0
        }
        return rule.copy(
            ruleType = type.name,
            sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(finalSources),
            targetBoxId = targetId,
            followDinoxName = follow,
            syncMode = mode.name,
            autoIntervalMinutes = interval,
        )
    }

    /** 返回面向用户的简短校验错误；null 表示规则合法。 */
    fun validate(rule: DinoxSyncRule): String? {
        val sources = rule.sourceBoxes
        val type = rule.typeOption
        when (type) {
            DinoxRuleType.ONE_TO_ONE -> {
                if (sources.size != 1) return "请选择一个 Dinox 来源卡片盒"
                if (!rule.followDinoxName && rule.targetBoxId.isBlank()) {
                    return "请选择本机目标卡片盒，或勾选「沿用 Dinox 卡片盒名称」"
                }
            }
            DinoxRuleType.MANY_TO_ONE -> {
                if (sources.isEmpty()) return "请至少选择一个 Dinox 来源卡片盒"
                if (rule.targetBoxId.isBlank()) return "请选择一个本机已有卡片盒作为目标"
            }
        }
        if (rule.modeOption == DinoxRuleSyncMode.AUTO && rule.autoIntervalMinutes < MIN_AUTO_INTERVAL_MINUTES) {
            return "自动同步间隔不能小于 $MIN_AUTO_INTERVAL_MINUTES 分钟"
        }
        return null
    }

    /**
     * 在执行同步规则时解析本机目标卡片盒。
     * 仅当「一对一 + 沿用 Dinox 卡片盒名称」时，若本机没有同名盒才会新建同名盒；
     * 其他任何情况都绝不新建盒子。
     */
    suspend fun resolveTargetBox(
        rule: DinoxSyncRule,
        boxes: LocalBoxLookup,
    ): Result<NoteBox> {
        val type = rule.typeOption
        val sources = rule.sourceBoxes
        if (sources.isEmpty()) {
            return Result.failure(DinoxApiException("同步规则未配置 Dinox 来源卡片盒"))
        }
        if (type == DinoxRuleType.ONE_TO_ONE && rule.followDinoxName) {
            val sourceName = sources.first().trim()
            if (sourceName.isEmpty()) {
                return Result.failure(DinoxApiException("同步规则的 Dinox 卡片盒名称为空"))
            }
            val existing = boxes.findByName(sourceName)
            if (existing != null) {
                return Result.success(existing)
            }
            val created = boxes.create(sourceName)
                ?: boxes.findByName(sourceName)
                ?: return Result.failure(DinoxApiException("无法创建本机卡片盒「$sourceName」"))
            return Result.success(created)
        }
        val targetId = rule.targetBoxId.trim()
        if (targetId.isEmpty()) {
            return Result.failure(DinoxApiException("同步规则未选择本机目标卡片盒"))
        }
        val target = boxes.findById(targetId)
            ?: return Result.failure(DinoxApiException("目标本机卡片盒不存在，请编辑规则重新选择"))
        return Result.success(target)
    }
}
