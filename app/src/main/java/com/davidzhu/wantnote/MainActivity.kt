package com.davidzhu.wantnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.LocalSync
import com.davidzhu.wantnote.ui.WantNoteApp
import com.davidzhu.wantnote.ui.theme.WantNoteTheme
import com.davidzhu.wantnote.ui.theme.loadEditorFontFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WantNoteApplication
        setContent {
            val settings by app.settingsRepository.observe()
                .collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
            val context = LocalContext.current
            val fontFamily = remember(settings.editorFontId, settings.customFontPath) {
                loadEditorFontFamily(context, settings)
            }
            WantNoteTheme(settings = settings, fontFamily = fontFamily) {
                CompositionLocalProvider(
                    LocalNotes provides app.repository,
                    LocalSettings provides app.settingsRepository,
                    LocalMedia provides app.mediaStorage,
                    LocalAi provides app.aiService,
                    LocalSync provides app.cloudSync,
                ) {
                    WantNoteApp()
                }
            }
        }
    }
}
