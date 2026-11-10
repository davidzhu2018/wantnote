package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.BuildConfig
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.DayNightMode
import com.davidzhu.wantnote.data.FontScaleOption
import com.davidzhu.wantnote.data.ThemePalette
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var webdavUrl by remember { mutableStateOf(settings.webdavUrl) }
    var webdavUser by remember { mutableStateOf(settings.webdavUsername) }
    var webdavPassword by remember { mutableStateOf(settings.webdavPassword) }
    var s3Endpoint by remember { mutableStateOf(settings.s3Endpoint) }
    var s3Bucket by remember { mutableStateOf(settings.s3Bucket) }
    var s3Region by remember { mutableStateOf(settings.s3Region) }
    var s3Access by remember { mutableStateOf(settings.s3AccessKey) }
    var s3Secret by remember { mutableStateOf(settings.s3SecretKey) }
    var s3Prefix by remember { mutableStateOf(settings.s3Prefix) }

    LaunchedEffect(settings.id, settings.webdavUrl, settings.s3Endpoint, settings.s3Bucket) {
        // Refresh form if another source updates settings, without logging secret values.
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            Text("想记", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))

            SectionTitle("主题")
            ChipRow {
                ThemePalette.entries.forEach { palette ->
                    FilterChip(
                        selected = settings.palette == palette,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(themeName = palette.name) } }
                        },
                        label = { Text(palette.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("日夜")
            ChipRow {
                DayNightMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.dayNightMode == mode,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(dayNight = mode.name) } }
                        },
                        label = { Text(mode.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("字体大小")
            ChipRow {
                FontScaleOption.entries.forEach { option ->
                    FilterChip(
                        selected = settings.fontScaleOption == option,
                        onClick = {
                            scope.launch { settingsRepo.update { it.copy(fontScale = option.name) } }
                        },
                        label = { Text(option.label) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("插入图片时压缩", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "开启后，从相册插入的图片会缩小并转成 JPEG 保存在应用私有目录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.compressImages,
                    onCheckedChange = { checked ->
                        scope.launch { settingsRepo.update { it.copy(compressImages = checked) } }
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle("云同步入口")
            Text(
                text = "本包只保存配置，不会实际上传或下载。未配置或不完整也不会影响离线读写本地笔记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Text("WebDAV", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = webdavUrl,
                onValueChange = { webdavUrl = it },
                label = { Text("服务器地址") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = webdavUser,
                onValueChange = { webdavUser = it },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = webdavPassword,
                onValueChange = { webdavPassword = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Button(
                onClick = {
                    scope.launch {
                        settingsRepo.update {
                            it.copy(
                                webdavUrl = webdavUrl.trim(),
                                webdavUsername = webdavUser.trim(),
                                webdavPassword = webdavPassword,
                            )
                        }
                        snackbar.showSnackbar("WebDAV 配置已保存在本机，不会上传。")
                    }
                },
            ) {
                Text("保存 WebDAV 配置")
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("S3", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = s3Endpoint,
                onValueChange = { s3Endpoint = it },
                label = { Text("Endpoint") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = s3Bucket,
                onValueChange = { s3Bucket = it },
                label = { Text("Bucket") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = s3Region,
                onValueChange = { s3Region = it },
                label = { Text("Region") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = s3Access,
                onValueChange = { s3Access = it },
                label = { Text("Access Key") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = s3Secret,
                onValueChange = { s3Secret = it },
                label = { Text("Secret Key") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(
                value = s3Prefix,
                onValueChange = { s3Prefix = it },
                label = { Text("Prefix（可选）") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
            )
            Button(
                onClick = {
                    scope.launch {
                        settingsRepo.update {
                            it.copy(
                                s3Endpoint = s3Endpoint.trim(),
                                s3Bucket = s3Bucket.trim(),
                                s3Region = s3Region.trim(),
                                s3AccessKey = s3Access.trim(),
                                s3SecretKey = s3Secret,
                                s3Prefix = s3Prefix.trim(),
                            )
                        }
                        snackbar.showSnackbar("S3 配置已保存在本机，不会上传。")
                    }
                },
            ) {
                Text("保存 S3 配置")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
