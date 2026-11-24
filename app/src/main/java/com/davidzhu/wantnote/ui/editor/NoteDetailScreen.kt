package com.davidzhu.wantnote.ui.editor

import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.EditorMode
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.components.MarkdownPreview
import com.davidzhu.wantnote.ui.components.TagChipRow
import com.davidzhu.wantnote.ui.theme.LocalEditorFontFamily
import kotlinx.coroutines.launch

/**
 * 0.11.0 N14-N17：笔记阅读态（只读详情页）。
 *
 * - 主页/搜索/日历/洞察点开笔记先进入本页只读展示渲染后的 Markdown；
 * - 右上角「编辑」进入编辑态（默认所见即所得）；
 * - 右上角「高级」收入「源码模式」，仅在用户主动打开高级菜单时切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val boxes by repository.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val allNotes by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLinks by repository.observeAllLinks().collectAsStateWithLifecycle(initialValue = emptyList())

    var loaded by remember(noteId) { mutableStateOf(false) }
    var content by remember(noteId) { mutableStateOf("") }
    var advancedMenuOpen by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("笔记详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (noteRow != null) {
                        TextButton(onClick = { onEdit(EditorMode.WYSIWYG) }) {
                            Text("编辑")
                        }
                        Box {
                            TextButton(onClick = { advancedMenuOpen = true }) {
                                Text("高级")
                            }
                            DropdownMenu(
                                expanded = advancedMenuOpen,
                                onDismissRequest = { advancedMenuOpen = false },
                            ) {
                                DropdownMenuItem(
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
                    if (!boxName.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = boxName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                if (noteTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TagChipRow(tags = noteTags, max = Int.MAX_VALUE)
                }
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
}
