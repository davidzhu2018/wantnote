package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.davidzhu.wantnote.ui.components.WantFilterChip
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextButton
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.AppStorage
import com.davidzhu.wantnote.data.DayNightMode
import com.davidzhu.wantnote.data.ThemePalette
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var storageTick by remember { mutableStateOf(0) }
    val hasStorageAccess = remember(storageTick) { AppStorage.hasStorageAccess(context) }

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
                    WantFilterChip(
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
                    WantFilterChip(
                        selected = settings.dayNightMode == mode,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(dayNight = mode.name) } }
                        },
                        label = { Text(mode.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("数据存储")
            Text(
                text = "当前存储位置：" + AppStorage.baseDir(context).absolutePath,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (hasStorageAccess) {
                    "已授权。笔记数据库和附件保存在「Documents/WantNote」，卸载后也能从文件管理器找回。"
                } else {
                    "尚未授权。数据暂时保存在应用私有目录（卸载会被清空）。授予“所有文件访问”权限后，" +
                        "重启想记即可把数据迁移到 Documents/WantNote，卸载后也能找回。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!hasStorageAccess) {
                WantTextButton(
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        } else {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        storageTick += 1
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("去授权存储")
                }
            }
        }
    }
}
