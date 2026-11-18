package com.davidzhu.wantnote.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.SyncMethod
import com.davidzhu.wantnote.ui.LocalBackup
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.LocalSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BackupSyncTabs = listOf("备份", "云同步")

/**
 * 0.7.0：设置里的「云同步」升级为「备份与同步」，包含两个 Tab：
 * - 备份：本地 zip 备份 / 恢复（笔记、块、标签、附件、字体、全部设置、AI 提供商）。
 * - 云同步：0.6.0 原 CloudSyncScreen 的 WebDAV / S3 能力原样保留。
 */
@Composable
fun BackupSyncScreen(onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    SettingsSubScaffold(title = "备份与同步", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tab) {
                BackupSyncTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> BackupTab(snackbar = snackbar)
                    else -> CloudSyncTab(snackbar = snackbar)
                }
            }
        }
    }
}

@Composable
private fun BackupTab(snackbar: SnackbarHostState) {
    val backup = LocalBackup.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupName by remember { mutableStateOf("") }

    val defaultBackupName = remember {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        "WantNote-备份-$stamp.zip"
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) {
            busy = false
            progress = ""
            scope.launch { snackbar.showSnackbar("已取消备份") }
        } else {
            scope.launch {
                runCatching {
                    val output = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("无法写入所选位置")
                    }
                    backup.createBackup(output) { progress = it }
                }.onSuccess { result ->
                    snackbar.showSnackbar(
                        "已备份 ${result.notes} 条笔记、${result.attachments} 个附件、${result.fonts} 个字体",
                    )
                }.onFailure { snackbar.showSnackbar(it.message ?: "备份失败") }
                progress = ""
                busy = false
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            scope.launch { snackbar.showSnackbar("已取消选择备份文件") }
        } else {
            pendingRestore = uri
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "备份会把全部笔记、块、标签、笔记-标签、附件/媒体、自定义字体，以及全部设置和 AI 提供商（含密钥）打包成一个 zip。恢复会用备份内容覆盖当前数据，请先确认文件来源。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        SectionTitle("备份")
        Text(
            "保存位置由系统文件选择器决定。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                progress = "正在准备备份…"
                pendingBackupName = defaultBackupName
                createLauncher.launch(defaultBackupName)
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("备份到文件（zip）") }

        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle("恢复")
        Text(
            "选择一个由本应用导出的备份 zip。恢复采用结构化导入，不会覆盖正在使用的数据库文件；笔记、标签、设置和 AI 提供商会写回当前数据目录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = {
                if (busy) return@OutlinedButton
                busy = true
                progress = "正在选择备份文件…"
                openLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("从文件恢复") }

        if (busy) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (progress.isNotBlank()) {
                Text(
                    progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (pendingBackupName.isNotBlank() && !busy) {
            Text(
                "上次选择的备份文件名：$pendingBackupName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                pendingRestore = null
                busy = false
                progress = ""
            },
            title = { Text("恢复备份") },
            text = {
                Text(
                    "恢复会覆盖当前的笔记、标签、设置和 AI 提供商。建议先做一次备份。要继续吗？",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestore = null
                        scope.launch {
                            progress = "正在读取备份…"
                            runCatching {
                                val input = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)
                                        ?: throw IllegalStateException("无法读取所选备份")
                                }
                                backup.restoreBackup(input) { progress = it }
                            }.onSuccess { result ->
                                val warning = if (result.missingAttachments > 0) {
                                    "，${result.missingAttachments} 个附件缺失已跳过"
                                } else {
                                    ""
                                }
                                snackbar.showSnackbar(
                                    "已恢复 ${result.notes} 条笔记、${result.attachments} 个附件$warning",
                                )
                            }.onFailure { snackbar.showSnackbar(it.message ?: "恢复失败") }
                            progress = ""
                            busy = false
                        }
                    },
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = false
                    progress = ""
                }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudSyncTab(snackbar: SnackbarHostState) {
    val settingsRepo = LocalSettings.current
    val sync = LocalSync.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    var method by remember { mutableStateOf(settings.syncMethodOption) }
    var expanded by remember { mutableStateOf(false) }
    var webdavUrl by remember { mutableStateOf(settings.webdavUrl) }
    var webdavUser by remember { mutableStateOf(settings.webdavUsername) }
    var webdavPassword by remember { mutableStateOf(settings.webdavPassword) }
    var s3Endpoint by remember { mutableStateOf(settings.s3Endpoint) }
    var s3Bucket by remember { mutableStateOf(settings.s3Bucket) }
    var s3Region by remember { mutableStateOf(settings.s3Region) }
    var s3Access by remember { mutableStateOf(settings.s3AccessKey) }
    var s3Secret by remember { mutableStateOf(settings.s3SecretKey) }
    var s3Prefix by remember { mutableStateOf(settings.s3Prefix) }

    LaunchedEffect(settings.id, settings.syncMethod, settings.webdavUrl, settings.s3Endpoint, settings.s3Bucket) {
        method = settings.syncMethodOption
        webdavUrl = settings.webdavUrl
        webdavUser = settings.webdavUsername
        webdavPassword = settings.webdavPassword
        s3Endpoint = settings.s3Endpoint
        s3Bucket = settings.s3Bucket
        s3Region = settings.s3Region
        s3Access = settings.s3AccessKey
        s3Secret = settings.s3SecretKey
        s3Prefix = settings.s3Prefix
    }

    fun draft(): AppSettings = settings.copy(
        syncMethod = method.name,
        webdavUrl = webdavUrl.trim(),
        webdavUsername = webdavUser.trim(),
        webdavPassword = webdavPassword,
        s3Endpoint = s3Endpoint.trim(),
        s3Bucket = s3Bucket.trim(),
        s3Region = s3Region.trim(),
        s3AccessKey = s3Access.trim(),
        s3SecretKey = s3Secret,
        s3Prefix = s3Prefix.trim(),
    )

    fun runAction(block: suspend () -> Result<String>) {
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching { block() }.getOrElse { Result.failure(it) }
            result.onSuccess { snackbar.showSnackbar(it) }
                .onFailure { snackbar.showSnackbar(it.message ?: "操作失败") }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            "凭证只保存在本机。上传会把笔记、标签和附件放到远程的 wantnote/ 目录；下载按更新时间较新者覆盖，不会清空未出现在远程清单里的本地笔记。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        SectionTitle("同步方式")
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = method.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("默认同步方式") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SyncMethod.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            method = option
                            expanded = false
                            scope.launch { settingsRepo.update { it.copy(syncMethod = option.name) } }
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            SyncMethod.entries.forEach { option ->
                FilterChip(
                    selected = method == option,
                    onClick = {
                        method = option
                        scope.launch { settingsRepo.update { it.copy(syncMethod = option.name) } }
                    },
                    label = { Text(option.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (method == SyncMethod.WEBDAV) {
            SectionTitle("WebDAV")
            OutlinedTextField(value = webdavUrl, onValueChange = { webdavUrl = it }, label = { Text("服务器地址") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
            OutlinedTextField(value = webdavUser, onValueChange = { webdavUser = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
            OutlinedTextField(
                value = webdavPassword,
                onValueChange = { webdavPassword = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        } else {
            SectionTitle("S3")
            OutlinedTextField(value = s3Endpoint, onValueChange = { s3Endpoint = it }, label = { Text("Endpoint") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
            OutlinedTextField(value = s3Bucket, onValueChange = { s3Bucket = it }, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
            OutlinedTextField(value = s3Region, onValueChange = { s3Region = it }, label = { Text("Region") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
            OutlinedTextField(value = s3Access, onValueChange = { s3Access = it }, label = { Text("Access Key") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
            OutlinedTextField(
                value = s3Secret,
                onValueChange = { s3Secret = it },
                label = { Text("Secret Key") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(value = s3Prefix, onValueChange = { s3Prefix = it }, label = { Text("Prefix（可选）") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { runAction { sync.testAndSave(draft()) } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("测试并保存") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runAction {
                    settingsRepo.update { draft() }
                    sync.upload()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("上传") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { runAction { sync.download() } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("下载") }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
