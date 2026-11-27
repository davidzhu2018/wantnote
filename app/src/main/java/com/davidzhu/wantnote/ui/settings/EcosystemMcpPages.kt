package com.davidzhu.wantnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.sync.ExternalReadingAutoSyncWorker
import com.davidzhu.wantnote.sync.ExternalReadingSyncService
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantFilterChip
import com.davidzhu.wantnote.ui.components.WantListItem
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTextField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class EcosystemPage(val title: String) {
    MENU("生态互联"),
    DINOX("Dinox"),
    FLOMO("Flomo"),
    READWISE("Readwise"),
    WEREAD("微信读书"),
}

private val AUTO_SYNC_INTERVALS = listOf(
    0 to "仅手动",
    15 to "每15分钟",
    30 to "每30分钟",
    60 to "每1小时",
    360 to "每6小时",
    1440 to "每天",
)

/**
 * 0.19.0：生态互联两级子菜单：
 * - Dinox（原样保留）
 * - Flomo（去掉 MCP，仅保留 API URL 配置，配合主页卡片「发送到 Flomo」）
 * - Readwise（单向同步，按类别映射到不同卡片盒，支持全量/增量与自动/手动同步）
 * - 微信读书（单向同步，API Key + 目标卡片盒，支持全量/增量与自动/手动同步）
 */
@Composable
fun EcosystemHubTab(snackbar: SnackbarHostState) {
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    var page by remember { mutableStateOf(EcosystemPage.MENU) }

    BackHandler(enabled = page != EcosystemPage.MENU) {
        page = EcosystemPage.MENU
    }

    when (page) {
        EcosystemPage.MENU -> {
            val titleStyle = MiuixTheme.textStyles.body1
            val descSizeSp = (titleStyle.fontSize.value - 2f).coerceAtLeast(11f).sp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "连接第三方笔记与阅读生态，支持单向拉取与快捷发送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                ) {
                    EcosystemMenuRow(
                        title = "Dinox",
                        subtitle = "卡片盒同步规则、全量/增量同步与定时同步",
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = EcosystemPage.DINOX },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    EcosystemMenuRow(
                        title = "Flomo",
                        subtitle = if (settings.flomoWebhookUrl.isNotBlank()) {
                            "已配置 Flomo API URL，可在主页长按或批量发送到 Flomo"
                        } else {
                            "配置 Flomo API URL，支持将笔记发送到 Flomo"
                        },
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = EcosystemPage.FLOMO },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    EcosystemMenuRow(
                        title = "Readwise",
                        subtitle = if (settings.readwiseToken.isNotBlank()) {
                            "已配置 Token · 支持按类别同步到不同卡片盒（单向）"
                        } else {
                            "单向同步 Readwise 高亮与摘录，支持按类别归入不同卡片盒"
                        },
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = EcosystemPage.READWISE },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    EcosystemMenuRow(
                        title = "微信读书",
                        subtitle = if (settings.wereadApiKey.isNotBlank()) {
                            "已配置 API Key · 支持同步划线与高亮到指定卡片盒（单向）"
                        } else {
                            "输入微信读书 API Key，单向同步划线与高亮到指定卡片盒"
                        },
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = EcosystemPage.WEREAD },
                    )
                }
            }
        }

        EcosystemPage.DINOX -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubPageBackBar(title = "Dinox", onBack = { page = EcosystemPage.MENU })
                EcosystemTab(snackbar = snackbar)
            }
        }

        EcosystemPage.FLOMO -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubPageBackBar(title = "Flomo", onBack = { page = EcosystemPage.MENU })
                FlomoApiSubPage(snackbar = snackbar)
            }
        }

        EcosystemPage.READWISE -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubPageBackBar(title = "Readwise", onBack = { page = EcosystemPage.MENU })
                ReadwiseSyncSubPage(snackbar = snackbar)
            }
        }

        EcosystemPage.WEREAD -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubPageBackBar(title = "微信读书", onBack = { page = EcosystemPage.MENU })
                WeReadSyncSubPage(snackbar = snackbar)
            }
        }
    }
}

@Composable
private fun EcosystemMenuRow(
    title: String,
    subtitle: String,
    titleStyle: androidx.compose.ui.text.TextStyle,
    descSizeSp: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    WantListItem(
        headlineContent = {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = descSizeSp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SubPageBackBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回生态互联")
        Text(
            text = "生态互联 / $title",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
}

/** Flomo 仅支持官方 URL API 输入，供卡片长按与多选队列「发送到 Flomo」使用。 */
@Composable
private fun FlomoApiSubPage(snackbar: SnackbarHostState) {
    val settingsRepo = LocalSettings.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var webhookUrl by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stored = runCatching { settingsRepo.get().flomoWebhookUrl }.getOrNull().orEmpty()
        if (!dirty) webhookUrl = stored
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "输入你的 Flomo 专属 API URL 后，即可在主页长按卡片或多选菜单中将笔记发送到 Flomo（自动附带笔记标签）。URL 仅保存在本机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WantTextField(
            value = webhookUrl,
            onValueChange = {
                webhookUrl = it
                dirty = true
            },
            label = "Flomo API URL",
            placeholder = "https://flomoapp.com/iwh/...",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WantButton(
                onClick = {
                    val clean = webhookUrl.trim()
                    scope.launch {
                        settingsRepo.update { it.copy(flomoWebhookUrl = clean) }
                        webhookUrl = clean
                        dirty = false
                        snackbar.showSnackbar("Flomo API URL 已保存")
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存配置") }
            WantTextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://flomoapp.com/mine?source=incoming_webhook")),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("获取 API URL") }
        }
    }
}

/** Readwise 单向同步子菜单（按类别映射到不同卡片盒，支持全量/增量与自动/手动同步）。 */
@Composable
private fun ReadwiseSyncSubPage(snackbar: SnackbarHostState) {
    val settingsRepo = LocalSettings.current
    val notesRepo = LocalNotes.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val boxes by notesRepo.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var token by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val stored = runCatching { settingsRepo.get().readwiseToken }.getOrNull().orEmpty()
        if (!dirty) token = stored
    }

    val categoryBoxMap = remember(settings.readwiseCategoryBoxesJson) {
        ExternalReadingSyncService.parseCategoryBoxMap(settings.readwiseCategoryBoxesJson)
    }

    fun runReadwiseSync(fullSync: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val cleanToken = token.trim()
            settingsRepo.update { it.copy(readwiseToken = cleanToken) }
            dirty = false
            val res = ExternalReadingSyncService.syncReadwise(
                token = cleanToken,
                categoryBoxesJson = settingsRepo.get().readwiseCategoryBoxesJson,
                lastSyncAt = settingsRepo.get().readwiseLastSyncAt,
                fullSync = fullSync,
                repository = notesRepo,
            )
            res.onSuccess { summary ->
                settingsRepo.update { it.copy(readwiseLastSyncAt = System.currentTimeMillis()) }
                statusText = summary.message
                snackbar.showSnackbar(summary.message)
            }.onFailure { err ->
                statusText = err.message ?: "Readwise 同步失败"
                snackbar.showSnackbar(statusText)
            }
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
            text = "单向同步 Readwise 高亮与笔记到想记，可按 Readwise 类别（书籍/文章/推文/播客/补充）分别归入不同卡片盒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WantTextField(
            value = token,
            onValueChange = {
                token = it
                dirty = true
            },
            label = "Readwise Access Token",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WantButton(
                onClick = {
                    val clean = token.trim()
                    scope.launch {
                        settingsRepo.update { it.copy(readwiseToken = clean) }
                        dirty = false
                        snackbar.showSnackbar("Readwise Token 已保存")
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("保存 Token") }
            WantTextButton(
                onClick = {
                    if (busy) return@WantTextButton
                    busy = true
                    scope.launch {
                        val res = ExternalReadingSyncService.testReadwiseToken(token)
                        val msg = res.getOrElse { it.message ?: "验证失败" }
                        statusText = msg
                        snackbar.showSnackbar(msg)
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("验证连接") }
        }

        SectionTitle("按 Readwise 类别分配卡片盒")
        ExternalReadingSyncService.READWISE_CATEGORIES.forEach { (catKey, catLabel) ->
            BoxTargetDropdown(
                label = catLabel,
                boxes = boxes,
                selectedBoxId = categoryBoxMap[catKey].orEmpty(),
                onSelect = { boxId ->
                    val next = categoryBoxMap.toMutableMap()
                    if (boxId.isBlank()) next.remove(catKey) else next[catKey] = boxId
                    val serialized = ExternalReadingSyncService.serializeCategoryBoxMap(next)
                    scope.launch { settingsRepo.update { it.copy(readwiseCategoryBoxesJson = serialized) } }
                },
            )
        }

        SectionTitle("自动同步频率")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AUTO_SYNC_INTERVALS.forEach { (mins, label) ->
                WantFilterChip(
                    selected = settings.readwiseAutoSyncMinutes == mins,
                    onClick = {
                        scope.launch {
                            settingsRepo.update { it.copy(readwiseAutoSyncMinutes = mins) }
                            ExternalReadingAutoSyncWorker.schedule(
                                context,
                                ExternalReadingAutoSyncWorker.TARGET_READWISE,
                                mins,
                            )
                            snackbar.showSnackbar(if (mins == 0) "Readwise 已设为仅手动同步" else "Readwise 已开启自动同步（$label）")
                        }
                    },
                    label = { Text(label) },
                )
            }
        }

        SectionTitle("手动同步")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WantButton(
                onClick = { runReadwiseSync(fullSync = false) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(if (busy) "同步中…" else "增量同步") }
            WantButton(
                onClick = { runReadwiseSync(fullSync = true) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(if (busy) "同步中…" else "全量同步") }
        }

        if (settings.readwiseLastSyncAt > 0L) {
            Text(
                text = "上次同步：${Dates.formatTimestamp(settings.readwiseLastSyncAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (statusText.isNotBlank()) {
            Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        WantTextButton(
            onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://readwise.io/access_token"))) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("获取 Readwise Access Token") }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 微信读书单向同步子菜单（API Key + 目标卡片盒 + 全量/增量 + 自动/手动同步）。 */
@Composable
private fun WeReadSyncSubPage(snackbar: SnackbarHostState) {
    val settingsRepo = LocalSettings.current
    val notesRepo = LocalNotes.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val boxes by notesRepo.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val stored = runCatching { settingsRepo.get().wereadApiKey }.getOrNull().orEmpty()
        if (!dirty) apiKey = stored
    }

    fun runWeReadSync(fullSync: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val cleanKey = apiKey.trim()
            settingsRepo.update { it.copy(wereadApiKey = cleanKey) }
            dirty = false
            val res = ExternalReadingSyncService.syncWeRead(
                apiKey = cleanKey,
                targetBoxId = settingsRepo.get().wereadBoxId,
                lastSyncAt = settingsRepo.get().wereadLastSyncAt,
                fullSync = fullSync,
                repository = notesRepo,
            )
            res.onSuccess { summary ->
                settingsRepo.update { it.copy(wereadLastSyncAt = System.currentTimeMillis()) }
                statusText = summary.message
                snackbar.showSnackbar(summary.message)
            }.onFailure { err ->
                statusText = err.message ?: "微信读书同步失败"
                snackbar.showSnackbar(statusText)
            }
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
            text = "输入微信读书 API Key，单向同步微信读书里的划线与高亮到指定卡片盒（支持自动定时同步与手动全量/增量同步）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WantTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                dirty = true
            },
            label = "微信读书 API Key",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        WantButton(
            onClick = {
                val clean = apiKey.trim()
                scope.launch {
                    settingsRepo.update { it.copy(wereadApiKey = clean) }
                    dirty = false
                    snackbar.showSnackbar("微信读书 API Key 已保存")
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存 API Key") }

        BoxTargetDropdown(
            label = "同步目标卡片盒",
            boxes = boxes,
            selectedBoxId = settings.wereadBoxId,
            onSelect = { boxId ->
                scope.launch { settingsRepo.update { it.copy(wereadBoxId = boxId) } }
            },
        )

        SectionTitle("自动同步频率")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AUTO_SYNC_INTERVALS.forEach { (mins, label) ->
                WantFilterChip(
                    selected = settings.wereadAutoSyncMinutes == mins,
                    onClick = {
                        scope.launch {
                            settingsRepo.update { it.copy(wereadAutoSyncMinutes = mins) }
                            ExternalReadingAutoSyncWorker.schedule(
                                context,
                                ExternalReadingAutoSyncWorker.TARGET_WEREAD,
                                mins,
                            )
                            snackbar.showSnackbar(if (mins == 0) "微信读书已设为仅手动同步" else "微信读书已开启自动同步（$label）")
                        }
                    },
                    label = { Text(label) },
                )
            }
        }

        SectionTitle("手动同步")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WantButton(
                onClick = { runWeReadSync(fullSync = false) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(if (busy) "同步中…" else "增量同步") }
            WantButton(
                onClick = { runWeReadSync(fullSync = true) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(if (busy) "同步中…" else "全量同步") }
        }

        if (settings.wereadLastSyncAt > 0L) {
            Text(
                text = "上次同步：${Dates.formatTimestamp(settings.wereadLastSyncAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (statusText.isNotBlank()) {
            Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
