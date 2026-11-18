package com.davidzhu.wantnote.ui.editor

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.BlockFontSize
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.Markup
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteLinkCodec
import com.davidzhu.wantnote.data.NoteMention
import com.davidzhu.wantnote.data.NoteMentionParser
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.MarkdownVisual
import com.davidzhu.wantnote.ui.components.TagChip
import com.davidzhu.wantnote.ui.components.TagColorPicker
import com.davidzhu.wantnote.ui.theme.LocalEditorFontFamily
import com.davidzhu.wantnote.ui.theme.LocalFontScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: String,
    onBack: () -> Unit,
) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val media = LocalMedia.current
    val ai = LocalAi.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val allTags by repository.observeAllTags().collectAsStateWithLifecycle(initialValue = emptyList())
    val editorFont = LocalEditorFontFamily.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var blocks by remember { mutableStateOf(listOf<NoteBlock>()) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var noteTags by remember { mutableStateOf(listOf<Tag>()) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val fields = remember { mutableStateMapOf<String, TextFieldValue>() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

    // Note library for the @ picker, live mention search and link-title
    // resolution. Ordered by updatedAt DESC, so "recent notes" is free.
    val allNotes by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val linkTitles = remember(allNotes) { allNotes.associate { it.id to SearchText.displayTitle(it) } }
    var showLinkPicker by remember { mutableStateOf(false) }
    // Active `@` mention: which block + where the query starts/ends.
    var mention by remember { mutableStateOf<EditorMention?>(null) }

    val titleState = rememberUpdatedState(title)
    val blocksState = rememberUpdatedState(blocks)

    fun requesterOf(id: String): FocusRequester = requesters.getOrPut(id) { FocusRequester() }

    fun fieldOf(block: NoteBlock): TextFieldValue {
        return fields.getOrPut(block.id) { TextFieldValue(block.text, TextRange(block.text.length)) }
    }

    fun updateBlocks(next: List<NoteBlock>) {
        blocks = next.mapIndexed { index, block -> block.copy(sortOrder = index, noteId = noteId) }
    }

    fun focusedIndex(): Int {
        val id = focusedId
        val index = blocks.indexOfFirst { it.id == id }
        return if (index >= 0) index else blocks.lastIndex.coerceAtLeast(0)
    }

    fun insertAfter(index: Int, block: NoteBlock, focus: Boolean = true) {
        val next = blocks.toMutableList()
        val at = (index + 1).coerceIn(0, next.size)
        next.add(at, block)
        updateBlocks(next)
        fields[block.id] = TextFieldValue(block.text, TextRange(block.text.length))
        if (focus) focusedId = block.id
    }

    fun newBlock(
        type: BlockType,
        indent: Int = 0,
        text: String = "",
        mediaPath: String = "",
        fontSize: BlockFontSize = BlockFontSize.NORMAL,
    ): NoteBlock {
        return NoteBlock(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            type = type.name,
            text = text,
            indent = indent.coerceIn(0, 8),
            checked = false,
            fontSize = fontSize.name,
            mediaPath = mediaPath,
            sortOrder = 0,
        )
    }

    fun addTypedBlock(type: BlockType) {
        if (blocks.isEmpty()) {
            val starter = newBlock(type)
            updateBlocks(listOf(starter))
            fields[starter.id] = TextFieldValue("")
            focusedId = starter.id
            return
        }
        val index = focusedIndex()
        val current = blocks[index]
        val convertible = current.blockType == BlockType.PARAGRAPH && current.text.isBlank() && current.mediaPath.isBlank()
        if (convertible && type != BlockType.DIVIDER && type != BlockType.IMAGE && type != BlockType.VIDEO && type != BlockType.FILE) {
            updateBlocks(
                blocks.mapIndexed { i, block ->
                    if (i == index) block.copy(type = type.name) else block
                },
            )
            focusedId = current.id
        } else {
            insertAfter(index, newBlock(type, indent = current.indent, fontSize = current.blockFontSize))
        }
    }

    fun wrapFocused(marker: String) {
        val id = focusedId ?: blocks.getOrNull(focusedIndex())?.id ?: return
        val block = blocks.firstOrNull { it.id == id } ?: return
        if (block.blockType == BlockType.DIVIDER || block.isAttachment) return
        val value = fieldOf(block)
        val (next, lo, hi) = Markup.wrap(value.text, value.selection.min, value.selection.max, marker)
        fields[id] = TextFieldValue(next, TextRange(lo, hi))
        updateBlocks(blocks.map { if (it.id == id) it.copy(text = next) else it })
    }

    /**
     * Toolbar 「@」 picker: insert the chosen note's stable-ID marker at the
     * CURRENT caret of the CURRENT block, replacing the selection if one is
     * active. Media/divider blocks have no caret, so the marker lands in a
     * fresh paragraph right after them; on an empty note it opens block one.
     */
    fun insertNoteLinkAtCaret(targetId: String) {
        val marker = NoteLinkCodec.marker(targetId)
        val index = focusedIndex()
        val current = blocks.getOrNull(index)
        if (current == null) {
            val created = newBlock(BlockType.PARAGRAPH, text = marker)
            updateBlocks(listOf(created))
            fields[created.id] = TextFieldValue(marker, TextRange(marker.length))
            focusedId = created.id
            return
        }
        if (current.isMedia || current.blockType == BlockType.DIVIDER) {
            insertAfter(
                index,
                newBlock(
                    BlockType.PARAGRAPH,
                    indent = current.indent,
                    text = marker,
                    fontSize = current.blockFontSize,
                ),
            )
            return
        }
        val value = fieldOf(current)
        val lo = value.selection.min.coerceIn(0, value.text.length)
        val hi = value.selection.max.coerceIn(lo, value.text.length)
        val next = value.text.substring(0, lo) + marker + value.text.substring(hi)
        fields[current.id] = TextFieldValue(next, TextRange(lo + marker.length))
        updateBlocks(blocks.map { if (it.id == current.id) it.copy(text = next) else it })
        focusedId = current.id
    }

    /**
     * Live mention: replace the typed `@query` (from [mention]) with the
     * chosen note's stable-ID marker, keeping the caret right after it.
     */
    fun applyMention(targetId: String) {
        val active = mention ?: return
        mention = null
        val block = blocks.firstOrNull { it.id == active.blockId } ?: return
        val value = fieldOf(block)
        val m = active.mention
        if (m.start < 0 || m.end > value.text.length || m.start >= m.end) return
        val marker = NoteLinkCodec.marker(targetId)
        val next = value.text.substring(0, m.start) + marker + value.text.substring(m.end)
        fields[block.id] = TextFieldValue(next, TextRange(m.start + marker.length))
        updateBlocks(blocks.map { if (it.id == block.id) it.copy(text = next) else it })
        focusedId = block.id
    }

    fun cycleFontSize() {
        if (blocks.isEmpty()) return
        val index = focusedIndex()
        val current = blocks[index].blockFontSize.next()
        updateBlocks(
            blocks.mapIndexed { i, block ->
                if (i == index) block.copy(fontSize = current.name) else block
            },
        )
    }

    fun changeIndent(delta: Int) {
        if (blocks.isEmpty()) return
        val index = focusedIndex()
        updateBlocks(
            blocks.mapIndexed { i, block ->
                if (i == index) block.copy(indent = (block.indent + delta).coerceIn(0, 8)) else block
            },
        )
    }

    fun setChecked(id: String, checked: Boolean) {
        updateBlocks(blocks.map { if (it.id == id) it.copy(checked = checked) else it })
    }

    fun mergeOrDelete(id: String) {
        val index = blocks.indexOfFirst { it.id == id }
        if (index <= 0) {
            val only = blocks.getOrNull(0) ?: return
            if (only.blockType != BlockType.PARAGRAPH) {
                updateBlocks(listOf(only.copy(type = BlockType.PARAGRAPH.name, checked = false, mediaPath = "", text = "")))
                fields[only.id] = TextFieldValue("")
            }
            return
        }
        val current = blocks[index]
        val previous = blocks[index - 1]
        if (previous.blockType != BlockType.DIVIDER && !previous.isMedia) {
            val mergedText = previous.text + current.text
            val caret = previous.text.length
            updateBlocks(
                blocks.mapIndexedNotNull { i, block ->
                    when (i) {
                        index -> null
                        index - 1 -> block.copy(text = mergedText)
                        else -> block
                    }
                },
            )
            fields[previous.id] = TextFieldValue(mergedText, TextRange(caret))
            fields.remove(current.id)
            focusedId = previous.id
        } else {
            updateBlocks(blocks.filterIndexed { i, _ -> i != index })
            fields.remove(current.id)
            focusedId = previous.id
        }
    }

    fun onTextChanged(block: NoteBlock, incoming: TextFieldValue) {
        val newline = incoming.text.indexOf('\n')
        if (newline >= 0) {
            mention = null
            val left = incoming.text.substring(0, newline)
            val right = incoming.text.substring(newline + 1).replace("\n", "")
            val index = blocks.indexOfFirst { it.id == block.id }
            if (index < 0) return
            if (left.isEmpty() && right.isEmpty() &&
                (block.blockType == BlockType.TASK || block.blockType == BlockType.ORDERED || block.blockType == BlockType.UNORDERED)
            ) {
                fields[block.id] = TextFieldValue("")
                updateBlocks(
                    blocks.mapIndexed { i, item ->
                        if (i == index) item.copy(type = BlockType.PARAGRAPH.name, text = "", checked = false) else item
                    },
                )
                focusedId = block.id
                return
            }
            fields[block.id] = TextFieldValue(left, TextRange(left.length))
            val created = newBlock(
                type = when (block.blockType) {
                    BlockType.IMAGE, BlockType.VIDEO, BlockType.FILE, BlockType.DIVIDER -> BlockType.PARAGRAPH
                    else -> block.blockType
                },
                indent = block.indent,
                text = right,
                fontSize = block.blockFontSize,
            )
            updateBlocks(
                blocks.toMutableList().also { list ->
                    list[index] = block.copy(text = left)
                    list.add(index + 1, created)
                },
            )
            fields[created.id] = TextFieldValue(right, TextRange(right.length))
            focusedId = created.id
        } else {
            fields[block.id] = incoming
            updateBlocks(blocks.map { if (it.id == block.id) it.copy(text = incoming.text) else it })
            // Typing or moving the caret right after an `@` re-derives the
            // live mention; moving out of the query (or deleting the `@`)
            // closes the search popup. This also fires on selection-only
            // changes, which is exactly what we want.
            val active = NoteMentionParser.activeMention(incoming.text, incoming.selection.min)
            mention = active?.let { EditorMention(block.id, it) }
        }
    }

    LaunchedEffect(noteId) {
        val note = repository.getById(noteId)
        if (note != null) {
            repository.ensureMigratedBlocks(note)
            title = note.title
            val loaded = repository.getBlocks(noteId)
            val next = loaded.ifEmpty {
                listOf(NoteBlock.paragraph(UUID.randomUUID().toString(), noteId, "", sortOrder = 0))
            }
            fields.clear()
            next.forEach { block ->
                fields[block.id] = TextFieldValue(block.text, TextRange(block.text.length))
            }
            blocks = next
            noteTags = repository.getTagsForNote(noteId)
            focusedId = next.firstOrNull { !it.isAttachment && it.blockType != BlockType.DIVIDER }?.id ?: next.firstOrNull()?.id
        }
        ready = true
    }

    LaunchedEffect(ready, noteId) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { title to blocks }
            .distinctUntilChanged()
            .debounce(400)
            .collect { (t, b) ->
                repository.save(noteId, t, b)
            }
    }

    LaunchedEffect(focusedId) {
        val id = focusedId ?: return@LaunchedEffect
        kotlinx.coroutines.yield()
        val requester = requesters[id] ?: return@LaunchedEffect
        runCatching { requester.requestFocus() }
    }

    val charCount = remember(blocks) { NoteRepository.bodyCharCount(blocks) }

    /**
     * Live search over the note library for the active `@` mention. Blank
     * query = recent notes (updatedAt DESC); otherwise title OR body match,
     * case-insensitive. The note being edited is never a candidate (self
     * links are meaningless and the repository drops them anyway).
     */
    val mentionCandidates = remember(mention, allNotes) {
        val active = mention ?: return@remember emptyList<Note>()
        val query = active.mention.query.trim()
        val pool = allNotes.filter { it.id != active.blockId }
        if (query.isEmpty()) {
            pool
        } else {
            pool.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                    note.body.contains(query, ignoreCase = true)
            }
        }
    }

    /**
     * 顶栏返回与系统返回共用：先立刻把当前标题和正文同步落盘（不走
     * 400ms debounce，也不依赖这次 composition 的生命周期），再退出。
     * 保存放在 NonCancellable 里执行，即使返回导致 composition 被取消、
     * rememberCoroutineScope 被清掉，这次保存也一定会写完；onBack 在
     * 保存结束后才调用（保存失败也会退出，不让用户卡在编辑器）。
     */
    fun leave() {
        val titleNow = titleState.value
        val blocksNow = blocksState.value
        scope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    repository.save(noteId, titleNow, blocksNow)
                    repository.deleteIfBlank(noteId)
                }
            } finally {
                onBack()
            }
        }
    }

    /**
     * One fill-only AI pass, shared by the 保存 button and the sparkle
     * dialog's 「只补充空位」 choice: applies the suggestion ONLY to fields
     * that are still empty at apply time and reports what was filled.
     */
    suspend fun runFillOnlyPass() {
        val result = ai.suggestFillTitleTags(noteId)
        if (result.isSuccess) {
            val suggestion = result.getOrNull()
            if (suggestion != null) {
                val fill = repository.fillEmptyTitleAndTags(noteId, suggestion.title, suggestion.tags)
                val latest = repository.getById(noteId)
                if (title.isBlank() && fill.titleFilled && latest != null) {
                    title = latest.title
                }
                noteTags = repository.getTagsForNote(noteId)
                snackbar.showSnackbar(
                    when {
                        fill.titleFilled && fill.tagsFilled -> "已保存，AI 补充了标题和标签"
                        fill.titleFilled -> "已保存，AI 补充了标题"
                        fill.tagsFilled -> "已保存，AI 补充了标签"
                        else -> "已保存"
                    },
                )
            } else {
                snackbar.showSnackbar("已保存")
            }
        } else {
            val error = result.exceptionOrNull()
            val msg = error?.message.orEmpty()
            when {
                msg.contains("请先在设置里") || msg.contains("还没有配置") || msg.contains("空笔记") -> {
                    snackbar.showSnackbar("已保存")
                }
                msg.contains("已有内容") -> snackbar.showSnackbar("已保存")
                else -> snackbar.showSnackbar("已保存。自动标题/标签未完成：${msg.ifBlank { "已保留当前内容" }}")
            }
        }
    }

    /** 保存 + fill-only AI pass (never overwrites manual content). */
    fun saveExplicit() {
        if (saving) return
        saving = true
        scope.launch {
            repository.save(noteId, titleState.value, blocksState.value)
            // AI runs as a fill-only pass: each field is filled ONLY if it is
            // still empty at apply time (checked inside one transaction), so
            // user-written titles/tags — including changes made while the AI
            // request was in flight — are never overwritten or clobbered.
            runFillOnlyPass()
            saving = false
        }
    }

    fun runAiGeneration(overwrite: Boolean) {
        if (generating) return
        generating = true
        scope.launch {
            repository.save(noteId, titleState.value, blocksState.value)
            if (overwrite) {
                // Reached only via the confirmation dialog; replaces the
                // stored title and tags with the AI result, as the user chose.
                ai.runOverwriteTitleTags(noteId)
                    .onSuccess { suggestion ->
                        if (suggestion.title.isNotBlank()) {
                            title = suggestion.title.take(40)
                        }
                        noteTags = repository.getTagsForNote(noteId)
                        snackbar.showSnackbar("已覆盖标题和标签")
                    }
                    .onFailure { error ->
                        snackbar.showSnackbar(error.message ?: "生成失败，已保留当前内容")
                    }
            } else {
                runFillOnlyPass()
            }
            generating = false
        }
    }

    /**
     * Sparkle button: if the note already has a manual title or tags, ask the
     * user first — nothing is overwritten without an explicit confirmed
     * choice. Only a completely blank title/tag note regenerates directly.
     */
    fun generateTitleTags() {
        if (generating) return
        scope.launch {
            val note = repository.getById(noteId)
            val hasExisting = note != null &&
                (note.title.isNotBlank() || repository.getTagsForNote(noteId).isNotEmpty())
            if (hasExisting) {
                showOverwriteDialog = true
            } else {
                runAiGeneration(overwrite = false)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { media.importImage(uri, settings.compressImages) }
            }.onSuccess { path ->
                val index = focusedIndex()
                val indent = blocks.getOrNull(index)?.indent ?: 0
                insertAfter(index, newBlock(BlockType.IMAGE, indent = indent, mediaPath = path))
                insertAfter(focusedIndex(), newBlock(BlockType.PARAGRAPH, indent = indent), focus = true)
            }.onFailure {
                snackbar.showSnackbar("无法插入图片")
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { media.importVideo(uri, settings.compressVideos) }
            }.onSuccess { path ->
                val index = focusedIndex()
                val indent = blocks.getOrNull(index)?.indent ?: 0
                insertAfter(index, newBlock(BlockType.VIDEO, indent = indent, mediaPath = path))
                insertAfter(focusedIndex(), newBlock(BlockType.PARAGRAPH, indent = indent), focus = true)
            }.onFailure {
                snackbar.showSnackbar("无法插入视频")
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { media.importFile(uri) }
            }.onSuccess { path ->
                val index = focusedIndex()
                val indent = blocks.getOrNull(index)?.indent ?: 0
                val name = path.substringAfterLast('/')
                insertAfter(index, newBlock(BlockType.FILE, indent = indent, text = name, mediaPath = path))
                insertAfter(focusedIndex(), newBlock(BlockType.PARAGRAPH, indent = indent), focus = true)
            }.onFailure {
                snackbar.showSnackbar("无法插入文件")
            }
        }
    }

    BackHandler { leave() }

    val currentFontLabel = blocks.getOrNull(focusedIndex())?.blockFontSize?.label ?: BlockFontSize.NORMAL.label

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("$charCount 字") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { saveExplicit() }, enabled = !saving) {
                        Text("保存")
                    }
                    IconButton(onClick = { generateTitleTags() }, enabled = !generating) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "重新生成标题和标签")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            OutlineToolbar(
                fontLabel = currentFontLabel,
                onTags = { showTagPicker = true },
                onAt = { showLinkPicker = true },
                onAddTask = { addTypedBlock(BlockType.TASK) },
                onOrdered = { addTypedBlock(BlockType.ORDERED) },
                onUnordered = { addTypedBlock(BlockType.UNORDERED) },
                onDivider = { addTypedBlock(BlockType.DIVIDER) },
                onFontSize = { cycleFontSize() },
                onBold = { wrapFocused("**") },
                onItalic = { wrapFocused("*") },
                onStrike = { wrapFocused("~~") },
                onHighlight = { wrapFocused("==") },
                onIndent = { changeIndent(1) },
                onOutdent = { changeIndent(-1) },
                onImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onVideo = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onFile = {
                    filePicker.launch(arrayOf("*/*"))
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = editorFont,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (title.isEmpty()) {
                            Text(
                                "标题（可选）",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        inner()
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
                EditorTagBar(
                    tags = noteTags,
                    onPick = { showTagPicker = true },
                    onRemove = { tag ->
                        scope.launch {
                            repository.removeTagFromNote(noteId, tag.id)
                            noteTags = repository.getTagsForNote(noteId)
                        }
                    },
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                        OutlineBlockRow(
                            block = block,
                            number = orderedNumber(blocks, index),
                            value = fieldOf(block),
                            focusRequester = requesterOf(block.id),
                            showPlaceholder = index == 0 && blocks.size == 1 && block.text.isEmpty() &&
                                block.blockType == BlockType.PARAGRAPH,
                            linkTitles = linkTitles,
                            onFocus = {
                                focusedId = block.id
                                if (mention?.blockId != block.id) mention = null
                            },
                            onValueChange = { onTextChanged(block, it) },
                            onCheckedChange = { setChecked(block.id, it) },
                            onBackspaceEmpty = { mergeOrDelete(block.id) },
                            onOpenVideo = { path ->
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
                            },
                        )
                    }
                }
            }
            // Live `@` mention search, docked just above the toolbar.
            mention?.let { active ->
                MentionListPopup(
                    query = active.mention.query.trim(),
                    candidates = mentionCandidates,
                    highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    onPick = { targetId -> applyMention(targetId) },
                    onDismiss = { mention = null },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showLinkPicker) {
        NoteLinkPickerDialog(
            notes = allNotes,
            currentNoteId = noteId,
            highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            onPick = { targetId ->
                showLinkPicker = false
                insertNoteLinkAtCaret(targetId)
            },
            onDismiss = { showLinkPicker = false },
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            allTags = allTags,
            selectedIds = noteTags.map { it.id }.toSet(),
            onToggle = { tag ->
                scope.launch {
                    if (tag.id in noteTags.map { it.id }.toSet()) {
                        repository.removeTagFromNote(noteId, tag.id)
                    } else {
                        repository.addTagToNote(noteId, tag.path, tag.color)
                    }
                    noteTags = repository.getTagsForNote(noteId)
                }
            },
            onCreate = { path, color ->
                scope.launch {
                    val created = repository.addTagToNote(noteId, path, color)
                    noteTags = repository.getTagsForNote(noteId)
                    if (created == null) {
                        snackbar.showSnackbar("请填写有效的标签路径")
                    } else {
                        snackbar.showSnackbar("已创建并附加：${created.path}")
                    }
                }
            },
            onDismiss = { showTagPicker = false },
        )
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("重新生成标题和标签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "这篇笔记已有标题或标签。选择应用方式：",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        showOverwriteDialog = false
                        runAiGeneration(overwrite = false)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("只补充空位（不动已有内容）")
                    }
                    TextButton(onClick = {
                        showOverwriteDialog = false
                        runAiGeneration(overwrite = true)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("覆盖标题和标签", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showOverwriteDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("取消")
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/**
 * Manual tag add/select dialog, opened from the editor toolbar # button
 * (and from 选择标签). Existing tags can be attached/detached by tapping;
 * a new tag can be typed (slash paths allowed) and created on the fly with
 * a color, then attached to the current note.
 */
@Composable
private fun TagPickerDialog(
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
    val filtered = if (query.isBlank()) allTags else allTags.filter { it.path.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索或输入新标签") },
                    placeholder = { Text("例如 工作/项目") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canCreate) {
                    Text(
                        text = "新标签：$newPath",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TagColorPicker(
                            selected = draftColor,
                            onSelect = { draftColor = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
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
                if (allTags.isEmpty()) {
                    Text(
                        "还没有标签。在上方输入名称即可创建并附加到本笔记。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (filtered.isEmpty()) {
                    Text(
                        "没有匹配「${query.trim()}」的标签，可以新建。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(filtered.size) { index ->
                            val tag = filtered[index]
                            val on = tag.id in selectedIds
                            FilterChip(
                                selected = on,
                                onClick = { onToggle(tag) },
                                label = { Text(tag.path) },
                                modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}


internal fun guessedMime(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "3gp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}

internal fun orderedNumber(blocks: List<NoteBlock>, index: Int): Int {
    val current = blocks.getOrNull(index) ?: return 0
    if (current.blockType != BlockType.ORDERED) return 0
    var count = 1
    var cursor = index - 1
    while (cursor >= 0) {
        val other = blocks[cursor]
        if (other.indent < current.indent) break
        if (other.indent == current.indent) {
            if (other.blockType != BlockType.ORDERED) break
            count++
        }
        cursor--
    }
    return count
}

@Composable
private fun OutlineBlockRow(
    block: NoteBlock,
    number: Int,
    value: TextFieldValue,
    focusRequester: FocusRequester,
    showPlaceholder: Boolean,
    linkTitles: Map<String, String>,
    onFocus: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onBackspaceEmpty: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val scale = LocalFontScale.current
    val fontFamily = LocalEditorFontFamily.current
    val fontSize = (block.blockFontSize.sp * scale).sp
    // Render inline Markdown (note links, emphasis, code, links) instead of
    // the raw source, while the stored text stays exactly as typed. The
    // transformation also fixes up caret mapping, so editing inside a
    // collapsed `[[note:id]]` marker behaves correctly.
    val linkColor = MaterialTheme.colorScheme.primary
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val inlineTransformation = remember(linkTitles, linkColor, highlightBg) {
        MarkdownVisual.transformation(
            highlightColor = highlightBg,
            linkTitles = linkTitles,
            linkColor = linkColor,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (block.indent * 20).dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (block.blockType) {
            BlockType.TASK -> {
                Checkbox(
                    checked = block.checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.size(32.dp),
                )
            }
            BlockType.UNORDERED -> {
                Text(
                    "•",
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            BlockType.ORDERED -> {
                Text(
                    "$number.",
                    modifier = Modifier
                        .widthIn(min = 24.dp)
                        .padding(top = 8.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> Unit
        }

        when (block.blockType) {
            BlockType.DIVIDER -> {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            BlockType.IMAGE -> {
                LocalImageBlock(path = block.mediaPath, modifier = Modifier.fillMaxWidth())
            }
            BlockType.VIDEO -> {
                LocalVideoBlock(path = block.mediaPath, onOpen = { onOpenVideo(block.mediaPath) })
            }
            BlockType.FILE -> {
                LocalFileBlock(path = block.mediaPath, label = block.text, onOpen = { onOpenVideo(block.mediaPath) })
            }
            else -> {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        lineHeight = (block.blockFontSize.sp * scale * 1.5f).sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    visualTransformation = inlineTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) onFocus() }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                value.text.isEmpty()
                            ) {
                                onBackspaceEmpty()
                                true
                            } else {
                                false
                            }
                        },
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) {
                                Text(
                                    if (showPlaceholder) "写点什么…不必先起标题" else "",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = fontSize,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalImageBlock(path: String, modifier: Modifier = Modifier) {
    val media = LocalMedia.current
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { media.decodePreview(path) }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "图片",
            modifier = modifier
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Text(
            "图片无法显示",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun LocalVideoBlock(path: String, onOpen: () -> Unit) {
    val media = LocalMedia.current
    val file = media.fileFor(path)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Videocam, contentDescription = null)
            Column {
                Text(if (file.exists()) file.name else "本地视频", style = MaterialTheme.typography.titleSmall)
                Text("点击用系统播放器打开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OutlineToolbar(
    fontLabel: String,
    onTags: () -> Unit,
    onAt: () -> Unit,
    onAddTask: () -> Unit,
    onOrdered: () -> Unit,
    onUnordered: () -> Unit,
    onDivider: () -> Unit,
    onFontSize: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onHighlight: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit,
    onFile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("标签", Icons.Outlined.Tag, onTags)
        ToolButton("@", Icons.Outlined.AlternateEmail, onAt)
        ToolButton("任务", Icons.Outlined.CheckBox, onAddTask)
        ToolButton("有序", Icons.Outlined.FormatListNumbered, onOrdered)
        ToolButton("无序", Icons.AutoMirrored.Outlined.FormatListBulleted, onUnordered)
        ToolButton("分割线", Icons.Outlined.HorizontalRule, onDivider)
        ToolButton("字号$fontLabel", Icons.Outlined.FormatSize, onFontSize)
        ToolButton("粗", Icons.Outlined.FormatBold, onBold)
        ToolButton("斜", Icons.Outlined.FormatItalic, onItalic)
        ToolButton("删除线", Icons.Outlined.StrikethroughS, onStrike)
        ToolButton("高亮", Icons.Outlined.Highlight, onHighlight)
        ToolButton("缩进", Icons.AutoMirrored.Outlined.FormatIndentIncrease, onIndent)
        ToolButton("反缩进", Icons.AutoMirrored.Outlined.FormatIndentDecrease, onOutdent)
        ToolButton("图片", Icons.Outlined.Image, onImage)
        ToolButton("视频", Icons.Outlined.Videocam, onVideo)
        ToolButton("文件", Icons.Outlined.AttachFile, onFile)
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 0.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorTagBar(
    tags: List<Tag>,
    onPick: () -> Unit,
    onRemove: (Tag) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            tags.forEach { tag ->
                TagChip(tag = tag, onRemove = { onRemove(tag) })
            }
            TextButton(onClick = onPick) {
                Text("选择标签")
            }
        }
    }
}

@Composable
private fun LocalFileBlock(path: String, label: String, onOpen: () -> Unit) {
    val media = LocalMedia.current
    val file = media.fileFor(path)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null)
            Column {
                Text(label.ifBlank { if (file.exists()) file.name else "本地文件" }, style = MaterialTheme.typography.titleSmall)
                Text("点击用系统应用打开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A live `@` mention tied to the block that contains it. */
private data class EditorMention(
    val blockId: String,
    val mention: NoteMention,
)

/** Title text with the first [query] match highlighted, if any. */
private fun highlightedTitle(title: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(title)
    val index = title.indexOf(query, ignoreCase = true)
    if (index < 0) return AnnotatedString(title)
    return buildAnnotatedString {
        append(title)
        addStyle(SpanStyle(background = highlightColor), index, index + query.length)
    }
}

/**
 * Live search panel for a typed `@`: docked just above the editor toolbar,
 * listing matching notes with the query highlighted in each title. Picking
 * one replaces the `@query` text with a stable-ID link marker at the caret.
 */
@Composable
private fun MentionListPopup(
    query: String,
    candidates: List<Note>,
    highlightColor: Color,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 232.dp),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (query.isEmpty()) "@" else "@$query",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            if (candidates.isEmpty()) {
                Text(
                    text = if (query.isEmpty()) "还没有其他笔记可以链接" else "没有匹配「$query」的笔记",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(candidates) { _, note ->
                        NoteCandidateRow(
                            note = note,
                            query = query,
                            highlightColor = highlightColor,
                            onClick = { onPick(note.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCandidateRow(
    note: Note,
    query: String,
    highlightColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedTitle(SearchText.displayTitle(note), query, highlightColor),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = Dates.formatTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Toolbar 「@」 picker: recent notes first (updatedAt DESC), searchable by
 * title or body. Choosing a note inserts its stable-ID link marker at the
 * CURRENT caret of the CURRENT block.
 */
@Composable
private fun NoteLinkPickerDialog(
    notes: List<Note>,
    currentNoteId: String,
    highlightColor: Color,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    val candidates = remember(notes, trimmed, currentNoteId) {
        val pool = notes.filter { it.id != currentNoteId }
        if (trimmed.isEmpty()) {
            pool
        } else {
            pool.filter { it.title.contains(trimmed, ignoreCase = true) || it.body.contains(trimmed, ignoreCase = true) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@ 链接到笔记") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索笔记") },
                    placeholder = { Text("留空显示最近笔记") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Text(
                        text = if (trimmed.isEmpty()) "还没有其他笔记可以链接" else "没有匹配「$trimmed」的笔记",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(candidates) { _, note ->
                            NoteCandidateRow(
                                note = note,
                                query = trimmed,
                                highlightColor = highlightColor,
                                onClick = { onPick(note.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
