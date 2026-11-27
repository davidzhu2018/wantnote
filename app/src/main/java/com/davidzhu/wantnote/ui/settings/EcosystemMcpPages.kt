package com.davidzhu.wantnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.davidzhu.wantnote.sync.McpConfig
import com.davidzhu.wantnote.sync.McpPlatform
import com.davidzhu.wantnote.sync.McpSyncService
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.WantButton
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
    DEDAO("得到大脑"),
}

/**
 * 0.18.2：生态互联两级子菜单。
 * 一级菜单：Dinox / Flomo / 得到大脑；
 * 二级菜单：Dinox 保留原全部同步功能，Flomo 与得到大脑提供 MCP 配置与双向同步。
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
            val flomoConfigured = settings.flomoMcpUrl.isNotBlank() || settings.flomoWebhookUrl.isNotBlank()
            val dedaoConfigured = settings.dedaoMcpUrl.isNotBlank()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "选择要连接的第三方笔记生态，支持多平台同步与 MCP 协议双向互通。",
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    )
                    EcosystemMenuRow(
                        title = "Flomo",
                        subtitle = if (flomoConfigured) {
                            "已配置 MCP / Webhook，支持双向同步"
                        } else {
                            "配置 Flomo MCP 与 Webhook，支持双向同步"
                        },
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = EcosystemPage.FLOMO },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    )
                    EcosystemMenuRow(
                        title = "得到大脑",
                        subtitle = if (dedaoConfigured) {
                            "已配置得到大脑（Get笔记）MCP，支持双向同步"
                        } else {
                            "配置得到大脑（Get笔记）MCP，支持双向同步"
                        },
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = EcosystemPage.DEDAO },
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
                SubPageBackBar(title = "Flomo MCP", onBack = { page = EcosystemPage.MENU })
                McpSyncSubPage(platform = McpPlatform.FLOMO, snackbar = snackbar)
            }
        }

        EcosystemPage.DEDAO -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SubPageBackBar(title = "得到大脑 MCP", onBack = { page = EcosystemPage.MENU })
                McpSyncSubPage(platform = McpPlatform.DEDAO, snackbar = snackbar)
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

@Composable
private fun McpSyncSubPage(
    platform: McpPlatform,
    snackbar: SnackbarHostState,
) {
    val settingsRepo = LocalSettings.current
    val notesRepo = LocalNotes.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var endpoint by remember(platform) { mutableStateOf("") }
    var token by remember(platform) { mutableStateOf("") }
    var webhook by remember(platform) { mutableStateOf("") }
    var dirty by remember(platform) { mutableStateOf(false) }
    var busy by remember(platform) { mutableStateOf(false) }
    var statusText by remember(platform) { mutableStateOf("") }

    LaunchedEffect(platform) {
        val s = runCatching { settingsRepo.get() }.getOrNull() ?: return@LaunchedEffect
        if (!dirty) {
            if (platform == McpPlatform.FLOMO) {
                endpoint = s.flomoMcpUrl
                token = s.flomoMcpToken
                webhook = s.flomoWebhookUrl
            } else {
                endpoint = s.dedaoMcpUrl
                token = s.dedaoMcpToken
            }
        }
    }

    val lastSyncAt = if (platform == McpPlatform.FLOMO) settings.flomoLastSyncAt else settings.dedaoLastSyncAt

    fun currentConfig(): McpConfig = McpConfig(
        platform = platform,
        endpointUrl = endpoint.trim(),
        token = token.trim(),
        webhookUrl = webhook.trim(),
        lastSyncAt = lastSyncAt,
    )

    suspend fun persistConfig() {
        val e = endpoint.trim()
        val t = token.trim()
        val w = webhook.trim()
        settingsRepo.update {
            if (platform == McpPlatform.FLOMO) {
                it.copy(flomoMcpUrl = e, flomoMcpToken = t, flomoWebhookUrl = w)
            } else {
                it.copy(dedaoMcpUrl = e, dedaoMcpToken = t)
            }
        }
        dirty = false
    }

    fun runSync(pull: Boolean, push: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            persistConfig()
            val result = McpSyncService.sync(currentConfig(), notesRepo, pull = pull, push = push)
            result.onSuccess { summary ->
                val now = System.currentTimeMillis()
                settingsRepo.update {
                    if (platform == McpPlatform.FLOMO) {
                        it.copy(flomoLastSyncAt = now)
                    } else {
                        it.copy(dedaoLastSyncAt = now)
                    }
                }
                statusText = summary.message
                snackbar.showSnackbar(summary.message)
            }.onFailure { err ->
                val msg = err.message ?: "同步失败"
                statusText = msg
                snackbar.showSnackbar(msg)
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
            text = if (platform == McpPlatform.FLOMO) {
                "支持接入 Flomo Max MCP（Streamable HTTP）实现双向同步；也可同时填写 Flomo API Webhook 地址用于快捷推送。密钥仅保存在本机。"
            } else {
                "支持接入得到大脑（原 Get笔记）MCP 服务（Streamable HTTP / 开放平台或自建 getnote-mcp 网关），连接后可自动发现读写工具并实现双向同步。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WantTextField(
            value = endpoint,
            onValueChange = {
                endpoint = it
                dirty = true
            },
            label = "${platform.label} MCP 地址（Streamable HTTP URL）",
            placeholder = platform.defaultEndpointHint,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        WantTextField(
            value = token,
            onValueChange = {
                token = it
                dirty = true
            },
            label = "MCP Token / API Key（选填）",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (platform == McpPlatform.FLOMO) {
            WantTextField(
                value = webhook,
                onValueChange = {
                    webhook = it
                    dirty = true
                },
                label = "Flomo API / Webhook URL（可选，支持直接推送）",
                placeholder = "https://flomoapp.com/iwh/...",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WantButton(
                onClick = {
                    scope.launch {
                        persistConfig()
                        snackbar.showSnackbar("${platform.label} MCP 配置已保存")
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("保存配置") }
            WantTextButton(
                onClick = {
                    if (busy) return@WantTextButton
                    busy = true
                    scope.launch {
                        persistConfig()
                        val res = McpSyncService.testConnection(currentConfig())
                        statusText = res.message
                        snackbar.showSnackbar(res.message)
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text(if (busy) "检测中…" else "测试连接") }
        }

        Spacer(modifier = Modifier.height(4.dp))
        SectionTitle("双向同步")
        WantButton(
            onClick = { runSync(pull = true, push = true) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "正在同步…" else "一键双向同步（拉取 + 推送）") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WantTextButton(
                onClick = { runSync(pull = true, push = false) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("仅拉取") }
            WantTextButton(
                onClick = { runSync(pull = false, push = true) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) { Text("仅推送") }
        }

        if (lastSyncAt > 0L) {
            Text(
                text = "上次同步：${Dates.formatTimestamp(lastSyncAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (statusText.isNotBlank()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        WantTextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(platform.docUrl)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("查看 ${platform.label} MCP / API 官方文档")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
