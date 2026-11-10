package com.davidzhu.wantnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.WantNoteApp
import com.davidzhu.wantnote.ui.theme.WantNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WantNoteApplication
        setContent {
            WantNoteTheme {
                CompositionLocalProvider(LocalNotes provides app.repository) {
                    WantNoteApp()
                }
            }
        }
    }
}
