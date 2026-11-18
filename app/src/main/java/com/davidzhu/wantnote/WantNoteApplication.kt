package com.davidzhu.wantnote

import android.app.Application
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.data.AppStorage
import com.davidzhu.wantnote.data.MarkdownStore
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.NoteDatabase
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.sync.CloudSyncService
import com.davidzhu.wantnote.sync.dinox.DinoxAutoSyncWorker
import com.davidzhu.wantnote.sync.dinox.DinoxSyncService
import com.davidzhu.wantnote.sync.dinox.DinoxTokenVault
import com.davidzhu.wantnote.transfer.BackupService
import com.davidzhu.wantnote.transfer.ImportExportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WantNoteApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { NoteDatabase.create(this) }
    val markdownStore by lazy { MarkdownStore(this) }
    val repository by lazy { NoteRepository(database, markdownStore) }
    val settingsRepository by lazy { SettingsRepository(database.settingsDao()) }
    val mediaStorage by lazy { MediaStorage(this) }
    val dinoxTokenVault by lazy { DinoxTokenVault(this) }
    val dinoxSync by lazy {
        DinoxSyncService(
            notes = repository,
            settingsRepository = settingsRepository,
            vault = dinoxTokenVault,
        )
    }
    val aiService by lazy {
        AiService(
            providerDao = database.aiProviderDao(),
            settingsRepository = settingsRepository,
            notes = repository,
        )
    }
    val cloudSync by lazy {
        CloudSyncService(
            notes = repository,
            settingsRepository = settingsRepository,
            media = mediaStorage,
            dinox = dinoxSync,
        )
    }
    val backupService by lazy {
        BackupService(
            context = this,
            db = database,
            notes = repository,
            media = mediaStorage,
            appVersion = BuildConfig.VERSION_NAME,
            dinox = dinoxSync,
        )
    }
    val importExportService by lazy {
        ImportExportService(
            context = this,
            notes = repository,
            media = mediaStorage,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 必须在任何 Room 访问之前同步完成旧库迁移（通常只是几个 MB 的拷贝）。
        runCatching { AppStorage.migrateDatabase(this) }
        applicationScope.launch {
            settingsRepository.ensureDefault()
        }
        applicationScope.launch {
            // 0.10.0：确保默认卡片盒「暂存箱」存在（幂等）。
            runCatching { repository.ensureDefaultBox() }
        }
        applicationScope.launch {
            // 已授权时，把私有目录里旧的附件/字体文件补拷到 Documents/WantNote。
            AppStorage.prepare(this@WantNoteApplication)
        }
        applicationScope.launch {
            // 恢复设置 / 换机后重建 Dinox 自动同步任务（间隔在设置里）。
            runCatching {
                settingsRepository.ensureDefault()
                val settings = settingsRepository.get()
                DinoxAutoSyncWorker.schedule(this@WantNoteApplication, settings.dinoxAutoSyncMinutes)
            }
        }
    }
}
