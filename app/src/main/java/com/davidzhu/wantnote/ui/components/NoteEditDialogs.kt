package com.davidzhu.wantnote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.davidzhu.wantnote.data.BoxCreateResult
import com.davidzhu.wantnote.data.BoxPickerQuery
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.Tag
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 0.13.0 详情页阅读态与编辑器**共用**的卡片盒 / 标签弹窗。
 *
 * 阅读态（[com.davidzhu.wantnote.ui.editor.NoteDetailScreen]）与编辑态
 * （[com.davidzhu.wantnote.ui.editor.EditorScreen]）都从这里取弹窗，搜索、
 * 「无匹配可新建」、标签增删的行为因此不会两处漂移。
 */

/**
 * 改卡片盒弹窗：顶部关键字搜索（本地过滤，忽略大小写），长列表可滚动；
 * 点选立即赋值并关闭；关键字没有匹配时给出「新建卡片盒」入口，创建成功后
 * 由 [onCreate] 负责把新盒赋给当前笔记。
 *
 * @param onPick 选中已有卡片盒。
 * @param onCreate 新建并赋给当前笔记；返回 [BoxCreateResult] 供 UI 提示重名/空名。
 */
@Composable
fun BoxPickerDialog(
    boxes: List<NoteBox>,
    currentBoxId: String?,
    onPick: (NoteBox) -> Unit,
    onCreate: suspend (String) -> BoxCreateResult,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val filtered = remember(boxes, query) { BoxPickerQuery.filter(boxes, query) }
    val canCreate = remember(boxes, query) { BoxPickerQuery.canCreate(boxes, query) }
    val draftName = BoxPickerQuery.createName(query)

    WantAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("改卡片盒") },
        text = {
            Column {
                WantTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        error = null
                        creating = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "搜索卡片盒",
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEach { box ->
                        BoxRow(
                            name = box.name,
                            selected = box.id == currentBoxId,
                            onClick = {
                                if (!busy) {
                                    busy = true
                                    onPick(box)
                                }
                            },
                        )
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = if (boxes.isEmpty()) {
                                "还没有卡片盒，可以直接新建。"
                            } else {
                                "没有匹配「${query.trim()}」的卡片盒。"
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    if (canCreate && draftName != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    creating = true
                                    error = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "新建卡片盒「$draftName」",
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error!!,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                if (creating && draftName != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "新建卡片盒：$draftName",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    WantButton(
                        onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    when (val result = onCreate(draftName)) {
                                        is BoxCreateResult.Created -> onDismiss()
                                        BoxCreateResult.DuplicateName -> {
                                            error = "卡片盒「$draftName」已存在"
                                            busy = false
                                        }
                                        BoxCreateResult.EmptyName -> {
                                            error = "请填写卡片盒名称"
                                            busy = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) {
                        Text("新建并归入此盒")
                    }
                }
            }
        },
        confirmButton = {
            WantTextButton(onClick = { if (!busy) onDismiss() }) { Text("取消") }
        },
    )
}

@Composable
private fun BoxRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "当前卡片盒",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 加标签弹窗：搜索已有标签、点选附加到当前笔记；关键字可以当场新建标签
 * （带颜色，走 `addTagToNote`，不绕过颜色 / 路径规范化）。
 */
@Composable
fun TagAddDialog(
    allTags: List<Tag>,
    selectedIds: Set<String>,
    onToggle: (Tag) -> Unit,
    onCreate: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf(Tag.defaultColor()) }
    val newPath = Tag.normalizePath(query)
    val exists = allTags.any { it.path == newPath }
    val canCreate = newPath.isNotEmpty() && !exists
    val filtered = remember(allTags, query) {
        if (query.isBlank()) allTags else allTags.filter { it.path.contains(query.trim(), ignoreCase = true) }
    }

    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择标签") },
        text = {
            Column {
                WantTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "搜索或输入新标签",
                    placeholder = "例如 工作/项目",
                    singleLine = true,
                )
                if (canCreate) {
                    Text(
                        text = "新标签：$newPath",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TagColorPicker(
                        selected = draftColor,
                        onSelect = { draftColor = it },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    WantTextButton(
                        onClick = {
                            onCreate(newPath, draftColor)
                            query = ""
                            draftColor = Tag.defaultColor()
                        },
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text("创建并附加")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        allTags.isEmpty() -> Text(
                            "还没有标签。在上方输入名称即可创建并附加到本笔记。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )

                        filtered.isEmpty() -> Text(
                            "没有匹配「${query.trim()}」的标签，可以新建。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )

                        else -> TagChipRow(
                            tags = filtered,
                            max = Int.MAX_VALUE,
                            selectedIds = selectedIds,
                            onClick = onToggle,
                        )
                    }
                }
            }
        },
        confirmButton = {
            WantTextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

/**
 * 编辑本笔记标签：单击阅读态里的标签打开，可勾选一个或多个标签从**本笔记**
 * 移除（[com.davidzhu.wantnote.data.NoteTagActions]）。多删会二次确认。
 * 这里不会删除标签本身（那是 设置 → 标签管理 的 `deleteTag`）。
 */
@Composable
fun NoteTagRemoveDialog(
    tags: List<Tag>,
    onConfirmRemove: (List<Tag>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(tags) { mutableStateOf(setOf<String>()) }
    var pendingConfirm by remember { mutableStateOf(false) }

    if (pendingConfirm) {
        val count = selected.value.size
        WantAlertDialog(
            onDismissRequest = { pendingConfirm = false },
            title = { Text("移除标签") },
            text = {
                Text(
                    "确认从本笔记移除 $count 个标签？标签本身仍保留在标签管理里。",
                    style = MiuixTheme.textStyles.body1,
                )
            },
            confirmButton = {
                WantButton(onClick = {
                    pendingConfirm = false
                    onConfirmRemove(tags.filter { it.id in selected.value })
                }) { Text("移除") }
            },
            dismissButton = {
                WantTextButton(onClick = { pendingConfirm = false }) { Text("取消") }
            },
        )
        return
    }

    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本笔记的标签") },
        text = {
            Column {
                Text(
                    text = "点选要移除的标签（可多选）。只从本笔记移除，不会删除标签本身。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (tags.isEmpty()) {
                    Text(
                        "本笔记还没有标签，用标签行右侧的 + 添加。",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        tags.forEach { tag ->
                            val checked = tag.id in selected.value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected.value = if (checked) {
                                            selected.value - tag.id
                                        } else {
                                            selected.value + tag.id
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TagChip(tag = tag, selected = !checked)
                                Spacer(modifier = Modifier.weight(1f))
                                if (checked) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = "已选中",
                                        tint = MiuixTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            WantButton(
                onClick = {
                    val picked = tags.filter { it.id in selected.value }
                    when {
                        picked.isEmpty() -> onDismiss()
                        picked.size == 1 -> onConfirmRemove(picked)
                        else -> pendingConfirm = true
                    }
                },
                enabled = selected.value.isNotEmpty(),
            ) {
                Text(if (selected.value.size > 1) "移除 ${selected.value.size} 个" else "移除")
            }
        },
        dismissButton = {
            WantTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 标签行右侧的「+」：无标签时也要显示，用于给本笔记加标签。 */
@Composable
fun TagAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "添加标签",
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}
