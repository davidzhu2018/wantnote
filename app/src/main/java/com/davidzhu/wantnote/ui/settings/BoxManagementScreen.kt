package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.ui.LocalNotes
import kotlinx.coroutines.launch

private sealed class BoxDialog {
    data object Create : BoxDialog()
    data class Rename(val box: NoteBox) : BoxDialog()
    data class Delete(val box: NoteBox) : BoxDialog()
}

/**
 * 设置 →「卡片盒管理」：卡片盒的增删查改。
 *
 * - 新建：输入名称点一次「确定」即关窗并提示（名称为空或重名保持弹窗并内联提示）。
 * - 重命名：铅笔按钮，成功后先关窗再提示。
 * - 删除：二次确认；盒内笔记全部自动移入默认「暂存箱」，内容、标签、
 *   链接都不受影响。「暂存箱」本身不可删除。
 */
@Composable
fun BoxManagementScreen(onBack: () -> Unit) {
    val notes = LocalNotes.current
    val boxes by notes.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<BoxDialog?>(null) }
    var draftName by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    // 笔记数随盒列表一起刷新（每次列表变化重算一次）。
    LaunchedEffect(boxes) {
        counts = notes.boxNoteCounts()
    }

    SettingsSubScaffold(title = "卡片盒管理", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "共 ${boxes.size} 个卡片盒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        draftName = ""
                        dialogError = null
                        dialog = BoxDialog.Create
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("新建卡片盒", modifier = Modifier.padding(start = 4.dp))
                }
            }
            Text(
                "删除卡片盒时，盒内笔记会自动移入「${NoteBox.DEFAULT_NAME}」，不会丢失内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(boxes, key = { it.id }) { box ->
                    val isDefault = box.id == NoteBox.DEFAULT_ID
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(box.name)
                                if (isDefault) {
                                    Text(
                                        "（默认）",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        },
                        supportingContent = { Text("${counts[box.id] ?: 0} 篇笔记") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    draftName = box.name
                                    dialogError = null
                                    dialog = BoxDialog.Rename(box)
                                }) {
                                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "重命名")
                                }
                                IconButton(
                                    onClick = {
                                        dialogError = null
                                        dialog = BoxDialog.Delete(box)
                                    },
                                    enabled = !isDefault,
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除")
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = false) { },
                    )
                }
            }
        }
    }

    val current = dialog
    if (current != null) {
        val title = when (current) {
            BoxDialog.Create -> "新建卡片盒"
            is BoxDialog.Rename -> "重命名卡片盒"
            is BoxDialog.Delete -> "删除卡片盒"
        }
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    dialog = null
                    dialogError = null
                }
            },
            title = { Text(title) },
            text = {
                when (current) {
                    is BoxDialog.Delete -> {
                        val count = counts[current.box.id] ?: 0
                        Text(
                            "确定删除「${current.box.name}」吗？盒内 $count 篇笔记会自动移入" +
                                "「${NoteBox.DEFAULT_NAME}」，笔记内容不会被删除。",
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = draftName,
                                onValueChange = {
                                    draftName = it
                                    if (dialogError != null) dialogError = null
                                },
                                label = { Text("卡片盒名称") },
                                singleLine = true,
                                isError = dialogError != null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            dialogError?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        if (submitting) return@TextButton
                        submitting = true
                        scope.launch {
                            try {
                                when (current) {
                                    BoxDialog.Create -> {
                                        val created = notes.createBox(draftName)
                                        if (created == null) {
                                            dialogError = if (draftName.isBlank()) {
                                                "请填写卡片盒名称"
                                            } else {
                                                "已存在同名卡片盒"
                                            }
                                        } else {
                                            // 0.11.0 N1：确认成功后先立即关窗，再展示 snackbar，避免挂起导致要再点一次。
                                            dialog = null
                                            dialogError = null
                                            submitting = false
                                            snackbar.showSnackbar("已创建「${created.name}」")
                                            return@launch
                                        }
                                    }
                                    is BoxDialog.Rename -> {
                                        try {
                                            val renamed = notes.renameBox(current.box.id, draftName)
                                            if (renamed == null) {
                                                dialogError = "请填写卡片盒名称"
                                            } else {
                                                dialog = null
                                                dialogError = null
                                                submitting = false
                                                snackbar.showSnackbar("已重命名为「${renamed.name}」")
                                                return@launch
                                            }
                                        } catch (error: NoteRepository.BoxNameConflict) {
                                            dialogError = error.message ?: "卡片盒名称已存在"
                                        }
                                    }
                                    is BoxDialog.Delete -> {
                                        val deleted = notes.deleteBox(current.box.id)
                                        dialog = null
                                        dialogError = null
                                        submitting = false
                                        snackbar.showSnackbar(
                                            if (deleted) {
                                                "已删除「${current.box.name}」，笔记已移入「${NoteBox.DEFAULT_NAME}」"
                                            } else {
                                                "默认卡片盒不能删除"
                                            },
                                        )
                                        return@launch
                                    }
                                }
                            } finally {
                                submitting = false
                            }
                        }
                    },
                ) {
                    Text(if (current is BoxDialog.Delete) "删除" else "确定")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        dialog = null
                        dialogError = null
                    },
                ) { Text("取消") }
            },
        )
    }
}
