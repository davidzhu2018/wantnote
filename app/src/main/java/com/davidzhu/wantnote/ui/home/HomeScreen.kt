@file:OptIn(ExperimentalMaterial3Api::class)

package com.davidzhu.wantnote.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.HomeViewMode
import com.davidzhu.wantnote.data.NoteWithDetails
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalDinox
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenNote: (String) -> Unit,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val ai = LocalAi.current
    val dinox = LocalDinox.current
    val items by repository.observeAllWithDetails().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLinks by repository.observeAllLinks().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val viewMode = settings.homeViewMode
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuNote by remember { mutableStateOf<NoteWithDetails?>(null) }
    var deleteTarget by remember { mutableStateOf<NoteWithDetails?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("想记") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
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
            ViewModeRow(
                selected = viewMode,
                onSelect = { mode ->
                    scope.launch {
                        settingsRepo.update { it.copy(homeView = mode.name) }
                    }
                },
            )
            if (items.isEmpty()) {
                Text(
                    text = "还没有笔记。点中间的加号就可以开始写，不必先起标题。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .clickable(onClick = onCreate),
                )
            } else {
                NoteCollection(
                    items = items,
                    links = allLinks,
                    viewMode = viewMode,
                    onOpenNote = onOpenNote,
                    onLongPressNote = { id -> menuNote = items.firstOrNull { it.note.id == id } },
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

    menuNote?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!busy) menuNote = null },
            title = { Text(SearchText.displayTitle(target.note), maxLines = 2) },
            text = {
                Column {
                    HomeMenuAction("编辑笔记") {
                        menuNote = null
                        onOpenNote(target.note.id)
                    }
                    HomeMenuAction("生成标题", enabled = !busy) {
                        generateField(target, wantTitle = true)
                    }
                    HomeMenuAction("生成标签", enabled = !busy) {
                        generateField(target, wantTitle = false)
                    }
                    HomeMenuAction("发送到Dinox", enabled = !busy) {
                        val id = target.note.id
                        menuNote = null
                        busy = true
                        scope.launch {
                            val result = dinox.sendNote(id)
                            busy = false
                            result.fold(
                                onSuccess = { snackbar.showSnackbar(it) },
                                onFailure = { error ->
                                    snackbar.showSnackbar(error.message ?: "发送失败，请先在数据管理里配置 Dinox")
                                },
                            )
                        }
                    }
                    HomeMenuAction("删除笔记", enabled = !busy) {
                        menuNote = null
                        deleteTarget = target
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuNote = null }, enabled = !busy) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条笔记？") },
            text = {
                Text("「${SearchText.displayTitle(target.note)}」及其所有内容、标签关联和笔记链接都会被永久删除，无法撤销。")
            },
            confirmButton = {
                TextButton(
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
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
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
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ViewModeRow(
    selected: HomeViewMode,
    onSelect: (HomeViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
            )
        }
    }
}
