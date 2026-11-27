package com.davidzhu.wantnote.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.ai.AiService
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.LocalInsights
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteWithBlocks
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.MarkdownText
import com.davidzhu.wantnote.ui.components.TagChipRow
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantDatePickerDialog
import com.davidzhu.wantnote.ui.components.WantFilterChip
import com.davidzhu.wantnote.ui.components.WantLinearProgressIndicator
import com.davidzhu.wantnote.ui.components.WantScaffold
import com.davidzhu.wantnote.ui.components.WantSurface
import com.davidzhu.wantnote.ui.components.WantTab
import com.davidzhu.wantnote.ui.components.WantTabRow
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTextField
import com.davidzhu.wantnote.ui.components.WantTopBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import java.util.Calendar
import kotlin.random.Random

/**
 * 0.20.0：随机挑选一轮回顾笔记（默认 20 条），优先从未回顾（reviewed == false）的笔记中随机抽取，
 * 不足时再从已回顾笔记中随机补齐。
 */
internal fun pickReviewBatch(
    pool: List<NoteWithDetails>,
    batchSize: Int = 20,
    seed: Long = System.currentTimeMillis(),
): List<NoteWithDetails> {
    if (pool.isEmpty() || batchSize <= 0) return emptyList()
    val rng = Random(seed)
    val unreviewed = pool.filter { !it.note.reviewed }.shuffled(rng)
    val reviewed = pool.filter { it.note.reviewed }.shuffled(rng)
    return (unreviewed + reviewed).take(batchSize)
}

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
    var tabIndex by remember { mutableIntStateOf(0) }

    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WantTopBar(title = "洞察")
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WantTabRow(selectedTabIndex = tabIndex) {
                WantTab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("回顾") })
                WantTab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("总结") })
                WantTab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("图谱") })
            }
            when (tabIndex) {
                0 -> ReviewTab(
                    items = items,
                    allTags = allTags,
                    repository = repository,
                    scope = scope,
                    snackbar = snackbar,
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
                    repository = repository,
                    ai = ai,
                    scope = scope,
                    snackbar = snackbar,
                    onOpenAiSettings = onOpenAiSettings,
                    onOpenNote = onOpenNote,
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

@Composable
private fun ReviewTab(
    items: List<NoteWithDetails>,
    allTags: List<Tag>,
    repository: NoteRepository,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(ReviewRange.ALL) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var selectedTagIds by remember { mutableStateOf(emptySet<String>()) }
    var pickingDate by remember { mutableStateOf<String?>(null) }
    var roundNumber by remember { mutableIntStateOf(1) }
    var roundSeed by remember { mutableStateOf(System.currentTimeMillis()) }
    var roundMarkedReviewed by remember { mutableStateOf(false) }

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

    val pool = remember(items, selectedTagIds, startMs, endMs) {
        NoteRepository.filterNotes(items, tagIds = selectedTagIds, startMs = startMs, endMs = endMs)
    }
    val linkTitles = remember(items) { items.associate { it.note.id to SearchText.displayTitle(it.note) } }

    // 锁定本轮抽出的笔记 ID 列表，避免标记「已回顾」后触发 Flow 刷新导致当前卡片顺序跳动
    val batchIds = remember(range, customStart, customEnd, selectedTagIds, roundSeed, pool.isEmpty()) {
        roundMarkedReviewed = false
        pickReviewBatch(pool, batchSize = 20, seed = roundSeed).map { it.note.id }
    }
    val itemById = remember(items) { items.associateBy { it.note.id } }
    val batch = remember(batchIds, itemById) {
        batchIds.mapNotNull { itemById[it] }
    }
    val unreviewedInPool = remember(pool) { pool.count { !it.note.reviewed } }

    fun markCurrentRoundReviewed(showToast: Boolean) {
        if (batchIds.isEmpty() || roundMarkedReviewed) return
        roundMarkedReviewed = true
        val ids = batchIds.toList()
        scope.launch {
            repository.markReviewedBatch(ids)
            if (showToast) {
                snackbar.showSnackbar("已完成本轮 ${ids.size} 篇笔记回顾，已标记为「已回顾」")
            }
        }
    }

    fun startNextBatch() {
        val prevIds = batchIds.toList()
        scope.launch {
            if (prevIds.isNotEmpty() && !roundMarkedReviewed) {
                repository.markReviewedBatch(prevIds)
            }
            roundMarkedReviewed = false
            roundNumber += 1
            roundSeed = System.nanoTime()
            snackbar.showSnackbar("已抽取第 $roundNumber 批回顾（优先未回顾笔记）")
        }
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
                WantFilterChip(
                    selected = range == candidate,
                    onClick = {
                        range = candidate
                        roundSeed = System.nanoTime()
                    },
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
                WantTextButton(onClick = { pickingDate = "start" }) {
                    Text(customStart?.let { "起：${Dates.formatDayShort(it)}" } ?: "选择起始日")
                }
                WantTextButton(onClick = { pickingDate = "end" }) {
                    Text(customEnd?.let { "止：${Dates.formatDayShort(it)}" } ?: "截止至今")
                }
                if (customStart != null || customEnd != null) {
                    WantTextButton(onClick = {
                        customStart = null
                        customEnd = null
                        roundSeed = System.nanoTime()
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
                    WantFilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            selectedTagIds = if (tag.id in selectedTagIds) {
                                selectedTagIds - tag.id
                            } else {
                                selectedTagIds + tag.id
                            }
                            roundSeed = System.nanoTime()
                        },
                        label = { Text(tag.path) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "符合条件 ${pool.size} 篇 · 未回顾 $unreviewedInPool 篇 · 本轮 ${batch.size} 篇",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (batch.isNotEmpty()) {
                WantTextButton(onClick = { startNextBatch() }) {
                    Text("再来一批")
                }
            }
        }

        if (batch.isEmpty()) {
            Text(
                "这个范围里还没有可回顾的笔记。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { batch.size })
            LaunchedEffect(roundSeed) {
                pagerState.scrollToPage(0)
            }
            // 滑动到最后一张卡片时，自动把本轮所有回顾过的笔记标为「已回顾」
            LaunchedEffect(pagerState.currentPage, batch.size, roundSeed) {
                if (batch.isNotEmpty() && pagerState.currentPage == batch.lastIndex) {
                    markCurrentRoundReviewed(showToast = true)
                }
            }

            val currentIdx = pagerState.currentPage.coerceIn(0, (batch.size - 1).coerceAtLeast(0))
            WantLinearProgressIndicator(
                progress = { (currentIdx + 1).toFloat() / batch.size.coerceAtLeast(1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
            Text(
                text = "左右滑动切换卡片（${currentIdx + 1} / ${batch.size}）${if (roundMarkedReviewed) " · 本轮已标记「已回顾」" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 4.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 340.dp),
            ) { page ->
                val item = batch[page]
                val isReviewed = item.note.reviewed || roundMarkedReviewed
                WantSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(20.dp),
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = Dates.formatTimestamp(item.note.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isReviewed) Color(0xFF2FA84F).copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (isReviewed) "已回顾" else "未回顾",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isReviewed) Color(0xFF2FA84F) else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = SearchText.displayTitle(item.note),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TagChipRow(tags = item.tags, max = 4)
                        val bodyPreview = item.note.body.trim().take(600)
                        if (bodyPreview.isNotBlank()) {
                            MarkdownText(
                                text = bodyPreview,
                                linkTitles = linkTitles,
                                maxLines = 10,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                renderInlineImages = false,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WantTextButton(onClick = { onOpenNote(item.note.id) }) {
                                Text("查看全文")
                            }
                            if (page == batch.lastIndex) {
                                WantButton(onClick = { startNextBatch() }) {
                                    Text("再来一批")
                                }
                            } else {
                                WantTextButton(
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                                    },
                                ) {
                                    Text("下一张 →")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WantTextButton(
                    onClick = { markCurrentRoundReviewed(showToast = true) },
                    enabled = !roundMarkedReviewed,
                    modifier = Modifier.weight(1f),
                ) { Text(if (roundMarkedReviewed) "本轮已回顾" else "标记本轮已回顾") }
                WantButton(
                    onClick = { startNextBatch() },
                    modifier = Modifier.weight(1f),
                ) { Text("再来一批") }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    val picking = pickingDate
    if (picking != null) {
        val initial = (if (picking == "start") customStart else customEnd)
            ?: System.currentTimeMillis()
        WantDatePickerDialog(
            initialMillis = initial,
            onDismiss = { pickingDate = null },
            onConfirm = { sel ->
                if (picking == "start") {
                    customStart = Dates.startOfDay(sel)
                } else {
                    customEnd = Dates.endOfDay(sel)
                }
                roundSeed = System.nanoTime()
                pickingDate = null
            },
        )
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
    repository: NoteRepository,
    ai: AiService,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onOpenAiSettings: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var summaryScope by remember { mutableStateOf(SummaryScope.WEEKLY) }
    var scopeTagId by remember { mutableStateOf<String?>(null) }
    var topic by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val todayStart = Dates.startOfDay(now)
    val todayEnd = Dates.endOfDay(now)
    val weekStart = Dates.startOfWeek()
    val monthStart = remember {
        val cal = Dates.nowCalendar()
        Dates.startOfDay(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 1)
    }

    val todayItems = remember(items, todayStart, todayEnd) {
        NoteRepository.filterNotes(items, startMs = todayStart, endMs = todayEnd)
    }
    val thisWeekItems = remember(items, weekStart) {
        NoteRepository.filterNotes(items, startMs = weekStart)
    }

    val scopedItems = remember(items, summaryScope, scopeTagId, topic, todayStart, weekStart, monthStart) {
        when (summaryScope) {
            SummaryScope.DAILY -> NoteRepository.filterNotes(items, startMs = todayStart)
            SummaryScope.WEEKLY -> NoteRepository.filterNotes(items, startMs = weekStart)
            SummaryScope.MONTHLY -> NoteRepository.filterNotes(items, startMs = monthStart)
            SummaryScope.TAG -> {
                val tagId = scopeTagId
                if (tagId == null) emptyList() else NoteRepository.filterNotes(items, tagIds = setOf(tagId))
            }
            SummaryScope.TOPIC -> {
                val q = topic.trim()
                if (q.isEmpty()) emptyList() else NoteRepository.filterNotes(items, keyword = q)
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

    fun buildCorpus(list: List<NoteWithDetails>): String = list.take(50).joinToString("\n\n") { item ->
        val title = SearchText.displayTitle(item.note)
        val body = item.note.body.take(800)
        "《$title》\n$body"
    }

    fun runAiTask(
        enabledFlag: Boolean,
        disabledMsg: String,
        targetItems: List<NoteWithDetails>,
        emptyMsg: String,
        scopeTitle: String,
        call: suspend (String) -> Result<String>,
    ) {
        if (!enabledFlag) {
            scope.launch { snackbar.showSnackbar(disabledMsg) }
            return
        }
        if (settings.aiSummaryProviderId.isBlank() || settings.aiSummaryModelId.isBlank()) {
            onOpenAiSettings()
            return
        }
        if (targetItems.isEmpty()) {
            scope.launch { snackbar.showSnackbar(emptyMsg) }
            return
        }
        if (running) return
        running = true
        scope.launch {
            val corpus = buildCorpus(targetItems)
            val result = call(corpus)
            result.onSuccess { text ->
                settingsRepo.update {
                    it.copy(
                        aiSummaryText = text,
                        aiSummaryUpdatedAt = System.currentTimeMillis(),
                        aiSummaryScope = scopeTitle,
                    )
                }
            }.onFailure { error ->
                snackbar.showSnackbar(error.message ?: "生成失败，请检查 AI 配置")
            }
            running = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("快捷报告", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WantButton(
                onClick = {
                    val todayLabel = Dates.formatDayShort(System.currentTimeMillis())
                    runAiTask(
                        enabledFlag = settings.aiEnableSummary,
                        disabledMsg = "AI提炼能力已关闭",
                        targetItems = todayItems,
                        emptyMsg = "今天（$todayLabel）还没有记录笔记",
                        scopeTitle = "AI 日报 · $todayLabel",
                    ) { corpus -> ai.generateDailyReport(corpus, todayLabel) }
                },
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "生成中…" else "AI 日报（${todayItems.size}）")
            }
            WantButton(
                onClick = {
                    val weekLabel = "${Dates.formatDayShort(weekStart)} 起本周"
                    runAiTask(
                        enabledFlag = settings.aiEnableSummary,
                        disabledMsg = "AI提炼能力已关闭",
                        targetItems = thisWeekItems,
                        emptyMsg = "本自然周还没有记录笔记",
                        scopeTitle = "AI 周报 · $weekLabel",
                    ) { corpus -> ai.generateWeeklyReport(corpus, weekLabel) }
                },
                enabled = !running,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "生成中…" else "AI 周报（${thisWeekItems.size}）")
            }
        }

        Text("范围", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryScope.entries.forEach { candidate ->
                WantFilterChip(
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
                            WantFilterChip(
                                selected = scopeTagId == tag.id,
                                onClick = { scopeTagId = if (scopeTagId == tag.id) null else tag.id },
                                label = { Text(tag.path) },
                            )
                        }
                    }
                }
            }
            SummaryScope.TOPIC -> {
                WantTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = "主题关键字",
                    placeholder = "例如 复习计划，会匹配标题、正文和标签",
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
            WantButton(
                onClick = {
                    runAiTask(
                        enabledFlag = settings.aiEnableSummary,
                        disabledMsg = "AI提炼能力已关闭",
                        targetItems = scopedItems,
                        emptyMsg = "当前范围没有笔记",
                        scopeTitle = "$scopeLabel · AI 提炼",
                    ) { corpus -> ai.generateSummary(corpus) }
                },
                enabled = !running && scopedItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when {
                        running -> "正在生成…"
                        settings.aiSummaryModelId.isBlank() -> "去配置 AI"
                        else -> "AI 提炼"
                    },
                )
            }
            WantButton(
                onClick = {
                    runAiTask(
                        enabledFlag = settings.aiEnableInsight,
                        disabledMsg = "AI洞察能力已关闭",
                        targetItems = scopedItems,
                        emptyMsg = "当前范围没有笔记",
                        scopeTitle = "$scopeLabel · AI 洞察",
                    ) { corpus -> ai.generateInsight(corpus) }
                },
                enabled = !running && scopedItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (running) "正在生成…" else "AI 洞察")
            }
        }
        if (running) {
            Spacer(modifier = Modifier.height(8.dp))
            WantLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (settings.aiSummaryText.isNotBlank()) {
            WantSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.aiSummaryScope.ifBlank { "AI 总结" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (settings.aiSummaryUpdatedAt > 0L) {
                                Text(
                                    Dates.formatTimestamp(settings.aiSummaryUpdatedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        WantTextButton(
                            onClick = {
                                val title = settings.aiSummaryScope.ifBlank {
                                    "AI 总结 · ${Dates.formatDayShort(System.currentTimeMillis())}"
                                }
                                val body = settings.aiSummaryText
                                scope.launch {
                                    val blank = repository.createBlank()
                                    repository.saveMarkdown(blank.id, title, body)
                                    snackbar.showSnackbar("已保存为笔记「$title」")
                                    onOpenNote(blank.id)
                                }
                            },
                        ) { Text("保存为笔记") }
                    }
                    Text(
                        text = settings.aiSummaryText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        WantTextButton(
                            onClick = {
                                scope.launch {
                                    settingsRepo.update {
                                        it.copy(aiSummaryText = "", aiSummaryUpdatedAt = 0L, aiSummaryScope = "")
                                    }
                                }
                            },
                        ) { Text("清除结果") }
                    }
                }
            }
        }

        WantSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
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
                WantSurface(
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
    WantSurface(
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
