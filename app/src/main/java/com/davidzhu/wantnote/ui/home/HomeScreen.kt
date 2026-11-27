package com.davidzhu.wantnote.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.BuildConfig
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.BoxCreateResult
import com.davidzhu.wantnote.data.HomeViewMode
import com.davidzhu.wantnote.data.LiquidGlassSurfaceRole
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.sync.ExternalReadingSyncService
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalDinox
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.BoxPickerDialog
import com.davidzhu.wantnote.ui.components.TagAddDialog
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantCircularProgressIndicator
import com.davidzhu.wantnote.ui.components.WantDropdownMenu
import com.davidzhu.wantnote.ui.components.WantDropdownMenuItem
import com.davidzhu.wantnote.ui.components.WantLinearProgressIndicator
import com.davidzhu.wantnote.ui.components.WantScaffold
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTopBar
import com.davidzhu.wantnote.ui.components.liquidGlassSurface
import com.davidzhu.wantnote.update.UpdateInfo
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 主页筛选维度：全部 / 未分配 / 按卡片盒 / 按标签（0.10.0 单选）。 */
private sealed interface HomeFilter {
    data object All : HomeFilter
    data object Pinned : HomeFilter
    data object Starred : HomeFilter
    data object Unassigned : HomeFilter
    data class ByBox(val boxId: String) : HomeFilter
    data class ByTag(val tagId: String) : HomeFilter
}

/** 0.19.0：批量发送队列进度状态（发送到 Flomo / Dinox）。 */
private data class SendQueueProgress(
    val targetLabel: String,
    val current: Int,
    val total: Int,
    val currentTitle: String,
)

@Composable
fun HomeScreen(
    onOpenNote: (String) -> Unit,
    onEditNote: (String) -> Unit = onOpenNote,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
    availableUpdate: UpdateInfo? = null,
) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val ai = LocalAi.current
    val dinox = LocalDinox.current
    val items by repository.observeAllWithDetails().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLinks by repository.observeAllLinks().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val boxes by repository.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val allTags by repository.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val viewMode = settings.homeViewMode
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuNote by remember { mutableStateOf<NoteWithDetails?>(null) }
    var deleteTarget by remember { mutableStateOf<NoteWithDetails?>(null) }
    var sendTarget by remember { mutableStateOf<NoteWithDetails?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }

    // 0.19.0：多选模式状态与批量操作弹窗/队列进度
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchBoxDialog by remember { mutableStateOf(false) }
    var showBatchTagDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchDinoxDialog by remember { mutableStateOf(false) }
    var sendQueue by remember { mutableStateOf<SendQueueProgress?>(null) }

    // 0.10.0：卡片盒 / 标签筛选（会话级，不持久化）。
    var filter by remember { mutableStateOf<HomeFilter>(HomeFilter.All) }

    val filtered = remember(items, filter) {
        val current = filter
        when (current) {
            HomeFilter.All -> items
            HomeFilter.Pinned -> NoteRepository.filterNotes(items, pinnedOnly = true)
            HomeFilter.Starred -> NoteRepository.filterNotes(items, starredOnly = true)
            HomeFilter.Unassigned -> NoteRepository.filterNotes(items, boxId = "")
            is HomeFilter.ByBox -> NoteRepository.filterNotes(items, boxId = current.boxId)
            is HomeFilter.ByTag -> NoteRepository.filterNotes(items, tagIds = setOf(current.tagId))
        }
    }

    val selectedNotes = remember(items, selectedIds) {
        items.filter { it.note.id in selectedIds }
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = multiSelectMode) {
        if (sendQueue == null) {
            exitMultiSelect()
        }
    }

    /** 0.19.0：批量发送到 Flomo（顺序队列 + 实时进度展示）。 */
    fun runBatchSendFlomo(targets: List<NoteWithDetails>) {
        if (targets.isEmpty() || sendQueue != null) return
        val webhook = settings.flomoWebhookUrl.trim()
        if (webhook.isEmpty()) {
            scope.launch {
                snackbar.showSnackbar("请先在「设置 → 数据管理 → 生态互联 → Flomo」填写 Flomo API URL")
            }
            return
        }
        scope.launch {
            var ok = 0
            var fail = 0
            var lastErr = ""
            targets.forEachIndexed { index, item ->
                sendQueue = SendQueueProgress(
                    targetLabel = "Flomo",
                    current = index + 1,
                    total = targets.size,
                    currentTitle = SearchText.displayTitle(item.note),
                )
                val res = ExternalReadingSyncService.sendNoteToFlomo(webhook, item.note, item.tags)
                if (res.isSuccess) {
                    ok++
                } else {
                    fail++
                    lastErr = res.exceptionOrNull()?.message.orEmpty()
                }
            }
            sendQueue = null
            exitMultiSelect()
            val summary = if (fail == 0) {
                "已发送 $ok 篇笔记到 Flomo"
            } else {
                "发送到 Flomo 完成：成功 $ok 篇，失败 $fail 篇${if (lastErr.isNotBlank()) "（$lastErr）" else ""}"
            }
            snackbar.showSnackbar(summary)
        }
    }

    /** 0.19.0：批量发送到 Dinox（顺序队列 + 实时进度展示）。 */
    fun runBatchSendDinox(targets: List<NoteWithDetails>, boxName: String?) {
        if (targets.isEmpty() || sendQueue != null) return
        scope.launch {
            var ok = 0
            var fail = 0
            var lastErr = ""
            targets.forEachIndexed { index, item ->
                sendQueue = SendQueueProgress(
                    targetLabel = "Dinox",
                    current = index + 1,
                    total = targets.size,
                    currentTitle = SearchText.displayTitle(item.note),
                )
                val res = dinox.sendNote(item.note.id, boxName)
                if (res.isSuccess) {
                    ok++
                } else {
                    fail++
                    lastErr = res.exceptionOrNull()?.message.orEmpty()
                }
            }
            sendQueue = null
            exitMultiSelect()
            val summary = if (fail == 0) {
                "已发送 $ok 篇笔记到 Dinox"
            } else {
                "发送到 Dinox 完成：成功 $ok 篇，失败 $fail 篇${if (lastErr.isNotBlank()) "（$lastErr）" else ""}"
            }
            snackbar.showSnackbar(summary)
        }
    }

    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WantTopBar(
                title = if (multiSelectMode) "已选 ${selectedIds.size} 篇" else "想记",
                navigationIcon = if (!multiSelectMode && availableUpdate != null) {
                    {
                        IconButton(onClick = { showUpdateDialog = true }) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A73E8)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ArrowUpward,
                                    contentDescription = "发现新版本 ${availableUpdate.versionName}",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                actions = {
                    if (multiSelectMode) {
                        WantTextButton(
                            onClick = { if (sendQueue == null) exitMultiSelect() },
                            enabled = sendQueue == null,
                        ) {
                            Text("完成")
                        }
                    } else {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (multiSelectMode) {
                    // 0.19.0：多选顶部控制栏（全选 / 取消全选 / 反选）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WantTextButton(
                            onClick = { selectedIds = filtered.map { it.note.id }.toSet() },
                            enabled = sendQueue == null,
                        ) { Text("全选") }
                        WantTextButton(
                            onClick = { selectedIds = emptySet() },
                            enabled = sendQueue == null,
                        ) { Text("取消全选") }
                        WantTextButton(
                            onClick = {
                                val visibleIds = filtered.map { it.note.id }.toSet()
                                selectedIds = visibleIds - selectedIds
                            },
                            enabled = sendQueue == null,
                        ) { Text("反选") }
                    }
                } else {
                    // 0.10.0：顶部展示控件改为两个下拉框（视图模式 + 卡片盒/标签筛选），
                    // 不再是平铺的选项行。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ViewModeDropdown(
                            selected = viewMode,
                            onSelect = { mode ->
                                scope.launch {
                                    settingsRepo.update { it.copy(homeView = mode.name) }
                                }
                            },
                        )
                        FilterDropdown(
                            filter = filter,
                            boxes = boxes,
                            tags = allTags,
                            onSelect = { filter = it },
                        )
                    }
                }

                // 0.19.0：发送队列实时进度横幅
                sendQueue?.let { progress ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "正在发送到 ${progress.targetLabel}（${progress.current}/${progress.total}）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${(progress.current * 100 / progress.total.coerceAtLeast(1))}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = progress.currentTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        WantLinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        text = if (items.isEmpty()) {
                            "还没有笔记。点中间的加号就可以开始写，不必先起标题。"
                        } else {
                            "当前筛选条件下没有笔记。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                            .clickable(onClick = onCreate),
                    )
                } else {
                    NoteCollection(
                        items = filtered,
                        links = allLinks,
                        viewMode = viewMode,
                        onOpenNote = onOpenNote,
                        onLongPressNote = { id -> menuNote = items.firstOrNull { it.note.id == id } },
                        profileNickname = settings.profileNickname,
                        profileAvatarPath = settings.profileAvatarPath,
                        multiSelectMode = multiSelectMode,
                        selectedIds = selectedIds,
                        onToggleSelect = { id ->
                            if (sendQueue == null) {
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            }
                        },
                    )
                }
            }

            // 0.19.0：多选底部操作胶囊（置顶 / 星标 / 删除 / 设置卡片盒 / 设置标签 / 发送到flomo / 发送到Dinox）
            if (multiSelectMode) {
                val hasSelection = selectedNotes.isNotEmpty() && sendQueue == null
                val allPinned = selectedNotes.isNotEmpty() && selectedNotes.all { it.note.pinned }
                val allStarred = selectedNotes.isNotEmpty() && selectedNotes.all { it.note.starred }
                MultiSelectBottomCapsule(
                    enabled = hasSelection,
                    allPinned = allPinned,
                    allStarred = allStarred,
                    onPin = {
                        val targets = selectedNotes
                        val targetPinned = !allPinned
                        scope.launch {
                            targets.forEach { repository.setPinned(it.note.id, targetPinned) }
                            snackbar.showSnackbar(
                                if (targetPinned) "已置顶 ${targets.size} 篇笔记" else "已取消置顶 ${targets.size} 篇笔记",
                            )
                            exitMultiSelect()
                        }
                    },
                    onStar = {
                        val targets = selectedNotes
                        val targetStarred = !allStarred
                        scope.launch {
                            targets.forEach { repository.setStarred(it.note.id, targetStarred) }
                            snackbar.showSnackbar(
                                if (targetStarred) "已星标 ${targets.size} 篇笔记" else "已取消星标 ${targets.size} 篇笔记",
                            )
                            exitMultiSelect()
                        }
                    },
                    onDelete = { showBatchDeleteDialog = true },
                    onSetBox = { showBatchBoxDialog = true },
                    onSetTags = { showBatchTagDialog = true },
                    onSendFlomo = { runBatchSendFlomo(selectedNotes) },
                    onSendDinox = { showBatchDinoxDialog = true },
                    onCancel = { exitMultiSelect() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }

    /**
     * Fill-only generation for ONE field: 生成标题 fills only a blank title,
     * 生成标签 fills only a missing tag set; the other field is never touched
     * (its suggestion side is passed as empty).
     */
    fun generateField(target: NoteWithDetails, wantTitle: Boolean) {
        val id = target.note.id
        menuNote = null
        if (wantTitle && !settings.aiEnableTitle) {
            scope.launch { snackbar.showSnackbar("AI生成标题能力已关闭") }
            return
        }
        if (!wantTitle && !settings.aiEnableTag) {
            scope.launch { snackbar.showSnackbar("AI生成标签能力已关闭") }
            return
        }
        busy = true
        scope.launch {
            val message = if (wantTitle && target.note.title.isNotBlank()) {
                "标题已有内容，未改动"
            } else if (!wantTitle && repository.getTagsForNote(id).isNotEmpty()) {
                "标签已有内容，未改动"
            } else {
                ai.suggestFillTitleTags(id).fold(
                    onSuccess = { suggestion ->
                        val filled = if (wantTitle) {
                            repository.fillEmptyTitleAndTags(id, suggestion.title, emptyList())
                        } else {
                            repository.fillEmptyTitleAndTags(id, "", suggestion.tags)
                        }
                        when {
                            wantTitle && filled.titleFilled -> "已补充标题"
                            !wantTitle && filled.tagsFilled -> "已补充标签"
                            else -> if (wantTitle) "标题已有内容，未改动" else "标签已有内容，未改动"
                        }
                    },
                    onFailure = { error ->
                        error.message?.take(160)?.ifBlank { null }
                            ?: "AI 未配置或暂时不可用，请到「设置 → AI」检查"
                    },
                )
            }
            busy = false
            snackbar.showSnackbar(message)
        }
    }

    /**
     * 0.10.0 长按「自动分配卡片盒」：AI 从已存在的卡片盒里挑一个最合适的，
     * 绝不新建盒子。没有盒子或 AI 未配置时给出可操作的提示。
     */
    fun autoAssignBox(target: NoteWithDetails) {
        val id = target.note.id
        menuNote = null
        if (!settings.aiEnableBox) {
            scope.launch { snackbar.showSnackbar("AI分配卡片盒能力已关闭") }
            return
        }
        busy = true
        scope.launch {
            val message = ai.suggestExistingBox(id).fold(
                onSuccess = { boxName ->
                    repository.assignNoteToBoxByName(id, boxName)?.let { box ->
                        "已分配到卡片盒「${box.name}」"
                    } ?: "自动分配失败，请重试或手动选择"
                },
                onFailure = { error ->
                    error.message?.take(160)?.ifBlank { null }
                        ?: "AI 未配置或暂时不可用，请到「设置 → AI」检查"
                },
            )
            busy = false
            snackbar.showSnackbar(message)
        }
    }

    if (showUpdateDialog && availableUpdate != null) {
        WantAlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本 ${availableUpdate.versionName}") },
            text = {
                Text("GitHub 上的 ${availableUpdate.releaseName} 新于当前的 ${BuildConfig.VERSION_NAME}。要立即下载更新吗？")
            },
            confirmButton = {
                WantTextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(availableUpdate.apkUrl ?: availableUpdate.htmlUrl)),
                            )
                        }
                        showUpdateDialog = false
                    },
                ) { Text("下载") }
            },
            dismissButton = {
                WantTextButton(onClick = { showUpdateDialog = false }) { Text("稍后") }
            },
        )
    }

    menuNote?.let { target ->
        WantAlertDialog(
            onDismissRequest = { if (!busy) menuNote = null },
            title = { Text(SearchText.displayTitle(target.note), maxLines = 2) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeMenuAction("编辑笔记") {
                        menuNote = null
                        onEditNote(target.note.id)
                    }
                    HomeMenuAction(if (target.note.pinned) "取消置顶" else "置顶", enabled = !busy) {
                        val nextPinned = !target.note.pinned
                        val id = target.note.id
                        menuNote = null
                        scope.launch {
                            repository.setPinned(id, nextPinned)
                            snackbar.showSnackbar(if (nextPinned) "已置顶笔记" else "已取消置顶")
                        }
                    }
                    HomeMenuAction(if (target.note.starred) "取消星标" else "星标", enabled = !busy) {
                        val nextStarred = !target.note.starred
                        val id = target.note.id
                        menuNote = null
                        scope.launch {
                            repository.setStarred(id, nextStarred)
                            snackbar.showSnackbar(if (nextStarred) "已添加星标" else "已取消星标")
                        }
                    }
                    HomeMenuAction("多选", enabled = !busy) {
                        menuNote = null
                        multiSelectMode = true
                        selectedIds = setOf(target.note.id)
                    }
                    HomeMenuAction("生成标题", enabled = !busy) {
                        generateField(target, wantTitle = true)
                    }
                    HomeMenuAction("生成标签", enabled = !busy) {
                        generateField(target, wantTitle = false)
                    }
                    HomeMenuAction("自动分配卡片盒", enabled = !busy) {
                        autoAssignBox(target)
                    }
                    HomeMenuAction("发送到flomo", enabled = !busy) {
                        menuNote = null
                        busy = true
                        scope.launch {
                            val result = ExternalReadingSyncService.sendNoteToFlomo(
                                webhookUrl = settings.flomoWebhookUrl,
                                note = target.note,
                                tags = target.tags,
                            )
                            busy = false
                            result.fold(
                                onSuccess = { snackbar.showSnackbar("已发送到 Flomo") },
                                onFailure = { error ->
                                    snackbar.showSnackbar(error.message ?: "发送到 Flomo 失败")
                                },
                            )
                        }
                    }
                    HomeMenuAction("发送到Dinox", enabled = !busy) {
                        menuNote = null
                        sendTarget = target
                    }
                    HomeMenuAction("删除笔记", enabled = !busy) {
                        menuNote = null
                        deleteTarget = target
                    }
                }
            },
            confirmButton = {
                WantTextButton(onClick = { menuNote = null }, enabled = !busy) { Text("取消") }
            },
        )
    }

    sendTarget?.let { target ->
        SendToDinoxDialog(
            noteTitle = SearchText.displayTitle(target.note),
            onSend = { boxName ->
                val id = target.note.id
                sendTarget = null
                busy = true
                scope.launch {
                    val result = dinox.sendNote(id, boxName)
                    busy = false
                    result.fold(
                        onSuccess = { snackbar.showSnackbar(it) },
                        onFailure = { error ->
                            snackbar.showSnackbar(error.message ?: "发送失败，请先在数据管理里配置 Dinox")
                        },
                    )
                }
            },
            onDismiss = { if (!busy) sendTarget = null },
        )
    }

    if (showBatchDinoxDialog) {
        val targets = selectedNotes
        SendToDinoxDialog(
            noteTitle = "已选中的 ${targets.size} 篇笔记",
            onSend = { boxName ->
                showBatchDinoxDialog = false
                runBatchSendDinox(targets, boxName)
            },
            onDismiss = { showBatchDinoxDialog = false },
        )
    }

    if (showBatchBoxDialog) {
        val targets = selectedNotes
        BoxPickerDialog(
            boxes = boxes,
            currentBoxId = null,
            onPick = { box ->
                showBatchBoxDialog = false
                scope.launch {
                    targets.forEach { repository.assignNoteToBox(it.note.id, box.id) }
                    snackbar.showSnackbar("已将 ${targets.size} 篇笔记归入「${box.name}」")
                    exitMultiSelect()
                }
            },
            onCreate = { name ->
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    BoxCreateResult.EmptyName
                } else {
                    val created = repository.createBox(trimmed)
                    if (created == null) {
                        BoxCreateResult.DuplicateName
                    } else {
                        targets.forEach { repository.assignNoteToBox(it.note.id, created.id) }
                        snackbar.showSnackbar("已新建「${created.name}」并归入 ${targets.size} 篇笔记")
                        exitMultiSelect()
                        BoxCreateResult.Created(created)
                    }
                }
            },
            onDismiss = { showBatchBoxDialog = false },
        )
    }

    if (showBatchTagDialog) {
        val targets = selectedNotes
        val commonTagIds = remember(targets) {
            if (targets.isEmpty()) emptySet()
            else targets.map { item -> item.tags.map { it.id }.toSet() }.reduce { acc, set -> acc intersect set }
        }
        TagAddDialog(
            allTags = allTags,
            selectedIds = commonTagIds,
            onToggle = { tag ->
                scope.launch {
                    if (tag.id in commonTagIds) {
                        targets.forEach { repository.removeTagFromNote(it.note.id, tag.id) }
                        snackbar.showSnackbar("已从 ${targets.size} 篇笔记移除 #${tag.path}")
                    } else {
                        targets.forEach { repository.attachTag(it.note.id, tag.id) }
                        snackbar.showSnackbar("已为 ${targets.size} 篇笔记添加 #${tag.path}")
                    }
                }
            },
            onCreate = { path, color ->
                scope.launch {
                    val created = repository.upsertTag(path, color)
                    if (created != null) {
                        targets.forEach { repository.attachTag(it.note.id, created.id) }
                        snackbar.showSnackbar("已为 ${targets.size} 篇笔记添加 #${created.path}")
                    }
                }
            },
            onDismiss = { showBatchTagDialog = false },
        )
    }

    if (showBatchDeleteDialog) {
        val targets = selectedNotes
        WantAlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除选中的 ${targets.size} 篇笔记？") },
            text = {
                Text("选中的 ${targets.size} 篇笔记及其内容、标签关联和双链都会被永久删除，无法撤销。")
            },
            confirmButton = {
                WantTextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        scope.launch {
                            var count = 0
                            targets.forEach {
                                if (repository.deleteNote(it.note.id)) count++
                            }
                            exitMultiSelect()
                            snackbar.showSnackbar("已删除 $count 篇笔记")
                        }
                    },
                ) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                WantTextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { target ->
        WantAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条笔记？") },
            text = {
                Text("「${SearchText.displayTitle(target.note)}」及其所有内容、标签关联和笔记链接都会被永久删除，无法撤销。")
            },
            confirmButton = {
                WantTextButton(
                    onClick = {
                        val id = target.note.id
                        deleteTarget = null
                        scope.launch {
                            val deleted = repository.deleteNote(id)
                            snackbar.showSnackbar(if (deleted) "已删除笔记" else "笔记已不存在")
                        }
                    },
                ) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                WantTextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 0.19.0：多选底部悬浮操作胶囊。 */
@Composable
private fun MultiSelectBottomCapsule(
    enabled: Boolean,
    allPinned: Boolean,
    allStarred: Boolean,
    onPin: () -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onSetBox: () -> Unit,
    onSetTags: () -> Unit,
    onSendFlomo: () -> Unit,
    onSendDinox: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(999.dp),
                role = LiquidGlassSurfaceRole.BAR,
                baseColor = MaterialTheme.colorScheme.surface,
                elevationOverride = 14.dp,
                borderWidth = 1.2.dp,
            )
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleActionItem(
            icon = Icons.Outlined.PushPin,
            label = if (allPinned) "取消置顶" else "置顶",
            enabled = enabled,
            onClick = onPin,
        )
        CapsuleActionItem(
            icon = if (allStarred) Icons.Rounded.Star else Icons.Outlined.StarBorder,
            label = if (allStarred) "取消星标" else "星标",
            enabled = enabled,
            onClick = onStar,
        )
        CapsuleActionItem(
            icon = Icons.Outlined.Folder,
            label = "设置卡片盒",
            enabled = enabled,
            onClick = onSetBox,
        )
        CapsuleActionItem(
            icon = Icons.Outlined.Label,
            label = "设置标签",
            enabled = enabled,
            onClick = onSetTags,
        )
        CapsuleActionItem(
            icon = Icons.Outlined.Send,
            label = "发送到flomo",
            enabled = enabled,
            onClick = onSendFlomo,
        )
        CapsuleActionItem(
            icon = Icons.Outlined.Send,
            label = "发送到Dinox",
            enabled = enabled,
            onClick = onSendDinox,
        )
        CapsuleActionItem(
            icon = Icons.Outlined.DeleteOutline,
            label = "删除",
            enabled = enabled,
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete,
        )
        CapsuleActionItem(
            icon = Icons.Outlined.Close,
            label = "取消",
            enabled = true,
            onClick = onCancel,
        )
    }
}

@Composable
private fun CapsuleActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val effectiveTint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = effectiveTint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effectiveTint,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeMenuAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    WantTextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

/** 下拉触发按钮：标签 + 右侧小箭头，统一样式。 */
@Composable
private fun DropdownTrigger(label: String, onClick: () -> Unit) {
    WantTextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Icon(
            Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** 0.10.0：视图模式下拉框（卡片/瀑布流/极简/时间戳/朋友圈）。 */
@Composable
private fun ViewModeDropdown(
    selected: HomeViewMode,
    onSelect: (HomeViewMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DropdownTrigger(label = selected.label) { expanded = true }
        WantDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HomeViewMode.entries.forEach { mode ->
                WantDropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

/** 0.10.0：筛选下拉框——按卡片盒或标签过滤主页卡片。 */
@Composable
private fun FilterDropdown(
    filter: HomeFilter,
    boxes: List<NoteBox>,
    tags: List<Tag>,
    onSelect: (HomeFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (filter) {
        HomeFilter.All -> "全部笔记"
        HomeFilter.Pinned -> "置顶"
        HomeFilter.Starred -> "星标"
        HomeFilter.Unassigned -> "未分配"
        is HomeFilter.ByBox -> "卡片盒：${boxes.firstOrNull { it.id == filter.boxId }?.name ?: "…"}"
        is HomeFilter.ByTag -> "#${tags.firstOrNull { it.id == filter.tagId }?.path ?: "…"}"
    }
    Box {
        DropdownTrigger(label = label) { expanded = true }
        WantDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WantDropdownMenuItem(
                text = { Text("全部笔记") },
                onClick = {
                    expanded = false
                    onSelect(HomeFilter.All)
                },
            )
            WantDropdownMenuItem(
                text = { Text("置顶") },
                onClick = {
                    expanded = false
                    onSelect(HomeFilter.Pinned)
                },
            )
            WantDropdownMenuItem(
                text = { Text("星标") },
                onClick = {
                    expanded = false
                    onSelect(HomeFilter.Starred)
                },
            )
            WantDropdownMenuItem(
                text = { Text("未分配（无卡片盒）") },
                onClick = {
                    expanded = false
                    onSelect(HomeFilter.Unassigned)
                },
            )
            if (boxes.isNotEmpty()) {
                boxes.forEach { box ->
                    WantDropdownMenuItem(
                        text = { Text("卡片盒：${box.name}") },
                        onClick = {
                            expanded = false
                            onSelect(HomeFilter.ByBox(box.id))
                        },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                tags.forEach { tag ->
                    WantDropdownMenuItem(
                        text = { Text("#${tag.path}") },
                        onClick = {
                            expanded = false
                            onSelect(HomeFilter.ByTag(tag.id))
                        },
                    )
                }
            }
        }
    }
}

/**
 * 0.12.0「发送到Dinox」对话框：
 * - 有本地缓存时立刻展示卡片盒下拉列表，不阻塞操作；
 * - 缓存超过两小时则先展示缓存，同时通过轻量 `GET /api/openapi/zettelboxes` 后台刷新；
 * - 缓存为空且轻量请求进行中仅显示短加载态，完成后立即呈现列表或短错误；
 * - 失败且缓存为空时立刻展示不含敏感信息的短错误与「重试」，重开弹窗不自动陷入阻塞循环；
 * - 失败且有缓存时保留上次缓存并展示短错误与可选「重试」。
 */
@Composable
private fun SendToDinoxDialog(
    noteTitle: String,
    onSend: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val dinox = LocalDinox.current
    val scope = rememberCoroutineScope()
    val observedBoxes by dinox.observeCachedBoxes().collectAsStateWithLifecycle(initialValue = null)
    val refreshState by dinox.boxRefreshState.collectAsStateWithLifecycle()
    var snapshotBoxes by remember { mutableStateOf<List<String>>(emptyList()) }
    var cacheChecked by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val displayBoxes = observedBoxes ?: snapshotBoxes

    fun requestBoxes(forceRetry: Boolean) {
        scope.launch {
            dinox.ensureBoxesForPicker(forceRetry = forceRetry)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val state = dinox.getBoxCacheState()
        snapshotBoxes = state.names
        cacheChecked = true
        dinox.ensureBoxesForPicker(forceRetry = false)
    }

    val pickerReady = cacheChecked && (displayBoxes.isNotEmpty() || (!refreshState.refreshing && refreshState.lastError == null))

    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发送到Dinox", maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "「$noteTitle」将按 Markdown 发送到 Dinox。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    displayBoxes.isEmpty() && (!cacheChecked || refreshState.refreshing) -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WantCircularProgressIndicator(
                                modifier = Modifier.padding(end = 10.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("正在获取卡片盒…")
                        }
                    }
                    displayBoxes.isEmpty() && refreshState.lastError != null -> {
                        Text(refreshState.lastError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        WantTextButton(onClick = { requestBoxes(forceRetry = true) }) {
                            Text("重试")
                        }
                    }
                    else -> {
                        Box {
                            WantTextButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    when {
                                        selected == null -> "选择目标卡片盒"
                                        selected.isNullOrEmpty() -> "不放入卡片盒"
                                        else -> "卡片盒：$selected"
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                            }
                            WantDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                WantDropdownMenuItem(
                                    text = { Text("不放入卡片盒") },
                                    onClick = {
                                        selected = ""
                                        menuOpen = false
                                    },
                                )
                                displayBoxes.forEach { name ->
                                    WantDropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selected = name
                                            menuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        if (refreshState.refreshing) {
                            Text(
                                "正在后台更新 Dinox 卡片盒列表…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (refreshState.lastError != null && displayBoxes.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "更新失败：${refreshState.lastError.orEmpty()}（已保留上次缓存）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                WantTextButton(onClick = { requestBoxes(forceRetry = true) }) {
                                    Text("重试")
                                }
                            }
                        }
                        if (displayBoxes.isEmpty() && !refreshState.refreshing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Dinox 里还没有卡片盒；可直接发送（不放入卡片盒）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                WantTextButton(onClick = { requestBoxes(forceRetry = true) }) {
                                    Text("刷新")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            WantTextButton(
                onClick = {
                    val chosen = selected ?: return@WantTextButton
                    sending = true
                    onSend(chosen.takeIf { it.isNotBlank() })
                },
                enabled = !sending && pickerReady && selected != null,
            ) { Text(if (sending) "发送中…" else "发送") }
        },
        dismissButton = {
            WantTextButton(onClick = onDismiss, enabled = !sending) { Text("取消") }
        },
    )
}
