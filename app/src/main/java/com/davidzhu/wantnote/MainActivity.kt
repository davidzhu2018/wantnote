package com.davidzhu.wantnote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.AppStorage
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalBackup
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.LocalSync
import com.davidzhu.wantnote.ui.LocalTransfer
import com.davidzhu.wantnote.ui.WantNoteApp
import com.davidzhu.wantnote.ui.theme.WantNoteTheme
import com.davidzhu.wantnote.ui.theme.loadEditorFontFamily

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WantNoteApplication
        setContent {
            val settings by app.settingsRepository.observe()
                .collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
            val context = LocalContext.current
            val fontFamily = remember(settings.editorFontId, settings.customFontPath) {
                loadEditorFontFamily(context, settings)
            }
            WantNoteTheme(settings = settings, fontFamily = fontFamily) {
                CompositionLocalProvider(
                    LocalNotes provides app.repository,
                    LocalSettings provides app.settingsRepository,
                    LocalMedia provides app.mediaStorage,
                    LocalAi provides app.aiService,
                    LocalSync provides app.cloudSync,
                    LocalBackup provides app.backupService,
                    LocalTransfer provides app.importExportService,
                ) {
                    WantNoteApp()
                    StorageAccessGate()
                }
            }
        }
    }
}

/**
 * 存储权限引导：笔记数据优先保存在用户可见的 Documents/WantNote（卸载后可
 * 找回）。未授权时每次启动弹一次说明，用户可以「暂不」跳过（数据继续留在
 * 应用私有目录，功能不受影响）；设置里也有同款说明和授权入口。
 */
@Composable
private fun StorageAccessGate() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(AppStorage.hasStorageAccess(context)) }
    var showPrompt by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = AppStorage.hasStorageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> /* ON_RESUME observer 会刷新 hasAccess */ }

    fun goToGrant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
        } else {
            // Android 10 及以下：运行时请求 WRITE_EXTERNAL_STORAGE 即可。
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    if (!hasAccess && showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text("允许想记访问文件吗？") },
            text = {
                Text(
                    "想记的笔记数据库和附件会保存在「Documents/WantNote」目录，" +
                        "卸载应用后这些数据仍能找回。需要“所有文件访问”权限才能读写该目录。\n\n" +
                        "不授权的话，数据会继续保存在应用私有目录（卸载会被清空），" +
                        "以后仍可在 设置 → 通用设置 里开启。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrompt = false
                    goToGrant()
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) { Text("暂不") }
            },
        )
    }
}
