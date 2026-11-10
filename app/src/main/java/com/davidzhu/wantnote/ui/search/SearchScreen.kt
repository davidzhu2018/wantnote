package com.davidzhu.wantnote.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.TagChip
import com.davidzhu.wantnote.ui.home.NoteCollection
import java.util.Calendar
import java.util.TimeZone

private enum class DatePreset(val label: String) {
    ANY("不限日期"),
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
    CUSTOM("自定义"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("按关键字搜标题、正文或标签") },
                singleLine = true,
                colors = TextFieldDefaults.colors(),
            )

            Text(
                "标签",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (allTags.isEmpty()) {
                Text(
                    "还没有标签。编辑笔记时可添加多级标签，例如 工作/项目。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allTags.forEach { tag ->
                        val selected = tag.id in selectedTagIds
                        TagChip(
                            tag = tag,
                            selected = selected,
                            onClick = {
                                selectedTagIds = if (selected) selectedTagIds - tag.id else selectedTagIds + tag.id
                            },
                        )
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
                    FilterChip(
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
                    TextButton(onClick = { picking = "start" }) {
                        Text(customStart?.let { "从 ${Dates.dayKey(it)}" } ?: "起始日期")
                    }
                    TextButton(onClick = { picking = "end" }) {
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

    val currentPicking = picking
    if (currentPicking != null) {
        val initial = if (currentPicking == "start") customStart else customEnd
        DatePickerDialogSheet(
            initial = initial,
            onDismiss = { picking = null },
            onConfirm = { millis ->
                // DatePicker uses UTC midnight; convert to local day start.
                val local = utcMillisToLocalDayStart(millis)
                if (currentPicking == "start") customStart = local else customEnd = Dates.endOfDay(local)
                picking = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogSheet(
    initial: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val value = state.selectedDateMillis
                    if (value != null) onConfirm(value) else onDismiss()
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state)
    }
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
            val start = Dates.startOfDay(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, 1)
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

private fun utcMillisToLocalDayStart(utcMidnight: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Dates.startOfDay(
        utc.get(Calendar.YEAR),
        utc.get(Calendar.MONTH) + 1,
        utc.get(Calendar.DAY_OF_MONTH),
    )
}
