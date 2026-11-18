package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.sync.dinox.DinoxAutoSyncWorker
import com.davidzhu.wantnote.ui.LocalDinox
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

/** Dinox 自动同步间隔选项（分钟；0 = 关闭）。 */
private val AutoSyncOptions = listOf(
    0 to "关闭",
    15 to "15分钟",
    60 to "1小时",
    360 to "6小时",
    1440 to "每天",
)

/**
 * 数据管理 → 生态互联。
 *
 * 目前唯一接入的第三方应用是 Dinox（https://dinox.info）：
 * - Token 粘贴后先「测试连接」，只有测试成功才加密落盘（Android
 *   Keystore），并生成可随 WebDAV/S3 设置同步的密文。
 * - 支持全量 / 增量（按 updateTime 游标）拉取、按卡片盒过滤、定时自动同步。
 * - 发送：主页长按笔记卡片 →「发送到Dinox」。
 *
 * 扩展点：后续第三方应用按同样的「Token 测试→保存 / 拉取 / 推送」形状
 * 在本 Tab 增加独立 Section + 各自的 Service，不要复用/污染 Dinox 状态。
 */
@Composable
fun EcosystemTab(snackbar: SnackbarHostState) {
    val settingsRepo = LocalSettings.current
    val dinox = LocalDinox.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)

    var tokenInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var boxInput by remember(settings.dinoxSyncBox) { mutableStateOf(settings.dinoxSyncBox) }
    var hasToken by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dinox.importSealedToken()
        hasToken = dinox.hasToken()
    }

    fun runOperation(operation: suspend () -> Result<String>) {
        if (busy) return
        busy = true
        lastError = null
        scope.launch {
            operation()
                .onSuccess { message ->
                    snackbar.showSnackbar(message)
                    hasToken = dinox.hasToken()
                }
                .onFailure { error -> lastError = error.message ?: "操作失败" }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "第三方同步。当前已接入：Dinox（AI 笔记）。Token 在 Dinox App → " +
                "设置 → 同步设置 → API Token 获取；请求发往 aisdk.chatgo.pro 开放接口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle("Dinox Token")
        if (hasToken) {
            Text(
                "✓ 已保存 Token（Android Keystore 加密，不进日志/备份明文）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "尚未保存 Token",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("粘贴 Dinox API Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    runOperation {
                        dinox.testAndSaveToken(tokenInput).also { result ->
                            if (result.isSuccess) tokenInput = ""
                        }
                    }
                },
                enabled = !busy && tokenInput.isNotBlank(),
            ) { Text("测试并保存") }
            if (hasToken) {
                OutlinedButton(onClick = { runOperation { dinox.testSavedToken() } }, enabled = !busy) {
                    Text("重新测试")
                }
                OutlinedButton(
                    onClick = {
                        runOperation {
                            dinox.clearToken()
                            Result.success("已清除本机 Token")
                        }
                    },
                    enabled = !busy,
                ) { Text("清除") }
            }
        }
        lastError?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        SectionTitle("从 Dinox 同步到本机")
        OutlinedTextField(
            value = boxInput,
            onValueChange = { boxInput = it },
            label = { Text("卡片盒名称（留空 = 全部；多个用「、」分隔）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            scope.launch {
                settingsRepo.update { it.copy(dinoxSyncBox = boxInput.trim()) }
                snackbar.showSnackbar("已保存卡片盒设置")
            }
        }) { Text("保存卡片盒设置") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { runOperation { dinox.syncFull() } }, enabled = !busy) {
                Text("全量同步")
            }
            OutlinedButton(onClick = { runOperation { dinox.syncIncremental() } }, enabled = !busy) {
                Text("增量同步")
            }
        }
        Text(
            text = if (settings.dinoxLastSyncAt > 0) {
                "上次同步：${Dates.formatTime(settings.dinoxLastSyncAt)}"
            } else {
                "还没有同步过（增量同步会从全部笔记开始）"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Dinox 的笔记会按 Markdown 原样导入（标题、标签、卡片盒对应信息保留），" +
                "增量同步只拉取上次之后有更新的笔记。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        SectionTitle("自动同步")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AutoSyncOptions.forEach { (minutes, label) ->
                FilterChip(
                    selected = settings.dinoxAutoSyncMinutes == minutes,
                    onClick = {
                        scope.launch {
                            settingsRepo.update { it.copy(dinoxAutoSyncMinutes = minutes) }
                        }
                        DinoxAutoSyncWorker.schedule(context, minutes)
                    },
                    label = { Text(label) },
                )
            }
        }
        Text(
            "自动同步在后台按间隔做一次增量拉取（需要网络）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        SectionTitle("发送到 Dinox")
        Text(
            "在主页长按笔记卡片选择「发送到Dinox」，会把这篇笔记的 Markdown、" +
                "标题、标签与原创建时间发送到 Dinox（卡片盒按上面的设置）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "更多第三方应用的接入方式与 Dinox 相同（Token 测试 → 加密保存 → 拉取/推送），" +
                "后续版本会继续扩充此页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
