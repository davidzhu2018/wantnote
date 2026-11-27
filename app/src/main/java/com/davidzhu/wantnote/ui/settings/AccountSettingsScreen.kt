package com.davidzhu.wantnote.ui.settings

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text

/**
 * 0.17.2：账号管理页面，支持设置用户名与本地头像，供主页朋友圈视图与后续账户体系使用。
 */
@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val media = LocalMedia.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var nickname by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    // 0.17.3：从数据库直接读初始值并监听后续变更，避免首帧 initialValue = DEFAULT("")
    // 抢先置位导致已保存用户名在重新进入页面时显示为空。
    LaunchedEffect(Unit) {
        val stored = runCatching { settingsRepo.get().profileNickname }.getOrNull().orEmpty()
        if (!dirty) {
            nickname = stored
        }
    }
    LaunchedEffect(settings.profileNickname) {
        if (!dirty && settings.profileNickname.isNotEmpty()) {
            nickname = settings.profileNickname
        }
    }

    val avatarBitmap by produceState<Bitmap?>(initialValue = null, settings.profileAvatarPath) {
        value = if (settings.profileAvatarPath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { media.loadPreview(settings.profileAvatarPath, maxSide = 512) }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { media.importImage(uri, compress = true) }
            }.onSuccess { path ->
                settingsRepo.update { it.copy(profileAvatarPath = path) }
                snackbar.showSnackbar("头像已更新")
            }.onFailure {
                snackbar.showSnackbar("无法导入头像图片")
            }
        }
    }

    SettingsSubScaffold(title = "账号管理", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = avatarBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "用户头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "默认头像",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WantButton(
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) {
                    Text("更换头像")
                }
                if (settings.profileAvatarPath.isNotBlank()) {
                    WantTextButton(
                        onClick = {
                            scope.launch {
                                settingsRepo.update { it.copy(profileAvatarPath = "") }
                                snackbar.showSnackbar("已恢复默认头像")
                            }
                        },
                    ) {
                        Text("恢复默认")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "用户名",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                WantTextField(
                    value = nickname,
                    onValueChange = {
                        nickname = it
                        dirty = true
                    },
                    label = "用户名",
                    placeholder = "例如：想记用户",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                WantButton(
                    onClick = {
                        val trimmed = nickname.trim()
                        scope.launch {
                            settingsRepo.update { it.copy(profileNickname = trimmed) }
                            nickname = trimmed
                            dirty = false
                            snackbar.showSnackbar("用户名已保存")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存用户名")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "用户名和头像仅保存在本机，并会随本地备份与云同步一起保存，用于主页「朋友圈」视图展示及后续账户体系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
