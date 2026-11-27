package com.davidzhu.wantnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.BuildConfig
import com.davidzhu.wantnote.R
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantCheckbox
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.update.UpdateCheck
import com.davidzhu.wantnote.update.UpdateCheckResult
import com.davidzhu.wantnote.update.UpdateInfo
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onUpdateStatus: (UpdateInfo?) -> Unit = {},
) {
    val context = LocalContext.current
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var checking by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<UpdateInfo?>(null) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // 0.17.0：若已勾选「自动检查更新」，每次进入关于菜单时自动检查一次更新，发现新版则弹窗提示。
    LaunchedEffect(Unit) {
        val current = runCatching { settingsRepo.get() }.getOrNull()
        if (current == null || current.autoCheckUpdate) {
            if (!checking) {
                checking = true
                when (val result = UpdateCheck.check()) {
                    is UpdateCheckResult.Available -> {
                        onUpdateStatus(result.info)
                        prompt = result.info
                    }
                    UpdateCheckResult.UpToDate -> {
                        onUpdateStatus(null)
                    }
                    else -> Unit
                }
                checking = false
            }
        }
    }

    val titleStyle = MiuixTheme.textStyles.body1
    val descSizeSp = (titleStyle.fontSize.value - 2f).coerceAtLeast(11f).sp

    SettingsSubScaffold(title = "关于", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Image(
                painter = painterResource(id = R.drawable.wantnote_logo),
                contentDescription = "想记",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("想记，想记就记！", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "当前版本 ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
                    .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable {
                        val next = !settings.autoCheckUpdate
                        scope.launch { settingsRepo.update { it.copy(autoCheckUpdate = next) } }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WantCheckbox(
                    checked = settings.autoCheckUpdate,
                    onCheckedChange = { checked ->
                        scope.launch { settingsRepo.update { it.copy(autoCheckUpdate = checked) } }
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "自动检查更新",
                        style = titleStyle,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "开启后，每次冷启动或进入关于页面时自动检查新版本",
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = descSizeSp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            WantButton(
                onClick = {
                    if (checking) return@WantButton
                    checking = true
                    scope.launch {
                        when (val result = UpdateCheck.check()) {
                            is UpdateCheckResult.Available -> {
                                onUpdateStatus(result.info)
                                prompt = result.info
                            }
                            UpdateCheckResult.UpToDate -> {
                                onUpdateStatus(null)
                                snackbar.showSnackbar("已是最新版本 ${BuildConfig.VERSION_NAME}")
                            }
                            is UpdateCheckResult.OpenReleases -> {
                                snackbar.showSnackbar(result.reason)
                                openUrl(result.url)
                            }
                            is UpdateCheckResult.Error -> snackbar.showSnackbar(result.message)
                        }
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (checking) "正在检查…" else "检查更新")
            }
        }
    }

    val info = prompt
    if (info != null) {
        WantAlertDialog(
            onDismissRequest = { prompt = null },
            title = { Text("发现新版本 ${info.versionName}") },
            text = { Text("GitHub 上的 ${info.releaseName} 新于当前的 ${BuildConfig.VERSION_NAME}。要下载吗？") },
            confirmButton = {
                WantTextButton(
                    onClick = {
                        openUrl(info.apkUrl ?: info.htmlUrl)
                        prompt = null
                    },
                ) { Text("下载") }
            },
            dismissButton = {
                WantTextButton(onClick = { prompt = null }) { Text("稍后") }
            },
        )
    }
}
