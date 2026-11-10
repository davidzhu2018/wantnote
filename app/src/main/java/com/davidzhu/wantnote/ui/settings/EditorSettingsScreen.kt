package com.davidzhu.wantnote.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.EditorFontOption
import com.davidzhu.wantnote.data.FontScaleOption
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorSettingsScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val media = LocalMedia.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { media.importFont(uri) }
            }.onSuccess { path ->
                settingsRepo.update {
                    it.copy(editorFontId = EditorFontOption.CUSTOM.id, customFontPath = path)
                }
                snackbar.showSnackbar("已添加自定义字体")
            }.onFailure {
                snackbar.showSnackbar("无法导入字体，请选择 TTF 或 OTF 文件")
            }
        }
    }

    val customName = settings.customFontPath.substringAfterLast('/').ifBlank { "未选择" }

    SettingsSubScaffold(title = "编辑器设置", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle("界面字体")
            Text(
                "内置字体均为 SIL Open Font License。Noto 系列已裁剪到 GB2312 常用汉字；生僻字可改用系统默认或自行添加完整字体。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                EditorFontOption.entries.filter { it != EditorFontOption.CUSTOM }.forEach { option ->
                    FilterChip(
                        selected = settings.editorFont == option,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(editorFontId = option.id) } }
                        },
                        label = { Text(option.label) },
                    )
                }
                FilterChip(
                    selected = settings.editorFont == EditorFontOption.CUSTOM,
                    onClick = {
                        if (settings.customFontPath.isNotBlank()) {
                            scope.launch { settingsRepo.update { it.copy(editorFontId = EditorFontOption.CUSTOM.id) } }
                        } else {
                            picker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/font-sfnt", "*/*"))
                        }
                    },
                    label = { Text("自定义字体") },
                )
            }
            if (settings.editorFont == EditorFontOption.CUSTOM) {
                Text(
                    "当前文件：$customName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    picker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/font-sfnt", "*/*"))
                },
            ) {
                Text("添加自定义字体（TTF / OTF）")
            }
            if (settings.customFontPath.isNotBlank()) {
                val file = File(context.filesDir, settings.customFontPath)
                Text(
                    if (file.exists()) "已保存到应用私有目录，卸载应用时会一并移除。" else "自定义字体文件不存在，请重新添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("字体大小")
            ChipRow {
                FontScaleOption.entries.forEach { option ->
                    FilterChip(
                        selected = settings.fontScaleOption == option,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(fontScale = option.name) } }
                        },
                        label = { Text(option.label) },
                    )
                }
            }
            Text(
                "字号会作用于编辑器和阅读界面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
