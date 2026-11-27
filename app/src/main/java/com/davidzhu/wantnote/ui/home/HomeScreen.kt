
package com.davidzhu.wantnote.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.davidzhu.wantnote.BuildConfig
import com.davidzhu.wantnote.update.UpdateInfo
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantCircularProgressIndicator
import com.davidzhu.wantnote.ui.components.WantDropdownMenu
import com.davidzhu.wantnote.ui.components.WantDropdownMenuItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantScaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import com.davidzhu.wantnote.ui.components.WantTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.HomeViewMode
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalDinox
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

/** 主页筛选维度：全部 / 未分配 / 按卡片盒 / 按标签（0.10.0 单选）。 */
private sealed interface HomeFilter {
    data object All : HomeFilter
    data object Unassigned : HomeFilter
    data class ByBox(val boxId: String) : HomeFilter
    data class ByTag(val tagId: String) : HomeFilter
}

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
    // 0.10.0：卡片盒 / 标签筛选（会话级，不持久化）。
    var filter by remember { mutableStateOf<HomeFilter>(HomeFilter.All) }

    val filtered = remember(items, filter) {
        val current = filter
        when (current) {
            HomeFilter.All -> items
            HomeFilter.Unassigned -> NoteRepository.filterNotes(items, boxId = "")
            is HomeFilter.ByBox -> NoteRepository.filterNotes(items, boxId = current.boxId)
            is HomeFilter.ByTag -> NoteRepository.filterNotes(items, tagIds = setOf(current.tagId))
        }
    }

    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WantTopBar(
                title = "想记",
                navigationIcon = if (availableUpdate != null) {
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
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
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
                )
            }
        }
    }

    /**
     * Fill-only generation for ONE field: 生成标题 fills only a blank title,
     * 生成标签 fills only a missing tag set; the other field is never touched
     * (its suggestion side is passed as empty). A field that already has
     * content is reported without spending an AI request. Failure messages
     * come from AiService and are user-safe: they name the missing setup
     * without echoing keys or endpoints.
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomeMenuAction("编辑笔记") {
                        menuNote = null
                        onEditNote(target.note.id)
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

/** 0.10.0：视图模式下拉框（卡片/瀑布流/极简/时间戳）。 */
@Composable
private fun ViewModeDropdown(
    selected: HomeViewMode,
    onSelect: (HomeViewMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
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
        HomeFilter.Unassigned -> "未分配"
        is HomeFilter.ByBox -> "卡片盒：${boxes.firstOrNull { it.id == filter.boxId }?.name ?: "…"}"
        is HomeFilter.ByTag -> "#${tags.firstOrNull { it.id == filter.tagId }?.path ?: "…"}"
    }
    androidx.compose.foundation.layout.Box {
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
                        androidx.compose.foundation.layout.Box {
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
