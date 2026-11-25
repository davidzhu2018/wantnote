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
