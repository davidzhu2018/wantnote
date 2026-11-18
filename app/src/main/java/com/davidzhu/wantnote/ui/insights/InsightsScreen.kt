package com.davidzhu.wantnote.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.LocalInsights
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteWithBlocks
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onOpenNote: (String) -> Unit, onOpenAiSettings: () -> Unit = {}) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val ai = LocalAi.current
    val items by repository.observeAllWithDetails().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLinks by repository.observeAllLinks().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val allTags by repository.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("洞察") },
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
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("回顾") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("总结") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("图谱") })
            }
            when (tabIndex) {
                0 -> ReviewTab(
                    items = items,
                    allTags = allTags,
                    onOpenNote = onOpenNote,
                    modifier = Modifier.weight(1f),
                )
                2 -> GraphTab(
                    items = items,
                    links = allLinks,
                    onOpenNote = onOpenNote,
                    modifier = Modifier.weight(1f),
                )
                else -> SummaryTab(
                    items = items,
                    allTags = allTags,
                    settings = settings,
                    settingsRepo = settingsRepo,
                    ai = ai,
                    scope = scope,
                    snackbar = snackbar,
                    onOpenAiSettings = onOpenAiSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class ReviewRange(val label: String) {
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    LAST_7("近7天"),
    LAST_30("近30天"),
    ALL("全部"),
    CUSTOM("自定义"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewTab(
    items: List<NoteWithDetails>,
    allTags: List<Tag>,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(ReviewRange.THIS_WEEK) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var selectedTagIds by remember { mutableStateOf(emptySet<String>()) }
    var pickingDate by remember { mutableStateOf<String?>(null) }

    val (startMs, endMs) = when (range) {
        ReviewRange.THIS_WEEK -> Dates.startOfWeek() to null
        ReviewRange.THIS_MONTH -> {
            val cal = Dates.nowCalendar()
            Dates.startOfDay(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 1) to null
        }
        ReviewRange.LAST_7 -> (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000) to null
        ReviewRange.LAST_30 -> (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) to null
        ReviewRange.ALL -> null to null
        ReviewRange.CUSTOM -> (
            customStart ?: (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
            ) to customEnd
    }

    val reviewNotes = remember(items, selectedTagIds, startMs, endMs) {
        NoteRepository.filterNotes(items, tagIds = selectedTagIds, startMs = startMs, endMs = endMs)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("日期范围", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReviewRange.entries.forEach { candidate ->
                FilterChip(
                    selected = range == candidate,
                    onClick = { range = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }
        if (range == ReviewRange.CUSTOM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { pickingDate = "start" }) {
                    Text(
                        customStart?.let { "起：${Dates.formatDayShort(it)}" } ?: "选择起始日",
                    )
                }
                TextButton(onClick = { pickingDate = "end" }) {
                    Text(
                        customEnd?.let { "止：${Dates.formatDayShort(it)}" } ?: "截止至今",
                    )
                }
                if (customStart != null || customEnd != null) {
                    TextButton(onClick = {
                        customStart = null
                        customEnd = null
                    }) { Text("重置") }
                }
            }
        }

        Text("标签", style = MaterialTheme.typography.titleSmall)
        if (allTags.isEmpty()) {
            Text(
                "还没有标签，可在设置 → 标签管理里创建。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            selectedTagIds = if (tag.id in selectedTagIds) {
                                selectedTagIds - tag.id
                            } else {
                                selectedTagIds + tag.id
                            }
                        },
                        label = { Text(tag.path) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "共 ${reviewNotes.size} 条符合条件的笔记，全部来自本机内容，可离线查看。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (reviewNotes.isEmpty()) {
            Text(
                "这个范围里还没有可回顾的笔记。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        } else {
            reviewNotes.forEach { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpenNote(item.note.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = SearchText.displayTitle(item.note),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Dates.formatTimestamp(item.note.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val preview = SearchText.preview(item.note).ifBlank {
                            SearchText.firstMeaningfulLine(item.note.body)
                        }
                        if (preview.isNotBlank()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    val picking = pickingDate
    if (picking != null) {
        val initial = (if (picking == "start") customStart else customEnd)
            ?: System.currentTimeMillis()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { sel ->
                        if (picking == "start") {
                            customStart = Dates.startOfDay(sel)
                        } else {
                            customEnd = Dates.endOfDay(sel)
                        }
                    }
                    pickingDate = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = null }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private enum class SummaryScope(val label: String) {
    DAILY("按日"),
    WEEKLY("按周"),
    MONTHLY("按月"),
    TAG("按标签"),
    TOPIC("按主题"),
}

@Composable
private fun SummaryTab(
    items: List<NoteWithDetails>,
    allTags: List<Tag>,
    settings: AppSettings,
    settingsRepo: SettingsRepository,
    ai: AiService,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var summaryScope by remember { mutableStateOf(SummaryScope.WEEKLY) }
    var scopeTagId by remember { mutableStateOf<String?>(null) }
    var topic by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    val todayStart = Dates.startOfDay(System.currentTimeMillis())
    val weekStart = Dates.startOfWeek()
    val monthStart = remember {
        val cal = Dates.nowCalendar()
        Dates.startOfDay(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 1)
    }

    val scopedItems = remember(items, summaryScope, scopeTagId, topic, todayStart, weekStart, monthStart) {
        when (summaryScope) {
            SummaryScope.DAILY -> NoteRepository.filterNotes(items, startMs = todayStart)
            SummaryScope.WEEKLY -> NoteRepository.filterNotes(items, startMs = weekStart)
            SummaryScope.MONTHLY -> NoteRepository.filterNotes(items, startMs = monthStart)
            SummaryScope.TAG -> {
                val tagId = scopeTagId
                if (tagId == null) {
                    emptyList()
                } else {
                    NoteRepository.filterNotes(items, tagIds = setOf(tagId))
                }
            }
            SummaryScope.TOPIC -> {
                val q = topic.trim()
                if (q.isEmpty()) {
                    emptyList()
                } else {
                    NoteRepository.filterNotes(items, keyword = q)
                }
            }
        }
    }

    val scopeLabel = when (summaryScope) {
        SummaryScope.DAILY -> "按日 · 今日"
        SummaryScope.WEEKLY -> "按周 · 本周"
        SummaryScope.MONTHLY -> "按月 · 本月"
        SummaryScope.TAG -> "按标签：" + (allTags.firstOrNull { it.id == scopeTagId }?.path ?: "未选择")
        SummaryScope.TOPIC -> "按主题：" + topic.trim().ifBlank { "未填写" }
    }

    val summary = remember(scopedItems) {
        LocalInsights.summarize(scopedItems.map { NoteWithBlocks(it.note, it.blocks) })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("范围", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryScope.entries.forEach { candidate ->
                FilterChip(
                    selected = summaryScope == candidate,
                    onClick = { summaryScope = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }
        when (summaryScope) {
            SummaryScope.TAG -> {
                if (allTags.isEmpty()) {
                    Text(
                        "还没有标签，可在设置 → 标签管理里创建。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = scopeTagId == tag.id,
                                onClick = { scopeTagId = if (scopeTagId == tag.id) null else tag.id },
                                label = { Text(tag.path) },
                            )
                        }
                    }
                }
            }
            SummaryScope.TOPIC -> {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("主题关键字") },
                    placeholder = { Text("例如 复习计划，会匹配标题、正文和标签") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            else -> Unit
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "当前范围：$scopeLabel，共 ${scopedItems.size} 条笔记参与统计与提炼。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    if (settings.aiSummaryProviderId.isBlank() || settings.aiSummaryModelId.isBlank()) {
                        onOpenAiSettings()
                        return@Button
                    }
                    if (running) return@Button
                    running = true
                    scope.launch {
                        val corpus = scopedItems.take(40).joinToString("\n\n") { item ->
                            val title = SearchText.displayTitle(item.note)
                            val body = item.note.body.take(800)
                            "《$title》\n$body"
                        }
                        val result = ai.generateSummary(corpus)
                        result.onSuccess { text ->
                            settingsRepo.update {
                                it.copy(
                                    aiSummaryText = text,
                                    aiSummaryUpdatedAt = System.currentTimeMillis(),
                                    aiSummaryScope = scopeLabel,
                                )
                            }
                        }.onFailure { error ->
                            snackbar.showSnackbar(error.message ?: "提炼失败，已保留本机统计")
                        }
                        running = false
                    }
                },
                enabled = !running && scopedItems.isNotEmpty(),
            ) {
                Text(
                    when {
                        running -> "正在提炼…"
                        settings.aiSummaryModelId.isBlank() -> "去配置 AI"
                        else -> "用 AI 提炼"
                    },
                )
            }
            if (settings.aiSummaryText.isNotBlank()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsRepo.update {
                                it.copy(aiSummaryText = "", aiSummaryUpdatedAt = 0L, aiSummaryScope = "")
                            }
                        }
                    },
                ) { Text("清除 AI 总结") }
            }
        }
        if (settings.aiSummaryText.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI 提炼", style = MaterialTheme.typography.titleSmall)
                    if (settings.aiSummaryScope.isNotBlank()) {
                        Text(
                            settings.aiSummaryScope,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (settings.aiSummaryUpdatedAt > 0L) {
                        Text(
                            Dates.formatTimestamp(settings.aiSummaryUpdatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = settings.aiSummaryText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = summary.narrative,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("笔记", summary.totalNotes.toString(), Modifier.weight(1f))
            StatCard("正文字数", summary.totalChars.toString(), Modifier.weight(1f))
            StatCard("待办未完成", summary.openTasks.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard("待办已完成", summary.doneTasks.toString(), Modifier.weight(1f))
            StatCard("高亮摘录", summary.highlights.size.toString(), Modifier.weight(1f))
            StatCard("活跃日期", summary.busiestDays.size.toString(), Modifier.weight(1f))
        }

        if (summary.highlights.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("高亮摘录", style = MaterialTheme.typography.titleMedium)
            summary.highlights.forEach { highlight ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
                ) {
                    Text(highlight, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (summary.busiestDays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("活跃日期", style = MaterialTheme.typography.titleMedium)
            summary.busiestDays.forEach { (day, count) ->
                Text(
                    text = "$day · $count 条",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
