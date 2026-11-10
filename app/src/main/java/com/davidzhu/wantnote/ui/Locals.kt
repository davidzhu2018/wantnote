package com.davidzhu.wantnote.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.sync.CloudSyncService

val LocalNotes = staticCompositionLocalOf<NoteRepository> {
    error("NoteRepository not provided")
}

val LocalSettings = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository not provided")
}

val LocalMedia = staticCompositionLocalOf<MediaStorage> {
    error("MediaStorage not provided")
}

val LocalAi = staticCompositionLocalOf<AiService> {
    error("AiService not provided")
}

val LocalSync = staticCompositionLocalOf<CloudSyncService> {
    error("CloudSyncService not provided")
}
