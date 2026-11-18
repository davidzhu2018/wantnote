package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val route: String,
)

val SettingsEntries = listOf(
    SettingsEntry("通用设置", "主题、日夜切换", "settings/general"),
    SettingsEntry("主题定制", "自定义配色、自建主题与重置", "settings/themes"),
    SettingsEntry("编辑器设置", "界面字体、字号", "settings/editor"),
    SettingsEntry("卡片盒管理", "卡片盒增删查改", "settings/boxes"),
    SettingsEntry("标签管理", "增删查改、颜色和子标签", "settings/tags"),
    SettingsEntry("附件管理", "文件、图片、视频与压缩", "settings/attachments"),
    SettingsEntry("备份与同步", "本地备份/恢复与 WebDAV / S3 云同步", "settings/sync"),
    SettingsEntry("数据管理", "导入、导出、Dinox 等第三方同步", "settings/importexport"),
    SettingsEntry("AI设置", "模型与密钥（仅保存在本机）", "settings/ai"),
    SettingsEntry("关于", "版本与检查更新", "settings/about"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpen: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(SettingsEntries) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title) },
                    supportingContent = { Text(entry.subtitle) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry.route) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}
