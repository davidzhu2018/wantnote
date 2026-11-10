package com.davidzhu.wantnote.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SettingsRepository

val LocalNotes = staticCompositionLocalOf<NoteRepository> {
    error("NoteRepository not provided")
}

val LocalSettings = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository not provided")
}

val LocalMedia = staticCompositionLocalOf<MediaStorage> {
    error("MediaStorage not provided")
}
