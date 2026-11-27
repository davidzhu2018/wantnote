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
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import com.davidzhu.wantnote.ui.components.WantListItem
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantScaffold
import top.yukonga.miuix.kmp.basic.Text
import com.davidzhu.wantnote.ui.components.WantTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val route: String,
)

val SettingsEntries = listOf(
    SettingsEntry("账号管理", "设置用户名与头像", "settings/account"),
    SettingsEntry("数据统计", "笔记与卡片盒统计、时段分布与创作热力图", "settings/stats"),
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

@Composable
fun SettingsScreen(onOpen: (String) -> Unit) {
    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WantTopBar(
                title = "设置",
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
                val titleStyle = MiuixTheme.textStyles.body1
                val descSizeSp = (titleStyle.fontSize.value - 2f).coerceAtLeast(11f).sp
                WantListItem(
                    headlineContent = {
                        Text(
                            text = entry.title,
                            style = titleStyle,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = entry.subtitle,
                            style = MiuixTheme.textStyles.footnote1.copy(fontSize = descSizeSp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(entry.route) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}
