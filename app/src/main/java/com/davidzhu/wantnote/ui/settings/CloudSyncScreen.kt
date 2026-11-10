package com.davidzhu.wantnote.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.SyncMethod
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.LocalSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val sync = LocalSync.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
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

    SettingsSubScaffold(title = "云同步", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
}
