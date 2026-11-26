package com.davidzhu.wantnote.ui.editor

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.BoxCreateResult
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.EditorMode
import com.davidzhu.wantnote.data.NoteBoxActions
import com.davidzhu.wantnote.data.NoteTagActions
import com.davidzhu.wantnote.data.NoteTagRemover
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.data.LiquidGlassSurfaceRole
import com.davidzhu.wantnote.ui.components.BoxPickerDialog
import com.davidzhu.wantnote.ui.components.liquidGlassSurface
import com.davidzhu.wantnote.ui.components.MarkdownPreview
import com.davidzhu.wantnote.ui.components.NoteTagRemoveDialog
import com.davidzhu.wantnote.ui.components.TagAddButton
import com.davidzhu.wantnote.ui.components.TagAddDialog
import com.davidzhu.wantnote.ui.components.TagChipRow
import com.davidzhu.wantnote.ui.components.WantDropdownMenu
import com.davidzhu.wantnote.ui.components.WantDropdownMenuItem
import com.davidzhu.wantnote.ui.components.WantScaffold
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantBackTopBar
import com.davidzhu.wantnote.ui.theme.LocalEditorFontFamily
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text

/**
 * 0.11.0 N14-N17：笔记阅读态（只读详情页）。
 *
 * - 主页/搜索/日历/洞察点开笔记先进入本页只读展示渲染后的 Markdown；
 * - 右上角「编辑」进入编辑态（默认所见即所得）；
 * - 右上角「高级」收入「源码模式」，仅在用户主动打开高级菜单时切换。
 *
 * 0.13.0：
 * - 点标题下的卡片盒名（没有盒子时显示「选择卡片盒」）打开可搜索的改盒弹窗，
 *   关键字无匹配时提供「新建卡片盒」并把新盒赋给本篇笔记；该弹窗与编辑态共用；
 * - 标签行右侧**始终**有「+」（即使没有标签），点已有标签可从本篇笔记移除一个或
 *   多个标签（`removeTagFromNote`，不动标签本身），多选移除有二次确认。
 */
@Composable
fun NoteDetailScreen(
    noteId: String,
    onBack: () -> Unit,
    onEdit: (EditorMode) -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val repository = LocalNotes.current
    val media = LocalMedia.current
    val editorFont = LocalEditorFontFamily.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val noteRow by repository.observeById(noteId).collectAsStateWithLifecycle(initialValue = null)
    val noteTags by repository.observeTagsForNote(noteId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allTags by repository.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val boxes by repository.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val allNotes by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLinks by repository.observeAllLinks().collectAsStateWithLifecycle(initialValue = emptyList())

    var loaded by remember(noteId) { mutableStateOf(false) }
    var content by remember(noteId) { mutableStateOf("") }
    var advancedMenuOpen by remember { mutableStateOf(false) }
    var boxDialogOpen by remember(noteId) { mutableStateOf(false) }
    var tagAddOpen by remember(noteId) { mutableStateOf(false) }
    var tagRemoveOpen by remember(noteId) { mutableStateOf(false) }

    val linkTitles = remember(allNotes) { allNotes.associate { it.id to SearchText.displayTitle(it) } }
    val outgoing = remember(allLinks, noteId) { allLinks.filter { it.sourceNoteId == noteId } }
    val incoming = remember(allLinks, noteId) { allLinks.filter { it.targetNoteId == noteId } }

    // 返回阅读态时，若编辑态更新了标题/正文/时间，自动重读 .md 源以展示最新内容。
    LaunchedEffect(noteId, noteRow?.updatedAt, noteRow?.mdPath, noteRow?.body) {
        val note = noteRow ?: repository.getById(noteId)
        content = if (note != null) repository.loadContent(note) else ""
        loaded = true
    }

    fun openAttachment(path: String) {
        val file = media.fileFor(path)
        val mime = context.contentResolver.getType(
            android.net.Uri.fromFile(file),
        ) ?: guessedMime(path)
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "打开附件"))
        }.onFailure {
            scope.launch { snackbar.showSnackbar("无法打开附件") }
        }
    }

    WantScaffold(
        containerColor = MiuixTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WantBackTopBar(
                title = "笔记详情",
                onBack = onBack,
                actions = {
                    if (noteRow != null) {
                        WantTextButton(onClick = { onEdit(EditorMode.WYSIWYG) }) {
                            Text("编辑")
                        }
                        Box {
                            WantTextButton(onClick = { advancedMenuOpen = true }) {
                                Text("高级")
                            }
                            WantDropdownMenu(
                                expanded = advancedMenuOpen,
                                onDismissRequest = { advancedMenuOpen = false },
                            ) {
                                WantDropdownMenuItem(
                                    text = { Text("源码模式") },
                                    onClick = {
                                        advancedMenuOpen = false
                                        onEdit(EditorMode.SOURCE)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val note = noteRow
        if (loaded && note == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text(
                    "这篇笔记已不存在。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (note != null) {
            val boxName = boxes.firstOrNull { it.id == note.boxId }?.name
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = editorFont),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = Dates.formatTime(note.updatedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 阅读态改卡片盒入口：点盒名（无盒时为「选择卡片盒」）。
                    Row(
                        modifier = Modifier
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(50),
                                role = LiquidGlassSurfaceRole.CHIP,
                                baseColor = MiuixTheme.colorScheme.surfaceContainer,
                            )
                            .clickable { boxDialogOpen = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (boxName.isNullOrBlank()) Icons.Outlined.Add else Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = boxName?.takeIf { it.isNotBlank() } ?: "选择卡片盒",
                            style = MaterialTheme.typography.labelMedium,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 标签行：右侧永远有「+」，没有标签时也能加。
                TagChipRow(
                    tags = noteTags,
                    max = Int.MAX_VALUE,
                    onClick = { tagRemoveOpen = true },
                    trailing = { TagAddButton(onClick = { tagAddOpen = true }) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                MarkdownPreview(
                    content = content,
                    linkTitles = linkTitles,
                    onOpenAttachment = { openAttachment(it) },
                    scrollable = false,
                )
                if (outgoing.isNotEmpty() || incoming.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "相关链接",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    outgoing.forEach { link ->
                        val title = linkTitles[link.targetNoteId] ?: return@forEach
                        Text(
                            text = "→ $title",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenNote(link.targetNoteId) }
                                .padding(vertical = 4.dp),
                        )
                    }
                    incoming.forEach { link ->
                        val title = linkTitles[link.sourceNoteId] ?: return@forEach
                        Text(
                            text = "← $title",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenNote(link.sourceNoteId) }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (boxDialogOpen) {
        BoxPickerDialog(
            boxes = boxes,
            currentBoxId = noteRow?.boxId,
            onPick = { box ->
                boxDialogOpen = false
                scope.launch {
                    repository.assignNoteToBox(noteId, box.id)
                    snackbar.showSnackbar("已归入「${box.name}」")
                }
            },
            onCreate = { name ->
                NoteBoxActions.createAndAssign(
                    createBox = repository::createBox,
                    assignNoteToBox = repository::assignNoteToBox,
                    noteId = noteId,
                    name = name,
                ).also { result ->
                    if (result is BoxCreateResult.Created) {
                        val box = result.box
                        scope.launch {
                            snackbar.showSnackbar("已新建「${box.name}」并归入本篇")
                        }
                    }
                }
            },
            onDismiss = { boxDialogOpen = false },
        )
    }

    if (tagAddOpen) {
        TagAddDialog(
            allTags = allTags,
            selectedIds = noteTags.map { it.id }.toSet(),
            onToggle = { tag ->
                scope.launch {
                    if (noteTags.any { it.id == tag.id }) {
                        repository.removeTagFromNote(noteId, tag.id)
                    } else {
                        repository.addTagToNote(noteId, tag.path, tag.color)
                    }
                }
            },
            onCreate = { path, color ->
                scope.launch { repository.addTagToNote(noteId, path, color) }
            },
            onDismiss = { tagAddOpen = false },
        )
    }

    if (tagRemoveOpen) {
        NoteTagRemoveDialog(
            tags = noteTags,
            onConfirmRemove = { picked ->
                tagRemoveOpen = false
                val remover = object : NoteTagRemover {
                    override suspend fun removeTagFromNote(noteId: String, tagId: String) {
                        repository.removeTagFromNote(noteId, tagId)
                    }
                }
                scope.launch {
                    val count = NoteTagActions.removeTagsFromNote(
                        remover = remover,
                        noteId = noteId,
                        tagIds = picked.map(Tag::id),
                    )
                    snackbar.showSnackbar(
                        if (count > 1) "已从本篇移除 $count 个标签" else "已移除标签",
                    )
                }
            },
            onDismiss = { tagRemoveOpen = false },
        )
    }
}
