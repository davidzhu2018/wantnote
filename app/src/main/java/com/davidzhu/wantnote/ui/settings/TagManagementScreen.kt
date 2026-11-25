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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.AccountTree
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.components.TagColorPicker
import kotlinx.coroutines.launch

private sealed class TagDialog {
    data object Create : TagDialog()
    data class Rename(val tag: Tag) : TagDialog()
    data class Child(val parent: Tag) : TagDialog()
    data class Delete(val tag: Tag) : TagDialog()
}

@Composable
fun TagManagementScreen(onBack: () -> Unit) {
    val notes = LocalNotes.current
    val tags by notes.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<TagDialog?>(null) }
    var draftPath by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf(Tag.defaultColor()) }

    val visible = remember(tags, query) {
        val q = query.trim()
        if (q.isEmpty()) tags else tags.filter { it.path.contains(q, ignoreCase = true) }
    }

    SettingsSubScaffold(title = "标签管理", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            WantTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = "搜索标签",
                placeholder = "路径或关键字",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("共 ${visible.size} 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WantTextButton(
                    onClick = {
                        draftPath = ""
                        draftColor = Tag.defaultColor()
                        dialog = TagDialog.Create
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("新建标签", modifier = Modifier.padding(start = 4.dp))
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { tag ->
                    val depth = tag.path.count { it == '/' }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tag.path, style = MaterialTheme.typography.bodyLarge)
                            TagColorPicker(
                                selected = tag.color,
                                onSelect = { color ->
                                    scope.launch { notes.setTagColor(tag.id, color) }
                                },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        IconButton(onClick = {
                            draftPath = tag.path.substringAfterLast('/')
                            draftColor = tag.color
                            dialog = TagDialog.Child(tag)
                        }) {
                            Icon(Icons.Outlined.AccountTree, contentDescription = "添加子标签")
                        }
                        IconButton(onClick = {
                            draftPath = tag.path
                            draftColor = tag.color
                            dialog = TagDialog.Rename(tag)
                        }) {
                            Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "重命名")
                        }
                        IconButton(onClick = { dialog = TagDialog.Delete(tag) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    val current = dialog
    if (current != null) {
        val title = when (current) {
            TagDialog.Create -> "新建标签"
            is TagDialog.Rename -> "重命名标签"
            is TagDialog.Child -> "添加子标签"
            is TagDialog.Delete -> "删除标签"
        }
        WantAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title) },
            text = {
                when (current) {
                    is TagDialog.Delete -> {
                        Text("确定删除「${current.tag.path}」吗？笔记不会被删除，只会去掉这条标签。若它有子标签路径，需要单独删除那些标签。")
                    }
                    is TagDialog.Child -> {
                        Column {
                            Text("父标签：${current.parent.path}", modifier = Modifier.padding(bottom = 8.dp))
                            WantTextField(
                                value = draftPath,
                                onValueChange = { draftPath = it },
                                label = "子标签名称",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TagColorPicker(selected = draftColor, onSelect = { draftColor = it })
                        }
                    }
                    else -> {
                        Column {
                            WantTextField(
                                value = draftPath,
                                onValueChange = { draftPath = it },
                                label = if (current is TagDialog.Rename) "路径（可用斜杠表示层级）" else "路径（例如 工作/项目）",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TagColorPicker(selected = draftColor, onSelect = { draftColor = it })
                        }
                    }
                }
            },
            confirmButton = {
                WantTextButton(
                    onClick = {
                        scope.launch {
                            try {
                                when (current) {
                                    TagDialog.Create -> {
                                        val created = notes.upsertTag(draftPath, draftColor)
                                        if (created == null) snackbar.showSnackbar("请填写有效的标签路径")
                                    }
                                    is TagDialog.Rename -> {
                                        notes.renameTag(current.tag.id, draftPath)
                                        notes.setTagColor(current.tag.id, draftColor)
                                    }
                                    is TagDialog.Child -> {
                                        val created = notes.addChildTag(current.parent.id, draftPath, draftColor)
                                        if (created == null) snackbar.showSnackbar("请填写子标签名称")
                                    }
                                    is TagDialog.Delete -> notes.deleteTag(current.tag.id)
                                }
                                dialog = null
                            } catch (error: NoteRepository.TagPathConflict) {
                                snackbar.showSnackbar(error.message ?: "标签路径冲突")
                            }
                        }
                    },
                ) {
                    Text(if (current is TagDialog.Delete) "删除" else "确定")
                }
            },
            dismissButton = {
                WantTextButton(onClick = { dialog = null }) { Text("取消") }
            },
        )
    }
}
