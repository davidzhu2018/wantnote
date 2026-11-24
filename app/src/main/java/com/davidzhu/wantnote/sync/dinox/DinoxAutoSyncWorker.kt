package com.davidzhu.wantnote.sync.dinox

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.davidzhu.wantnote.WantNoteApplication
import com.davidzhu.wantnote.data.DinoxRuleSyncMode
import com.davidzhu.wantnote.data.DinoxSyncRule
import java.util.concurrent.TimeUnit

/**
 * 遗留 0.9.0/0.10.0 旧「从 Dinox 同步到本机」的全局定时 Worker 占位。
 *
 * 0.11.0 已移除该入口并明确禁止在旧入口下静默定时拉取：若系统里仍残留
 * 升级前排队的旧任务，此处直接返回 success 且启动时立即取消。
 */
class DinoxAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val WORK_NAME = "dinox-auto-sync"

        fun cancelLegacy(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/**
 * 0.11.0 卡片盒同步规则的定时自动同步 Worker（每条自动规则一个唯一周期任务）。
 *
 * - 规则存在 Room（`dinox_sync_rules`），应用启动时会按库中规则重建调度；
 * - 每次触发按规则执行一次增量同步（首次游标为 0 时等同全量拉取）；
 * - 规则已删除或切为手动模式时自动取消并退出。
 */
class DinoxBoxSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WantNoteApplication
            ?: return Result.failure()
        val ruleId = inputData.getString(KEY_RULE_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        val rule = app.dinoxSync.getSyncRule(ruleId)
        if (rule == null || rule.modeOption != DinoxRuleSyncMode.AUTO) {
            cancelRule(applicationContext, ruleId)
            return Result.success()
        }
        if (!app.dinoxSync.hasToken()) {
            return Result.success()
        }
        val result = app.dinoxSync.runSyncRule(ruleId, full = false)
        return when {
            result.isSuccess -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val KEY_RULE_ID = "rule_id"
        private const val WORK_PREFIX = "dinox-box-sync-rule-"

        fun workNameFor(ruleId: String): String = "$WORK_PREFIX$ruleId"

        fun scheduleRule(context: Context, rule: DinoxSyncRule) {
            val manager = WorkManager.getInstance(context)
            val name = workNameFor(rule.id)
            if (rule.modeOption != DinoxRuleSyncMode.AUTO ||
                rule.autoIntervalMinutes < DinoxSyncRuleTargetResolver.MIN_AUTO_INTERVAL_MINUTES
            ) {
                manager.cancelUniqueWork(name)
                return
            }
            val interval = rule.autoIntervalMinutes
                .coerceAtLeast(DinoxSyncRuleTargetResolver.MIN_AUTO_INTERVAL_MINUTES)
                .toLong()
            val request = PeriodicWorkRequestBuilder<DinoxBoxSyncWorker>(interval, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_RULE_ID to rule.id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancelRule(context: Context, ruleId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workNameFor(ruleId))
        }

        fun syncAllSchedules(context: Context, rules: List<DinoxSyncRule>) {
            rules.forEach { rule -> scheduleRule(context, rule) }
        }
    }
}
