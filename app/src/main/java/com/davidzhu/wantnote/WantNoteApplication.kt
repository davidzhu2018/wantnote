package com.davidzhu.wantnote

import android.app.Application
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.NoteDatabase
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.sync.CloudSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WantNoteApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { NoteDatabase.create(this) }
    val repository by lazy { NoteRepository(database) }
    val settingsRepository by lazy { SettingsRepository(database.settingsDao()) }
    val mediaStorage by lazy { MediaStorage(this) }
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
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            settingsRepository.ensureDefault()
        }
    }
}
