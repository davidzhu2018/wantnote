package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.DayNightMode
import com.davidzhu.wantnote.data.ThemePalette
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()

    SettingsSubScaffold(title = "通用设置", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle("主题")
            ChipRow {
                ThemePalette.entries.forEach { palette ->
                    FilterChip(
                        selected = settings.palette == palette,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(themeName = palette.name) } }
                        },
                        label = { Text(palette.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("日夜")
            ChipRow {
                DayNightMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.dayNightMode == mode,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(dayNight = mode.name) } }
                        },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
    }
}
