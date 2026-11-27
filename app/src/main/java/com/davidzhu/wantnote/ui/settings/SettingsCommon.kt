package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.davidzhu.wantnote.ui.components.WantBackTopBar
import com.davidzhu.wantnote.ui.components.WantScaffold
import com.davidzhu.wantnote.ui.components.WantSectionTitle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String) {
    WantSectionTitle(text = text)
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SettingsSubScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    WantScaffold(
        containerColor = MiuixTheme.colorScheme.background,
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        topBar = {
            WantBackTopBar(
                title = title,
                onBack = onBack,
                actions = { actions() },
            )
        },
        content = content,
    )
}

@Composable
fun UiStyleDropdownRow(
    selected: com.davidzhu.wantnote.data.UiStyle,
    onSelect: (com.davidzhu.wantnote.data.UiStyle) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = "UI 风格",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Box {
            com.davidzhu.wantnote.ui.components.WantTextButton(
                onClick = { expanded = !expanded },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = androidx.compose.ui.Modifier.weight(1f),
                        horizontalAlignment = androidx.compose.ui.Alignment.Start,
                    ) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = selected.label,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                        top.yukonga.miuix.kmp.basic.Text(
                            text = if (selected == com.davidzhu.wantnote.data.UiStyle.COUI) {
                                "COUI（ColorOS 17 凝光视效、连体圆角卡片、胶囊按钮）"
                            } else {
                                "MiuiX（HyperOS 立体液态玻璃风格，默认）"
                            },
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = if (expanded) {
                            androidx.compose.material.icons.Icons.Outlined.ArrowDropUp
                        } else {
                            androidx.compose.material.icons.Icons.Outlined.ArrowDropDown
                        },
                        contentDescription = null,
                    )
                }
            }
            com.davidzhu.wantnote.ui.components.WantDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.9f),
            ) {
                com.davidzhu.wantnote.data.UiStyle.entries.forEach { option ->
                    com.davidzhu.wantnote.ui.components.WantDropdownMenuItem(
                        text = {
                            top.yukonga.miuix.kmp.basic.Text(
                                text = if (option == selected) "✓ ${option.label}" else option.label,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
