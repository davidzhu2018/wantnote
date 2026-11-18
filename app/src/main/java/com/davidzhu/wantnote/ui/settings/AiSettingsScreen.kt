package com.davidzhu.wantnote.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.ai.AiPresets
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.data.AiProvider
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val ai = LocalAi.current
    val settingsRepo = LocalSettings.current
    val providers by ai.observeProviders().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var addingPreset by remember { mutableStateOf<String?>(null) }

    val modelChoices = providers.flatMap { provider ->
        val enabled = provider.enabledModelIds().toSet()
        val available = provider.availableModels()
        // Dropdown lists every fetched model; ones ticked as enabled come first.
        val ordered = if (available.isEmpty()) {
            provider.enabledModels()
        } else {
            available.sortedByDescending { it.id in enabled }
        }
        ordered.map { model -> Triple(provider, model.id, "${provider.name} / ${model.name}") }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "密钥只保存在本机，不会写入日志或上传。填写 key 后可一键获取可用模型，再勾选要使用的模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("用于提炼总结的默认模型", style = MaterialTheme.typography.titleMedium)
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
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("用于自动标题和标签的默认模型", style = MaterialTheme.typography.titleMedium)
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
                    }
                },
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("添加服务提供商", style = MaterialTheme.typography.titleMedium)
            Text(
                "内置 DeepSeek、智谱、Kimi、阿里云百炼、千问，也可添加自定义 OpenAI 兼容接口。接入方式对齐另一个项目的提供商目录：预填地址，粘贴 key，再拉取模型清单。",
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
                        FilterChip(
                            selected = addingPreset == preset.id,
                            onClick = { addingPreset = if (addingPreset == preset.id) null else preset.id },
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
                                snackbar.showSnackbar("已保存，密钥仅存放在本机。")
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("已添加的提供商", style = MaterialTheme.typography.titleMedium)
            if (providers.isEmpty()) {
                Text(
                    "还没有添加服务提供商。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                providers.forEach { provider ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ProviderCard(
                        provider = provider,
                        onSave = { updated ->
                            scope.launch {
                                ai.upsertProvider(updated)
                                snackbar.showSnackbar("已保存。")
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
                                ai.deleteProvider(provider.id)
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
}

/**
 * Default-model picker as a dropdown over the models fetched from the
 * added providers (ticked/enabled models first). Replaces the old chip row.
 */
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
            "先添加提供商并获取/勾选模型，才会出现可选项。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.first.id == providerId && it.second == modelId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
        OutlinedButton(
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            choices.forEach { (provider, id, displayLabel) ->
                val isCurrent = provider.id == providerId && id == modelId
                DropdownMenuItem(
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
    onSave: (AiProvider) -> Unit,
    onFetch: () -> Unit,
    onToggleModel: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenKeyUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(provider.name, style = MaterialTheme.typography.titleSmall)
        ProviderEditor(
            initial = provider,
            isNew = false,
            onOpenKeyUrl = onOpenKeyUrl,
            onSave = onSave,
            extraActions = {
                Button(onClick = onFetch) { Text("获取可用模型") }
                TextButton(onClick = onDelete) { Text("删除") }
            },
        )
        val models = provider.availableModels()
        val enabled = provider.enabledModelIds().toSet()
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
                    Checkbox(
                        checked = model.id in enabled,
                        onCheckedChange = { checked -> onToggleModel(model.id, checked) },
                    )
                    Text(model.name, style = MaterialTheme.typography.bodyMedium)
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
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("名称") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = baseUrl,
        onValueChange = { baseUrl = it },
        label = { Text("接口地址") },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text("API Key") },
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
        Button(
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
            OutlinedButton(onClick = { onOpenKeyUrl(keyUrl) }) {
                Text("打开申请页面")
            }
        }
        extraActions?.invoke()
    }
}

