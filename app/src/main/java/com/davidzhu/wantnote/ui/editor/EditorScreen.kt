package com.davidzhu.wantnote.ui.editor

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.BlockFontSize
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.Markup
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.TagChip
import com.davidzhu.wantnote.ui.components.TagColorPicker
import com.davidzhu.wantnote.ui.theme.LocalFontScale
import kotlinx.coroutines.Dispatchers
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var blocks by remember { mutableStateOf(listOf<NoteBlock>()) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var noteTags by remember { mutableStateOf(listOf<Tag>()) }
    var newTagPath by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf(Tag.defaultColor()) }
    var generating by remember { mutableStateOf(false) }
    val fields = remember { mutableStateMapOf<String, TextFieldValue>() }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }

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
        if (convertible && type != BlockType.DIVIDER && type != BlockType.IMAGE && type != BlockType.VIDEO) {
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
        if (block.blockType == BlockType.DIVIDER || block.isMedia) return
        val value = fieldOf(block)
        val (next, lo, hi) = Markup.wrap(value.text, value.selection.min, value.selection.max, marker)
        fields[id] = TextFieldValue(next, TextRange(lo, hi))
        updateBlocks(blocks.map { if (it.id == id) it.copy(text = next) else it })
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
                    BlockType.IMAGE, BlockType.VIDEO, BlockType.DIVIDER -> BlockType.PARAGRAPH
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
            focusedId = next.firstOrNull { !it.isMedia && it.blockType != BlockType.DIVIDER }?.id ?: next.firstOrNull()?.id
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

    fun leave() {
        scope.launch {
            repository.save(noteId, titleState.value, blocksState.value)
            val removed = repository.deleteIfBlank(noteId)
            if (!removed) {
                ai.enqueueAutoTitleTags(noteId, force = false)
            }
            onBack()
        }
    }

    fun generateTitleTags() {
        if (generating) return
        generating = true
        scope.launch {
            repository.save(noteId, titleState.value, blocksState.value)
            val result = ai.runAutoTitleTags(noteId, force = true)
            result.onSuccess { suggestion ->
                if (suggestion.title.isNotBlank()) {
                    title = suggestion.title
                }
                noteTags = repository.getTagsForNote(noteId)
                snackbar.showSnackbar("已生成标题和标签")
            }.onFailure { error ->
                snackbar.showSnackbar(error.message ?: "生成失败，已保留当前内容")
            }
            generating = false
        }
    }

    suspend fun addTagFromInput() {
        val path = Tag.normalizePath(newTagPath)
        if (path.isEmpty()) return
        val tag = repository.addTagToNote(noteId, path, newTagColor) ?: return
        noteTags = repository.getTagsForNote(noteId)
        newTagPath = ""
        if (tag.path == path) {
            // keep the selected color for the next tag
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
                withContext(Dispatchers.IO) { media.importVideo(uri) }
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
                    IconButton(onClick = { generateTitleTags() }, enabled = !generating) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "生成标题和标签")
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground),
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
                newPath = newTagPath,
                newColor = newTagColor,
                onPathChange = { newTagPath = it },
                onColorChange = { newTagColor = it },
                onAdd = { scope.launch { addTagFromInput() } },
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
                        onFocus = { focusedId = block.id },
                        onValueChange = { onTextChanged(block, it) },
                        onCheckedChange = { setChecked(block.id, it) },
                        onBackspaceEmpty = { mergeOrDelete(block.id) },
                        onOpenVideo = { path ->
                            val file = media.fileFor(path)
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "打开视频"))
                            }.onFailure {
                                scope.launch { snackbar.showSnackbar("无法打开视频") }
                            }
                        },
                    )
                }
            }
        }
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
    onFocus: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onBackspaceEmpty: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val scale = LocalFontScale.current
    val fontSize = (block.blockFontSize.sp * scale).sp
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
            else -> {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = fontSize,
                        lineHeight = (block.blockFontSize.sp * scale * 1.5f).sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    newPath: String,
    newColor: Long,
    onPathChange: (String) -> Unit,
    onColorChange: (Long) -> Unit,
    onAdd: () -> Unit,
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
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newPath,
                onValueChange = onPathChange,
                modifier = Modifier.weight(1f),
                label = { Text("添加标签（可用斜杠表示层级）") },
                placeholder = { Text("工作/项目") },
                singleLine = true,
            )
            TextButton(onClick = onAdd, enabled = Tag.normalizePath(newPath).isNotEmpty()) {
                Text("添加")
            }
        }
        TagColorPicker(
            selected = newColor,
            onSelect = onColorChange,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
    }
}
