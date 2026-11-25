package com.davidzhu.wantnote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantButton
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import com.davidzhu.wantnote.ui.components.WantTextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.BuildConfig
import com.davidzhu.wantnote.R
import com.davidzhu.wantnote.update.UpdateCheck
import com.davidzhu.wantnote.update.UpdateCheckResult
import com.davidzhu.wantnote.update.UpdateInfo
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var checking by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<UpdateInfo?>(null) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

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
            WantButton(
                onClick = {
                    if (checking) return@WantButton
                    checking = true
                    scope.launch {
                        when (val result = UpdateCheck.check()) {
                            is UpdateCheckResult.Available -> prompt = result.info
                            UpdateCheckResult.UpToDate -> snackbar.showSnackbar("已是最新版本 ${BuildConfig.VERSION_NAME}")
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
