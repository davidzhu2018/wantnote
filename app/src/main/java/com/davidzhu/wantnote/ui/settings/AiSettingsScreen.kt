package com.davidzhu.wantnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.davidzhu.wantnote.ai.AiPresets
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantCheckbox
import com.davidzhu.wantnote.ui.components.WantDropdownMenu
import com.davidzhu.wantnote.ui.components.WantDropdownMenuItem
import com.davidzhu.wantnote.ui.components.WantFilterChip
import com.davidzhu.wantnote.ui.components.WantListItem
import com.davidzhu.wantnote.ui.components.WantScaffold
import com.davidzhu.wantnote.ui.components.WantSwitch
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTextField
import com.davidzhu.wantnote.ui.components.WantTopBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class AiSettingsPage(val title: String) {
    MENU("AI 设置"),
    PROVIDERS("AI提供商配置"),
    MODELS("AI模型选择"),
    CAPABILITIES("AI能力选择"),
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val ai = LocalAi.current
    val settingsRepo = LocalSettings.current
    val providers by ai.observeProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var page by remember { mutableStateOf(AiSettingsPage.MENU) }
    var showAddProvider by remember { mutableStateOf(false) }
    var addingPreset by remember { mutableStateOf<String?>(null) }
    var editingProviderId by remember { mutableStateOf<String?>(null) }

    val navigateBack: () -> Unit = {
        if (page != AiSettingsPage.MENU) {
            page = AiSettingsPage.MENU
        } else {
            onBack()
        }
    }

    BackHandler(enabled = page != AiSettingsPage.MENU) {
        page = AiSettingsPage.MENU
    }

    val modelChoices = providers.flatMap { provider ->
        val enabled = provider.enabledModelIds().toSet()
        val available = provider.availableModels()
        val ordered = if (available.isEmpty()) {
            provider.enabledModels()
        } else {
            available.sortedByDescending { it.id in enabled }
        }
        ordered.map { model -> Triple(provider, model.id, "${provider.name} / ${model.name}") }
    }

    val enabledCapabilityCount = listOf(
        settings.aiEnableTitle,
        settings.aiEnableTag,
        settings.aiEnableBox,
        settings.aiEnableSummary,
        settings.aiEnableInsight,
    ).count { it }

    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WantTopBar(
                title = page.title,
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when (page) {
            AiSettingsPage.MENU -> {
                val titleStyle = MiuixTheme.textStyles.body1
                val descSizeSp = (titleStyle.fontSize.value - 2f).coerceAtLeast(11f).sp
                val summaryModelLabel = modelChoices
                    .firstOrNull { it.first.id == settings.aiSummaryProviderId && it.second == settings.aiSummaryModelId }
                    ?.third ?: "未选择"
                val titleTagModelLabel = modelChoices
                    .firstOrNull { it.first.id == settings.aiTitleTagProviderId && it.second == settings.aiTitleTagModelId }
                    ?.third ?: "未选择"

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    AiMenuRow(
                        title = "AI提供商配置",
                        subtitle = if (providers.isEmpty()) {
                            "尚未添加提供商，点击查看或新增"
                        } else {
                            "已添加 ${providers.size} 个提供商，支持修改与新增"
                        },
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = AiSettingsPage.PROVIDERS },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    AiMenuRow(
                        title = "AI模型选择",
                        subtitle = "提炼：$summaryModelLabel · 标题/标签：$titleTagModelLabel",
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = AiSettingsPage.MODELS },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    AiMenuRow(
                        title = "AI能力选择",
                        subtitle = "已开启 $enabledCapabilityCount/5 项 AI 能力（标题、标签、卡片盒、提炼、洞察）",
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onClick = { page = AiSettingsPage.CAPABILITIES },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Text(
                        "密钥只保存在本机，不会写入日志或上传。关闭任意 AI 能力开关后，自动生成时将跳过该项并给出提示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }

            AiSettingsPage.PROVIDERS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "密钥只保存在本机，不会写入日志或上传。可修改已添加的提供商，或点击下方入口新增。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已添加的提供商（${providers.size}）", style = MaterialTheme.typography.titleMedium)
                        WantButton(
                            onClick = {
                                showAddProvider = !showAddProvider
                                if (!showAddProvider) addingPreset = null
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                if (showAddProvider) "收起新增" else "添加新的提供商",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }

                    if (showAddProvider) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                                .border(
                                    1.dp,
                                    MiuixTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(16.dp),
                                )
                                .padding(12.dp),
                        ) {
                            Text("选择预设或自定义接口", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "内置 DeepSeek、智谱、Kimi、阿里云百炼、千问，也可添加自定义 OpenAI 兼容接口。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                            AiPresets.ALL.chunked(3).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { preset ->
                                        WantFilterChip(
                                            selected = addingPreset == preset.id,
                                            onClick = {
                                                addingPreset = if (addingPreset == preset.id) null else preset.id
                                            },
                                            label = { Text(preset.name) },
                                        )
                                    }
                                }
                            }
                            val presetId = addingPreset
                            if (presetId != null) {
                                val preset = AiPresets.byId(presetId)
                                if (preset != null) {
                                    ProviderEditor(
                                        initial = AiProvider(
                                            id = AiService.newProviderId(),
                                            presetId = preset.id,
                                            name = preset.name,
                                            baseUrl = preset.baseUrl,
                                            apiKey = "",
                                        ),
                                        isNew = true,
                                        onOpenKeyUrl = { url ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        },
                                        onSave = { provider ->
                                            scope.launch {
                                                ai.upsertProvider(provider)
                                                addingPreset = null
                                                showAddProvider = false
                                                editingProviderId = provider.id
                                                snackbar.showSnackbar("已添加提供商，可点击「获取可用模型」。")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (providers.isEmpty()) {
                        Text(
                            "还没有添加服务提供商，请点击右上角「添加新的提供商」。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        providers.forEach { provider ->
                            val isEditing = editingProviderId == provider.id
                            Spacer(modifier = Modifier.height(10.dp))
                            ProviderCard(
                                provider = provider,
                                expanded = isEditing,
                                onToggleExpand = {
                                    editingProviderId = if (isEditing) null else provider.id
                                },
                                onSave = { updated ->
                                    scope.launch {
                                        ai.upsertProvider(updated)
                                        snackbar.showSnackbar("已保存提供商修改。")
                                    }
                                },
                                onFetch = {
                                    scope.launch {
                                        val result = ai.fetchModels(provider.id)
                                        result.onSuccess { models ->
                                            snackbar.showSnackbar("已获取 ${models.size} 个模型，请勾选要使用的。")
                                        }.onFailure { error ->
                                            snackbar.showSnackbar(error.message ?: "获取模型失败")
                                        }
                                    }
                                },
                                onToggleModel = { modelId, enabled ->
                                    scope.launch {
                                        val current = ai.getProvider(provider.id) ?: return@launch
                                        val next = current.enabledModelIds().toMutableList()
                                        if (enabled && modelId !in next) next += modelId
                                        if (!enabled) next.remove(modelId)
                                        ai.setEnabledModels(provider.id, next)
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        if (editingProviderId == provider.id) editingProviderId = null
                                        ai.deleteProvider(provider.id)
                                        snackbar.showSnackbar("已删除提供商「${provider.name}」。")
                                    }
                                },
                                onOpenKeyUrl = { url ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            AiSettingsPage.MODELS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text("用于提炼总结与洞察的默认模型", style = MaterialTheme.typography.titleMedium)
                    ModelDropdown(
                        label = "提炼总结的默认模型",
                        choices = modelChoices,
                        providerId = settings.aiSummaryProviderId,
                        modelId = settings.aiSummaryModelId,
                        onSelect = { providerId, modelId ->
                            scope.launch {
                                settingsRepo.update {
                                    it.copy(aiSummaryProviderId = providerId, aiSummaryModelId = modelId)
                                }
                                snackbar.showSnackbar("已更新提炼默认模型")
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("用于自动标题、标签与卡片盒的默认模型", style = MaterialTheme.typography.titleMedium)
                    ModelDropdown(
                        label = "自动标题和标签的默认模型",
                        choices = modelChoices,
                        providerId = settings.aiTitleTagProviderId,
                        modelId = settings.aiTitleTagModelId,
                        onSelect = { providerId, modelId ->
                            scope.launch {
                                settingsRepo.update {
                                    it.copy(aiTitleTagProviderId = providerId, aiTitleTagModelId = modelId)
                                }
                                snackbar.showSnackbar("已更新自动标题/标签默认模型")
                            }
                        },
                    )
                    if (modelChoices.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        WantButton(onClick = { page = AiSettingsPage.PROVIDERS }) {
                            Text("去配置 AI 提供商")
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            AiSettingsPage.CAPABILITIES -> {
                val titleStyle = MiuixTheme.textStyles.body1
                val descSizeSp = (titleStyle.fontSize.value - 2f).coerceAtLeast(11f).sp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "勾选需要启用的 AI 能力（默认全部开启）。关闭某项开关后，触发自动生成时将跳过该项并提示「XXXX能力已关闭」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CapabilityToggleRow(
                        title = "AI生成标题",
                        subtitle = "保存笔记、编辑器 AI 按钮或长按菜单自动生成标题",
                        checked = settings.aiEnableTitle,
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(aiEnableTitle = checked) } }
                        },
                    )
                    CapabilityToggleRow(
                        title = "AI生成标签",
                        subtitle = "保存笔记、编辑器 AI 按钮或长按菜单自动生成标签",
                        checked = settings.aiEnableTag,
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(aiEnableTag = checked) } }
                        },
                    )
                    CapabilityToggleRow(
                        title = "AI分配卡片盒",
                        subtitle = "保存笔记、编辑器 AI 按钮或长按菜单自动归入已有卡片盒",
                        checked = settings.aiEnableBox,
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(aiEnableBox = checked) } }
                        },
                    )
                    CapabilityToggleRow(
                        title = "AI提炼",
                        subtitle = "在洞察 → 总结中用 AI 提炼指定范围的笔记要点",
                        checked = settings.aiEnableSummary,
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(aiEnableSummary = checked) } }
                        },
                    )
                    CapabilityToggleRow(
                        title = "AI洞察",
                        subtitle = "在洞察 → 总结中跨笔记发掘关联主题、趋势与行动建议",
                        checked = settings.aiEnableInsight,
                        titleStyle = titleStyle,
                        descSizeSp = descSizeSp,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(aiEnableInsight = checked) } }
                        },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun AiMenuRow(
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
private fun CapabilityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    titleStyle: androidx.compose.ui.text.TextStyle,
    descSizeSp: androidx.compose.ui.unit.TextUnit,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = descSizeSp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        WantSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ModelDropdown(
    label: String,
    choices: List<Triple<AiProvider, String, String>>,
    providerId: String,
    modelId: String,
    onSelect: (String, String) -> Unit,
) {
    if (choices.isEmpty()) {
        Text(
            "先在「AI提供商配置」里添加提供商并获取/勾选模型，才会出现可选项。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.first.id == providerId && it.second == modelId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        WantTextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        selected?.third ?: "请选择模型",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                )
            }
        }
        WantDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            choices.forEach { (provider, id, displayLabel) ->
                val isCurrent = provider.id == providerId && id == modelId
                WantDropdownMenuItem(
                    text = { Text(displayLabel, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "当前默认模型",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        onSelect(provider.id, id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (AiProvider) -> Unit,
    onFetch: () -> Unit,
    onToggleModel: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenKeyUrl: (String) -> Unit,
) {
    val models = provider.availableModels()
    val enabled = provider.enabledModelIds().toSet()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    provider.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${provider.baseUrl.ifBlank { "未填写地址" }} · 已启用 ${enabled.size}/${models.size} 个模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WantTextButton(
                onClick = onToggleExpand,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(if (expanded) "收起" else "修改")
            }
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            ProviderEditor(
                initial = provider,
                isNew = false,
                onOpenKeyUrl = onOpenKeyUrl,
                onSave = onSave,
                extraActions = {
                    WantButton(onClick = onFetch) { Text("获取可用模型") }
                    WantTextButton(onClick = onDelete) { Text("删除") }
                },
            )
            if (models.isEmpty()) {
                Text(
                    "保存 key 后点击「获取可用模型」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("选择要使用的模型", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                models.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WantCheckbox(
                            checked = model.id in enabled,
                            onCheckedChange = { checked -> onToggleModel(model.id, checked) },
                        )
                        Text(model.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderEditor(
    initial: AiProvider,
    isNew: Boolean,
    onOpenKeyUrl: (String) -> Unit,
    onSave: (AiProvider) -> Unit,
    extraActions: @Composable (() -> Unit)? = null,
) {
    var name by remember(initial.id, initial.name) { mutableStateOf(initial.name) }
    var baseUrl by remember(initial.id, initial.baseUrl) { mutableStateOf(initial.baseUrl) }
    var apiKey by remember(initial.id, initial.apiKey) { mutableStateOf(initial.apiKey) }
    val preset = AiPresets.byId(initial.presetId)

    if (isNew && !preset?.note.isNullOrBlank()) {
        Text(
            preset!!.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    WantTextField(
        value = name,
        onValueChange = { name = it },
        label = "名称",
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
    WantTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it },
        label = "接口地址",
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
    WantTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = "API Key",
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    val keyUrl = preset?.keyUrl.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WantButton(
            onClick = {
                onSave(
                    initial.copy(
                        name = name.trim().ifBlank { preset?.name ?: "自定义" },
                        baseUrl = baseUrl.trim().trimEnd('/'),
                        apiKey = apiKey.trim(),
                    ),
                )
            },
        ) {
            Text(if (isNew) "保存提供商" else "保存")
        }
        if (keyUrl.isNotBlank()) {
            WantTextButton(onClick = { onOpenKeyUrl(keyUrl) }) {
                Text("打开申请页面")
            }
        }
        extraActions?.invoke()
    }
}
