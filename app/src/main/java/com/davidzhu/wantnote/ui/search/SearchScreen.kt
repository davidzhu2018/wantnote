package com.davidzhu.wantnote.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tag
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantCheckbox
import com.davidzhu.wantnote.ui.components.WantFilterChip
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantScaffold
import top.yukonga.miuix.kmp.basic.Text
import com.davidzhu.wantnote.ui.components.WantTextField
import com.davidzhu.wantnote.ui.components.WantTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.tagColor
import com.davidzhu.wantnote.ui.home.NoteCollection
import java.util.Calendar
import com.davidzhu.wantnote.ui.components.WantDatePickerDialog

private enum class DatePreset(val label: String) {
    ANY("不限日期"),
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
    CUSTOM("自定义"),
}

@Composable
fun SearchScreen(
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val items by repository.observeAllWithDetails().collectAsStateWithLifecycle(initialValue = emptyList())
    val allTags by repository.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var selectedTagIds by remember { mutableStateOf(setOf<String>()) }
    var showTagDialog by remember { mutableStateOf(false) }
    var datePreset by rememberSaveable { mutableStateOf(DatePreset.ANY.name) }
    var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }

    val preset = DatePreset.entries.firstOrNull { it.name == datePreset } ?: DatePreset.ANY
    val now = remember { System.currentTimeMillis() }
    val (startMs, endMs) = remember(preset, customStart, customEnd, now) {
        dateRange(preset, customStart, customEnd, now)
    }

    val filtered = remember(items, query, selectedTagIds, startMs, endMs) {
        NoteRepository.filterNotes(
            items = items,
            keyword = query,
            tagIds = selectedTagIds,
            startMs = startMs,
            endMs = endMs,
        )
    }

    val hasFilter = query.isNotBlank() || selectedTagIds.isNotEmpty() || preset != DatePreset.ANY

    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            WantTopBar(
                title = "搜索",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WantTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "按关键字搜标题、正文或标签",
                singleLine = true,
            )

            // 0.11.0 N18-N19：不再平铺全部标签，改为「标签」按钮弹出多选对话框。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WantTextButton(onClick = { showTagDialog = true }) {
                    Icon(
                        Icons.Outlined.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (selectedTagIds.isEmpty()) {
                            "标签"
                        } else {
                            "标签（已选 ${selectedTagIds.size}）"
                        },
                    )
                }
                if (selectedTagIds.isNotEmpty()) {
                    WantTextButton(onClick = { selectedTagIds = emptySet() }) {
                        Text("清空")
                    }
                }
            }

            Text(
                "日期",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DatePreset.entries.forEach { option ->
                    WantFilterChip(
                        selected = preset == option,
                        onClick = { datePreset = option.name },
                        label = { Text(option.label) },
                    )
                }
            }
            if (preset == DatePreset.CUSTOM) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WantTextButton(onClick = { picking = "start" }) {
                        Text(customStart?.let { "从 ${Dates.dayKey(it)}" } ?: "起始日期")
                    }
                    WantTextButton(onClick = { picking = "end" }) {
                        Text(customEnd?.let { "到 ${Dates.dayKey(it)}" } ?: "结束日期")
                    }
                }
            }

            val emptyText = when {
                !hasFilter -> "输入关键字，或选择标签、日期，筛选结果会按当前主页视图展示。"
                filtered.isEmpty() -> "没有找到符合当前条件的笔记。"
                else -> null
            }
            NoteCollection(
                items = filtered,
                viewMode = settings.homeViewMode,
                onOpenNote = onOpenNote,
                emptyText = emptyText,
            )
        }
    }

    if (showTagDialog) {
        SearchTagPickerDialog(
            allTags = allTags,
            initialSelectedIds = selectedTagIds,
            onConfirm = { chosen ->
                selectedTagIds = chosen
                showTagDialog = false
            },
            onDismiss = { showTagDialog = false },
        )
    }

    val currentPicking = picking
    if (currentPicking != null) {
        val initial = if (currentPicking == "start") customStart else customEnd
        WantDatePickerDialog(
            initialMillis = initial,
            onDismiss = { picking = null },
            onConfirm = { millis ->
                // WantDatePickerDialog 直接返回本地零点毫秒。
                if (currentPicking == "start") customStart = millis else customEnd = Dates.endOfDay(millis)
                picking = null
            },
        )
    }
}

/**
 * 0.11.0 N18-N19：搜索页标签多选弹窗。
 * 列出全部标签，支持单选/多选及清空；确认后按 OR 规则（包含任意所选标签即匹配）筛选笔记。
 */
@Composable
private fun SearchTagPickerDialog(
    allTags: List<Tag>,
    initialSelectedIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftIds by remember(initialSelectedIds) { mutableStateOf(initialSelectedIds) }

    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("选择标签")
                if (draftIds.isNotEmpty()) {
                    WantTextButton(onClick = { draftIds = emptySet() }) {
                        Text("清空选择")
                    }
                }
            }
        },
        text = {
            if (allTags.isEmpty()) {
                Text(
                    "还没有标签。编辑笔记时可添加多级标签，例如 工作/项目。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "可单选或多选；筛选包含所选任意标签的笔记。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                    ) {
                        items(allTags, key = { it.id }) { tag ->
                            val checked = tag.id in draftIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        draftIds = if (checked) draftIds - tag.id else draftIds + tag.id
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WantCheckbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        draftIds = if (on) draftIds + tag.id else draftIds - tag.id
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tagColor(tag.color)),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tag.path,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            WantTextButton(onClick = { onConfirm(draftIds) }) { Text("确定") }
        },
        dismissButton = {
            WantTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun dateRange(
    preset: DatePreset,
    customStart: Long?,
    customEnd: Long?,
    now: Long,
): Pair<Long?, Long?> {
    return when (preset) {
        DatePreset.ANY -> null to null
        DatePreset.TODAY -> Dates.startOfDay(now) to Dates.endOfDay(now)
        DatePreset.WEEK -> Dates.startOfWeek(now) to Dates.endOfDay(now)
        DatePreset.MONTH -> {
            val cal = Dates.nowCalendar().apply { timeInMillis = now }
            val start = Dates.startOfDay(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 1)
            start to Dates.endOfDay(now)
        }
        DatePreset.CUSTOM -> {
            val start = customStart
            val end = customEnd
            when {
                start == null && end == null -> null to null
                start == null -> null to end
                end == null -> start to Long.MAX_VALUE
                start <= end -> start to end
                else -> end to start
            }
        }
    }
}
