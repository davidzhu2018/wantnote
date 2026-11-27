package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantFilterChip
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.data.ThemeColorSet
import com.davidzhu.wantnote.data.ThemeCustoms
import com.davidzhu.wantnote.data.ThemePalette
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.theme.builtinColorSet
import com.davidzhu.wantnote.ui.theme.colorFromArgb
import kotlinx.coroutines.launch

/**
 * 0.9.0 主题定制：
 *
 * - 内置主题（纸色/墨色/青松/暮粉）的 11 个颜色都可以覆盖，
 *   「重置此主题」一键恢复出厂色。
 * - 可以把当前配色「另存为新主题」；自建主题可以删除。
 * - 全部持久化在 AppSettings.themeCustomJson（ThemeCustoms 序列化），
 *   立即全局生效（WantNoteTheme 每次从设置解析）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbarMessage = remember { mutableStateOf<String?>(null) }

    val customs = remember(settings.themeCustomJson) { ThemeCustoms.parse(settings.themeCustomJson) }
    var selected by remember(settings.themeName) { mutableStateOf(settings.themeName) }

    // 当前主题生效色的草稿（field → ARGB），保存时与出厂色对比生成覆盖。
    val draft = remember { mutableStateMapOf<String, Long>() }
    var editingField by remember { mutableStateOf<String?>(null) }
    var saveAsDialog by remember { mutableStateOf(false) }

    fun effectiveSet(key: String): ThemeColorSet {
        val builtIn = builtinColorSet(ThemePalette.fromStorage(key))
        return (customs[key] ?: builtIn).mergedOver(builtIn)
    }

    LaunchedEffect(selected, settings.themeCustomJson) {
        val set = effectiveSet(selected)
        ThemeColorSet.FIELDS.forEach { (field, getter) ->
            getter(set)?.let { draft[field] = it }
        }
    }

    fun persist(next: Map<String, ThemeColorSet>, themeName: String = selected) {
        scope.launch {
            settingsRepo.update {
                it.copy(themeCustomJson = ThemeCustoms.serialize(next), themeName = themeName)
            }
        }
    }

    SettingsSubScaffold(title = "主题定制", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    UiStyleDropdownRow(
                        selected = settings.uiStyleOption,
                        onSelect = { style ->
                            scope.launch { settingsRepo.update { it.copy(uiStyle = style.name) } }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                item {
                    Text(
                        "选择主题",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        ThemePalette.entries.forEach { palette ->
                            WantFilterChip(
                                selected = selected == palette.name,
                                onClick = { selected = palette.name },
                                label = { Text(palette.label) },
                            )
                        }
                        customs.keys.filter { ThemeCustoms.isUserTheme(it) }.forEach { key ->
                            WantFilterChip(
                                selected = selected == key,
                                onClick = { selected = key },
                                label = { Text(ThemeCustoms.displayName(key)) },
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    val isUser = ThemeCustoms.isUserTheme(selected)
                    Text(
                        if (isUser) {
                            "编辑「${ThemeCustoms.displayName(selected)}」（自建主题）"
                        } else {
                            "自定义「${ThemePalette.fromStorage(selected).label}」（内置主题，可重置）"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(ThemeColorSet.FIELDS.size) { index ->
                    val (field, getter) = ThemeColorSet.FIELDS[index]
                    val value = draft[field] ?: getter(effectiveSet(selected)) ?: 0xFF000000L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colorFromArgb(value), CircleShape)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                ThemeColorSet.LABELS[field] ?: field,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "#%08X".format(value),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        WantTextButton(onClick = { editingField = field }) {
                            Text("修改")
                        }
                    }
                }
                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    ) {
                        WantButton(onClick = {
                            val base = effectiveSet(selected)
                            val updated = customs.toMutableMap()
                            // 只保存与出厂色不同的字段（覆盖语义）。
                            updated[selected] = ThemeColorSet(
                                lightBg = draft["lightBg"]?.takeIf { it != base.lightBg },
                                lightInk = draft["lightInk"]?.takeIf { it != base.lightInk },
                                lightMuted = draft["lightMuted"]?.takeIf { it != base.lightMuted },
                                lightVariant = draft["lightVariant"]?.takeIf { it != base.lightVariant },
                                lightOutline = draft["lightOutline"]?.takeIf { it != base.lightOutline },
                                darkBg = draft["darkBg"]?.takeIf { it != base.darkBg },
                                darkInk = draft["darkInk"]?.takeIf { it != base.darkInk },
                                darkMuted = draft["darkMuted"]?.takeIf { it != base.darkMuted },
                                darkVariant = draft["darkVariant"]?.takeIf { it != base.darkVariant },
                                darkOutline = draft["darkOutline"]?.takeIf { it != base.darkOutline },
                                highlight = draft["highlight"]?.takeIf { it != base.highlight },
                            )
                            persist(updated)
                            snackbarMessage.value = "已保存并应用"
                        }) {
                            Text("保存修改")
                        }
                        WantTextButton(onClick = { saveAsDialog = true }) {
                            Text("另存为新主题")
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    ) {
                        if (!ThemeCustoms.isUserTheme(selected)) {
                            WantTextButton(onClick = {
                                val updated = customs.toMutableMap()
                                updated.remove(selected)
                                persist(updated)
                                snackbarMessage.value = "已重置为出厂配色"
                            }) {
                                Text("重置此主题")
                            }
                        } else {
                            WantTextButton(onClick = {
                                val updated = customs.toMutableMap()
                                updated.remove(selected)
                                persist(updated, themeName = ThemePalette.PAPER.name)
                                selected = ThemePalette.PAPER.name
                                snackbarMessage.value = "已删除主题"
                            }) {
                                Text("删除此主题")
                            }
                        }
                    }
                }
            }
    }

    // 单个颜色的编辑弹窗：预设色板 + 任意十六进制值。
    editingField?.let { field ->
        val current = draft[field] ?: 0xFF000000L
        var hexInput by remember(field) { mutableStateOf("#%08X".format(current)) }
        WantAlertDialog(
            onDismissRequest = { editingField = null },
            title = { Text(ThemeColorSet.LABELS[field] ?: field) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Tag.PALETTE.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(colorFromArgb(preset), CircleShape)
                                    .clickable { hexInput = "#%08X".format(preset) }
                                    .border(
                                        width = if (preset == current) 2.dp else 0.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                    WantTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it },
                        label = "ARGB 十六进制，如 #FF3F2A1A",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                WantTextButton(onClick = {
                    parseArgb(hexInput)?.let { draft[field] = it }
                    editingField = null
                }) { Text("确定") }
            },
            dismissButton = {
                WantTextButton(onClick = { editingField = null }) { Text("取消") }
            },
        )
    }

    if (saveAsDialog) {
        var name by remember { mutableStateOf("") }
        WantAlertDialog(
            onDismissRequest = { saveAsDialog = false },
            title = { Text("另存为新主题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WantTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "主题名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.entries.take(3).forEach { (_, value) ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(colorFromArgb(value), RoundedCornerShape(6.dp)),
                            )
                        }
                        Text(
                            "保存当前配色为自建主题",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                WantTextButton(onClick = {
                    val clean = name.trim()
                    if (clean.isEmpty()) return@WantTextButton
                    val key = ThemeCustoms.userKey(clean)
                    val updated = customs.toMutableMap()
                    updated[key] = ThemeColorSet(
                        lightBg = draft["lightBg"],
                        lightInk = draft["lightInk"],
                        lightMuted = draft["lightMuted"],
                        lightVariant = draft["lightVariant"],
                        lightOutline = draft["lightOutline"],
                        darkBg = draft["darkBg"],
                        darkInk = draft["darkInk"],
                        darkMuted = draft["darkMuted"],
                        darkVariant = draft["darkVariant"],
                        darkOutline = draft["darkOutline"],
                        highlight = draft["highlight"],
                    )
                    persist(updated, themeName = key)
                    selected = key
                    saveAsDialog = false
                    snackbarMessage.value = "已创建主题：$clean"
                }) { Text("创建") }
            },
            dismissButton = {
                WantTextButton(onClick = { saveAsDialog = false }) { Text("取消") }
            },
        )
    }

    snackbarMessage.value?.let { message ->
        WantAlertDialog(
            onDismissRequest = { snackbarMessage.value = null },
            title = { Text(message) },
            confirmButton = {
                WantTextButton(onClick = { snackbarMessage.value = null }) { Text("好") }
            },
        )
    }
}

/** 解析 "#AARRGGBB" / "AARRGGBB" / "#RRGGBB" / "RRGGBB" 为 ARGB Long。 */
private fun parseArgb(raw: String): Long? {
    val hex = raw.trim().removePrefix("#")
    return when (hex.length) {
        6 -> (0xFF000000L or (hex.toLongOrNull(16) ?: return null))
        8 -> hex.toLongOrNull(16)
        else -> null
    }
}
