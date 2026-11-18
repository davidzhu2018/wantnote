package com.davidzhu.wantnote.sync.dinox

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidzhu.wantnote.WantNoteApplication
import java.util.concurrent.TimeUnit

/**
 * Dinox 自动同步：按用户设置的间隔做一次增量拉取。
 * 间隔在数据管理 → 生态互联里配置（关闭 / 15分钟 / 1小时 / 6小时 / 每天），
 * 改动即重建唯一周期任务（UPDATE 策略，保留上次排队状态）。
 */
class DinoxAutoSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WantNoteApplication
            ?: return Result.failure()
        val result = app.dinoxSync.syncIncremental()
        return when {
            result.isSuccess -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "dinox-auto-sync"

        /** minutes <= 0 取消任务；WorkManager 最小周期为 15 分钟。 */
        fun schedule(context: Context, minutes: Int) {
            val manager = WorkManager.getInstance(context)
            if (minutes <= 0) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val interval = minutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<DinoxAutoSyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
