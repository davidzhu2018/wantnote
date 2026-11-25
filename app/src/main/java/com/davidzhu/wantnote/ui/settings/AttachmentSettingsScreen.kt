package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.davidzhu.wantnote.ui.components.WantSwitch
import com.davidzhu.wantnote.ui.components.WantTab
import com.davidzhu.wantnote.ui.components.WantTabRow
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

private val TabLabels = listOf("文件", "图片", "视频")
private val TabKinds = listOf(
    MediaStorage.AttachmentKind.FILE,
    MediaStorage.AttachmentKind.IMAGE,
    MediaStorage.AttachmentKind.VIDEO,
)

@Composable
fun AttachmentSettingsScreen(onBack: () -> Unit) {
    val settingsRepo = LocalSettings.current
    val notes = LocalNotes.current
    val media = LocalMedia.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf(listOf<MediaStorage.StoredAttachment>()) }
    var confirmClear by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        val blocks = notes.getAllMediaBlocks()
        val refs = mutableMapOf<String, MutableList<String>>()
        blocks.forEach { block ->
            val path = block.mediaPath
            if (path.isNotBlank()) {
                refs.getOrPut(path) { mutableListOf() }.add(block.noteId)
            }
        }
        val listed = media.listMediaFiles().map { file ->
            val relative = media.relativePathOf(file)
            val noteIds = refs[relative]?.distinct().orEmpty()
            MediaStorage.StoredAttachment(
                relativePath = relative,
                file = file,
                kind = MediaStorage.kindOf(relative),
                size = file.length(),
                referenced = noteIds.isNotEmpty(),
                noteIds = noteIds,
            )
        }
        // Also include referenced paths whose files may live outside media/ listing miss
        val extra = refs.keys.filter { path -> listed.none { it.relativePath == path } }.map { path ->
            val file = media.fileFor(path)
            MediaStorage.StoredAttachment(
                relativePath = path,
                file = file,
                kind = MediaStorage.kindOf(path),
                size = if (file.exists()) file.length() else 0L,
                referenced = true,
                noteIds = refs[path]?.distinct().orEmpty(),
            )
        }
        items = listed + extra
    }

    val kind = TabKinds[tab]
    val visible = items.filter { it.kind == kind }
    val unused = items.filter { !it.referenced }

    SettingsSubScaffold(title = "附件管理", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("图片压缩", style = MaterialTheme.typography.titleMedium)
                        Text("开启后，从相册插入的图片会缩小并转成 JPEG。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WantSwitch(
                        checked = settings.compressImages,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(compressImages = checked) } }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("视频压缩", style = MaterialTheme.typography.titleMedium)
                        Text("开关会保存在本机。本包导入视频时仍按原文件保存（完整转码未接入）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WantSwitch(
                        checked = settings.compressVideos,
                        onCheckedChange = { checked ->
                            scope.launch { settingsRepo.update { it.copy(compressVideos = checked) } }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                WantTextButton(
                    onClick = { confirmClear = true },
                    enabled = unused.isNotEmpty(),
                ) {
                    Text("清理未引用附件（${unused.size}）")
                }
            }
            WantTabRow(selectedTabIndex = tab) {
                TabLabels.forEachIndexed { index, label ->
                    WantTab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }
            if (visible.isEmpty()) {
                Text(
                    "这一类还没有附件。",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.relativePath }) { item ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(item.file.name.ifBlank { item.relativePath }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                buildString {
                                    append(formatSize(item.size))
                                    append(" · ")
                                    append(if (item.referenced) "已被 ${item.noteIds.size} 条笔记引用" else "未被任何笔记引用")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        WantAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清理未引用附件") },
            text = { Text("将删除 ${unused.size} 个没有任何笔记引用的文件，此操作不可撤销。") },
            confirmButton = {
                WantTextButton(
                    onClick = {
                        scope.launch {
                            var deleted = 0
                            unused.forEach { item ->
                                if (media.deleteRelative(item.relativePath)) deleted += 1
                            }
                            confirmClear = false
                            tick += 1
                            snackbar.showSnackbar("已清理 $deleted 个未引用附件")
                        }
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                WantTextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

private fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
