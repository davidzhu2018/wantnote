package com.davidzhu.wantnote.ui.editor

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Videocam
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantCheckbox
import com.davidzhu.wantnote.ui.components.WantDropdownMenu
import com.davidzhu.wantnote.ui.components.WantDropdownMenuItem
import com.davidzhu.wantnote.ui.components.WantFilterChip
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextField
import com.davidzhu.wantnote.ui.components.WantScaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.davidzhu.wantnote.ui.components.WantSurface
import top.yukonga.miuix.kmp.basic.Text
import com.davidzhu.wantnote.ui.components.WantTextButton
import com.davidzhu.wantnote.ui.components.WantTopBar
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.davidzhu.wantnote.data.EditorMode
import com.davidzhu.wantnote.data.Markup
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.data.NoteLinkCodec
import com.davidzhu.wantnote.data.NoteMention
import com.davidzhu.wantnote.data.NoteMentionParser
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.Tag
import com.davidzhu.wantnote.transfer.MarkdownBlock
import com.davidzhu.wantnote.transfer.MarkdownCodec
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalMedia
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import com.davidzhu.wantnote.ui.components.MarkdownPreview
import com.davidzhu.wantnote.ui.components.MarkdownVisual
import com.davidzhu.wantnote.ui.components.NoteImage
import com.davidzhu.wantnote.ui.components.TagChip
import com.davidzhu.wantnote.ui.components.TagColorPicker
import com.davidzhu.wantnote.ui.theme.LocalEditorFontFamily
import com.davidzhu.wantnote.ui.theme.LocalFontScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.davidzhu.wantnote.ui.components.BoxPickerDialog
import com.davidzhu.wantnote.ui.components.TagAddDialog
import com.davidzhu.wantnote.data.NoteBoxActions
import com.davidzhu.wantnote.data.BoxCreateResult

private val editorCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun EditorScreen(
    noteId: String,
    initialMode: EditorMode = EditorMode.WYSIWYG,
    onBack: (deletedBlank: Boolean) -> Unit,
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var blocks by remember { mutableStateOf(listOf<NoteBlock>()) }
    // 0.11.0 N16-N17：编辑默认所见即所得；源码模式收进右上角「高级」按钮，不再作为默认或主切换。
    var mode by remember(noteId, initialMode) { mutableStateOf(initialMode) }
    var showAdvancedMenu by remember { mutableStateOf(false) }
    var sourceText by remember { mutableStateOf(TextFieldValue("")) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var noteTags by remember { mutableStateOf(listOf<Tag>()) }
    var showTagPicker by remember { mutableStateOf(false) }
    // 0.10.0 卡片盒：本会话选择的卡片盒 ID（"" = 未分配）。手动选择后立即
    // 落库；未手动选择时点「保存」由 AI 自动分配到一个已存在的卡片盒。
    val boxes by repository.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val noteRow by repository.observeById(noteId).collectAsStateWithLifecycle(initialValue = null)
    var showBoxPicker by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
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
    val sourceState = rememberUpdatedState(sourceText)
    val modeState = rememberUpdatedState(mode)

    fun requesterOf(id: String): FocusRequester = requesters.getOrPut(id) { FocusRequester() }

    fun fieldOf(block: NoteBlock): TextFieldValue {
        return fields.getOrPut(block.id) { TextFieldValue(block.text, TextRange(block.text.length)) }
    }

    fun updateBlocks(next: List<NoteBlock>) {
        blocks = next.mapIndexed { index, block -> block.copy(sortOrder = index, noteId = noteId) }
    }

    /** 块列表 → 内容 Markdown（不含标题 H1；表格/代码围栏原样还原）。 */
    fun blocksToMarkdown(blockList: List<NoteBlock>): String {
        return MarkdownCodec.render(
            title = "",
            blocks = blockList.map { block ->
                MarkdownBlock(
                    type = block.blockType,
                    text = block.text,
                    indent = block.indent,
                    checked = block.checked,
                    mediaPath = block.mediaPath,
                    headingLevel = block.headingLevel,
                )
            },
        ).trimEnd('\n')
    }

    /** 当前编辑面上的正文 Markdown（两种模式共用，保存/防抖都走这里）。 */
    fun currentContentMarkdown(): String {
        return if (modeState.value == EditorMode.SOURCE) {
            sourceState.value.text.replace("\r\n", "\n")
        } else {
            blocksToMarkdown(blocksState.value)
        }
    }

    fun focusedIndex(): Int {
        val id = focusedId
        val index = blocks.indexOfFirst { it.id == id }
        return if (index >= 0) index else blocks.lastIndex.coerceAtLeast(0)
    }

    /** 重建 WYSIWYG 的字段缓存（模式切换 / 初次载入用）。 */
    fun syncFieldsFromBlocks() {
        fields.clear()
        blocks.forEach { block ->
            fields[block.id] = TextFieldValue(block.text, TextRange(block.text.length))
        }
    }

    /**
     * 切换编辑模式：两种表示互转都走「内容 Markdown」，所以切换零损耗，
     * 也不会产生额外保存。仅在当前会话切换（下次进入编辑仍默认所见即所得）。
     */
    fun switchMode(target: EditorMode) {
        if (target == mode) return
        if (target == EditorMode.SOURCE) {
            val content = blocksToMarkdown(blocks)
            sourceText = TextFieldValue(content, TextRange(content.length))
        } else {
            val content = sourceText.text
            scope.launch {
                blocks = repository.deriveBlocks(noteId, content).ifEmpty {
                    listOf(
                        NoteBlock.paragraph(
                            id = UUID.randomUUID().toString(),
                            noteId = noteId,
                            text = "",
                            sortOrder = 0,
                        ),
                    )
                }
                syncFieldsFromBlocks()
                focusedId = blocks.firstOrNull { !it.isAttachment && it.blockType != BlockType.DIVIDER }?.id
                    ?: blocks.firstOrNull()?.id
            }
        }
        mention = null
        mode = target
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
        headingLevel: Int = 0,
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
            headingLevel = when {
                headingLevel in 1..6 -> headingLevel
                type == BlockType.HEADING -> 1
                else -> 0
            },
        )
    }

    fun addTypedBlock(type: BlockType) {
        if (type == BlockType.TABLE) {
            val index = focusedIndex()
            val current = blocks.getOrNull(index)
            val table = newBlock(
                BlockType.TABLE,
                indent = current?.indent ?: 0,
                text = MarkdownCodec.tableTemplate(indent = current?.indent ?: 0),
            )
            if (blocks.isEmpty()) {
                updateBlocks(listOf(table))
            } else {
                insertAfter(index, table)
            }
            fields[table.id] = TextFieldValue(table.text, TextRange(table.text.length))
            focusedId = table.id
            return
        }
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

    /** 0.16.5：多级标题设置（0=正文，1..6=H1..H6），同时支持所见即所得与源码模式。 */
    fun setHeadingLevel(level: Int) {
        val target = level.coerceIn(0, 6)
        if (modeState.value == EditorMode.SOURCE) {
            val value = sourceText
            val text = value.text
            val caret = value.selection.min.coerceIn(0, text.length)
            val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', caret).let { if (it < 0) text.length else it }
            val currentLine = text.substring(lineStart, lineEnd)
            val stripped = currentLine.replace(Regex("""^ {0,3}#{1,6}[ \t]+"""), "")
            val prefix = if (target == 0) "" else "#".repeat(target) + " "
            val replacedLine = prefix + stripped
            val next = text.substring(0, lineStart) + replacedLine + text.substring(lineEnd)
            val newCaret = (lineStart + replacedLine.length).coerceIn(0, next.length)
            sourceText = TextFieldValue(next, TextRange(newCaret))
            return
        }
        if (blocks.isEmpty()) {
            val created = if (target == 0) {
                newBlock(BlockType.PARAGRAPH)
            } else {
                newBlock(BlockType.HEADING, headingLevel = target)
            }
            updateBlocks(listOf(created))
            fields[created.id] = TextFieldValue("")
            focusedId = created.id
            return
        }
        val index = focusedIndex()
        val current = blocks[index]
        updateBlocks(
            blocks.mapIndexed { i, block ->
                if (i == index) {
                    if (target == 0) {
                        block.copy(type = BlockType.PARAGRAPH.name, headingLevel = 0)
                    } else {
                        block.copy(type = BlockType.HEADING.name, headingLevel = target)
                    }
                } else {
                    block
                }
            },
        )
        fields[current.id] = TextFieldValue(fields[current.id]?.text.orEmpty(), fields[current.id]?.selection ?: TextRange.Zero)
        focusedId = current.id
    }

    /** 0.16.5：引用工具（对应 Markdown `> ` 语法），同时支持所见即所得与源码模式。 */
    fun insertQuote() {
        if (modeState.value == EditorMode.SOURCE) {
            val value = sourceText
            val text = value.text
            val caret = value.selection.min.coerceIn(0, text.length)
            val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', caret).let { if (it < 0) text.length else it }
            val currentLine = text.substring(lineStart, lineEnd)
            val replacedLine = if (currentLine.trimStart().startsWith(">")) {
                currentLine.replaceFirst(Regex("""^([ \t]*)>[ \t]?"""), "$1")
            } else {
                "> $currentLine"
            }
            val next = text.substring(0, lineStart) + replacedLine + text.substring(lineEnd)
            val newCaret = (lineStart + replacedLine.length).coerceIn(0, next.length)
            sourceText = TextFieldValue(next, TextRange(newCaret))
            return
        }
        val index = focusedIndex()
        val current = blocks.getOrNull(index)
        if (current != null && current.blockType == BlockType.QUOTE) {
            updateBlocks(
                blocks.mapIndexed { i, block ->
                    if (i == index) block.copy(type = BlockType.PARAGRAPH.name) else block
                },
            )
            focusedId = current.id
        } else {
            addTypedBlock(BlockType.QUOTE)
        }
    }

    /**
     * 行内标记包裹（粗体/斜体/删除线/高亮）。源码模式直接在整篇文本上
     * 操作；所见即所得模式作用于聚焦块的文本。
     */
    fun wrapFocused(marker: String) {
        if (modeState.value == EditorMode.SOURCE) {
            val value = sourceText
            val (next, lo, hi) = Markup.wrap(value.text, value.selection.min, value.selection.max, marker)
            sourceText = TextFieldValue(next, TextRange(lo, hi))
            return
        }
        val id = focusedId ?: blocks.getOrNull(focusedIndex())?.id ?: return
        val block = blocks.firstOrNull { it.id == id } ?: return
        if (block.blockType == BlockType.DIVIDER || block.isAttachment || block.blockType == BlockType.TABLE) return
        val value = fieldOf(block)
        val (next, lo, hi) = Markup.wrap(value.text, value.selection.min, value.selection.max, marker)
        fields[id] = TextFieldValue(next, TextRange(lo, hi))
        updateBlocks(blocks.map { if (it.id == id) it.copy(text = next) else it })
    }

    /**
     * Toolbar 「@」 picker: insert the chosen note's stable-ID marker at the
     * CURRENT caret of the current editing surface (source text or focused
     * block). Media/divider blocks have no caret, so the marker lands in a
     * fresh paragraph right after them; on an empty note it opens block one.
     */
    fun insertNoteLinkAtCaret(targetId: String) {
        val marker = NoteLinkCodec.marker(targetId)
        if (modeState.value == EditorMode.SOURCE) {
            val value = sourceText
            val lo = value.selection.min.coerceIn(0, value.text.length)
            val hi = value.selection.max.coerceIn(lo, value.text.length)
            val next = value.text.substring(0, lo) + marker + value.text.substring(hi)
            sourceText = TextFieldValue(next, TextRange(lo + marker.length))
            return
        }
        val index = focusedIndex()
        val current = blocks.getOrNull(index)
        if (current == null) {
            val created = newBlock(BlockType.PARAGRAPH, text = marker)
            updateBlocks(listOf(created))
            fields[created.id] = TextFieldValue(marker, TextRange(marker.length))
            focusedId = created.id
            return
        }
        if (current.isMedia || current.blockType == BlockType.DIVIDER || current.blockType == BlockType.TABLE) {
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
        val marker = NoteLinkCodec.marker(targetId)
        if (modeState.value == EditorMode.SOURCE) {
            val value = sourceText
            val m = active.mention
            if (m.start < 0 || m.end > value.text.length || m.start >= m.end) return
            val next = value.text.substring(0, m.start) + marker + value.text.substring(m.end)
            sourceText = TextFieldValue(next, TextRange(m.start + marker.length))
            return
        }
        val block = blocks.firstOrNull { it.id == active.blockId } ?: return
        val value = fieldOf(block)
        val m = active.mention
        if (m.start < 0 || m.end > value.text.length || m.start >= m.end) return
        val next = value.text.substring(0, m.start) + marker + value.text.substring(m.end)
        fields[block.id] = TextFieldValue(next, TextRange(m.start + marker.length))
        updateBlocks(blocks.map { if (it.id == block.id) it.copy(text = next) else it })
        focusedId = block.id
    }

    /** 在当前编辑面（源码 / 聚焦块）的插入点写入一段文本。 */
    fun insertTextAtCaret(text: String) {
        if (modeState.value == EditorMode.SOURCE) {
            val value = sourceText
            val lo = value.selection.min.coerceIn(0, value.text.length)
            val hi = value.selection.max.coerceIn(lo, value.text.length)
            val next = value.text.substring(0, lo) + text + value.text.substring(hi)
            sourceText = TextFieldValue(next, TextRange(lo + text.length))
            return
        }
        val index = focusedIndex()
        val current = blocks.getOrNull(index)
        if (current == null || current.isMedia || current.blockType == BlockType.DIVIDER ||
            current.blockType == BlockType.TABLE
        ) {
            val created = newBlock(
                BlockType.PARAGRAPH,
                indent = current?.indent ?: 0,
                text = text,
                fontSize = current?.blockFontSize ?: BlockFontSize.NORMAL,
            )
            if (blocks.isEmpty()) {
                updateBlocks(listOf(created))
            } else {
                insertAfter(index, created, focus = false)
            }
            fields[created.id] = TextFieldValue(text, TextRange(text.length))
            focusedId = created.id
            return
        }
        val value = fieldOf(current)
        val lo = value.selection.min.coerceIn(0, value.text.length)
        val hi = value.selection.max.coerceIn(lo, value.text.length)
        val next = value.text.substring(0, lo) + text + value.text.substring(hi)
        fields[current.id] = TextFieldValue(next, TextRange(lo + text.length))
        updateBlocks(blocks.map { if (it.id == current.id) it.copy(text = next) else it })
        focusedId = current.id
    }

    fun cycleFontSize() {
        if (modeState.value == EditorMode.SOURCE) return
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
        if (modeState.value == EditorMode.SOURCE) return
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
                updateBlocks(listOf(only.copy(type = BlockType.PARAGRAPH.name, checked = false, mediaPath = "", text = "", headingLevel = 0)))
                fields[only.id] = TextFieldValue("")
            }
            return
        }
        val current = blocks[index]
        val previous = blocks[index - 1]
        if (previous.blockType != BlockType.DIVIDER && !previous.isMedia && previous.blockType != BlockType.TABLE) {
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
        // 表格块的原始竖线行里允许换行，不拆块。
        if (block.blockType == BlockType.TABLE) {
            fields[block.id] = incoming
            updateBlocks(blocks.map { if (it.id == block.id) it.copy(text = incoming.text) else it })
            return
        }
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
                headingLevel = block.headingLevel,
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

    /** 源码模式的文本变化：更新整篇 Markdown + 活动 @ 提及。 */
    fun onSourceChanged(incoming: TextFieldValue) {
        sourceText = incoming
        val active = NoteMentionParser.activeMention(incoming.text, incoming.selection.min)
        mention = active?.let { EditorMention(SOURCE_MENTION_ANCHOR, it) }
    }

    LaunchedEffect(noteId, initialMode) {
        mode = initialMode
        val note = repository.getById(noteId)
        if (note != null) {
            title = note.title
            val content = repository.loadContent(note)
            if (mode == EditorMode.SOURCE) {
                sourceText = TextFieldValue(content, TextRange(content.length))
            } else {
                blocks = repository.deriveBlocks(noteId, content).ifEmpty {
                    listOf(
                        NoteBlock(
                            id = UUID.randomUUID().toString(),
                            noteId = noteId,
                            type = BlockType.PARAGRAPH.name,
                            text = "",
                            indent = 0,
                            checked = false,
                            fontSize = BlockFontSize.NORMAL.name,
                            mediaPath = "",
                            sortOrder = 0,
                        ),
                    )
                }
                syncFieldsFromBlocks()
                focusedId = blocks.firstOrNull { !it.isAttachment && it.blockType != BlockType.DIVIDER }?.id
                    ?: blocks.firstOrNull()?.id
            }
            noteTags = repository.getTagsForNote(noteId)
        }
        // 0.10.0：打开选择器前确保默认卡片盒存在（首次使用兜底）。
        repository.ensureDefaultBox()
        ready = true
    }

    LaunchedEffect(ready, noteId) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { EditorSnapshot(modeState.value, titleState.value, currentContentMarkdown()) }
            .distinctUntilChanged()
            .debounce(400)
            .collect { snapshot ->
                repository.saveMarkdown(noteId, snapshot.title, snapshot.content)
            }
    }

    LaunchedEffect(focusedId) {
        val id = focusedId ?: return@LaunchedEffect
        kotlinx.coroutines.yield()
        val requester = requesters[id] ?: return@LaunchedEffect
        runCatching { requester.requestFocus() }
    }

    val charCount = if (mode == EditorMode.SOURCE) {
        remember(sourceText.text) { sourceText.text.codePointCount(0, sourceText.text.length) }
    } else {
        remember(blocks) { NoteRepository.bodyCharCount(blocks) }
    }

    /**
     * Live search over the note library for the active `@` mention. Blank
     * query = recent notes (updatedAt DESC); otherwise title OR body match,
     * case-insensitive. The note being edited is never a candidate (self
     * links are meaningless and the repository drops them anyway).
     */
    val mentionCandidates = remember(mention, allNotes) {
        val active = mention ?: return@remember emptyList<Note>()
        val query = active.mention.query.trim()
        // 源码模式的提及锚点是虚拟 ID；两种模式都排除“当前这篇笔记”。
        val selfId = if (active.blockId == SOURCE_MENTION_ANCHOR) noteId else active.blockId
        val pool = allNotes.filter { it.id != selfId }
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
        if (leaving) return
        leaving = true
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        if (!ready) {
            onBack(false)
            return
        }
        val titleNow = titleState.value
        val contentNow = currentContentMarkdown()
        val isBlankNow = titleNow.isBlank() && contentNow.isBlank()
        // 先在主线程同步返回目标页面（空笔记直接回主页），再在独立 IO 作用域落盘/清理空笔记，
        // 避免在编辑器仍在栈顶时删库或跨协程恢复触发返回栈异常退出应用。
        onBack(isBlankNow)
        editorCleanupScope.launch {
            if (isBlankNow) {
                runCatching { repository.deleteIfBlank(noteId) }
            } else {
                runCatching { repository.saveMarkdown(noteId, titleNow, contentNow) }
                runCatching { repository.deleteIfBlank(noteId) }
            }
        }
    }

    /**
     * One fill-only AI pass, shared by the 保存 button and the sparkle
     * dialog's 「只补充空位」 choice: applies the suggestion ONLY to fields
     * that are still empty at apply time and reports what was filled.
     */
    suspend fun runFillOnlyPass(extraMessages: List<String> = emptyList()) {
        val currentSettings = settingsRepo.get()
        val notices = ArrayList<String>()
        notices.addAll(extraMessages.filter { it.isNotBlank() })
        if (!currentSettings.aiEnableTitle) notices += "AI生成标题能力已关闭"
        if (!currentSettings.aiEnableTag) notices += "AI生成标签能力已关闭"
        val suffix = if (notices.isEmpty()) "" else "；${notices.joinToString("，")}"

        if (!currentSettings.aiEnableTitle && !currentSettings.aiEnableTag) {
            snackbar.showSnackbar("已保存$suffix")
            return
        }

        val result = ai.suggestFillTitleTags(noteId)
        if (result.isSuccess) {
            val suggestion = result.getOrNull()
            if (suggestion != null) {
                val nextTitle = if (currentSettings.aiEnableTitle) suggestion.title else ""
                val nextTags = if (currentSettings.aiEnableTag) suggestion.tags else emptyList()
                val fill = repository.fillEmptyTitleAndTags(noteId, nextTitle, nextTags)
                val latest = repository.getById(noteId)
                if (currentSettings.aiEnableTitle && title.isBlank() && fill.titleFilled && latest != null) {
                    title = latest.title
                }
                noteTags = repository.getTagsForNote(noteId)
                snackbar.showSnackbar(
                    when {
                        fill.titleFilled && fill.tagsFilled -> "已保存，AI 补充了标题和标签$suffix"
                        fill.titleFilled -> "已保存，AI 补充了标题$suffix"
                        fill.tagsFilled -> "已保存，AI 补充了标签$suffix"
                        else -> "已保存$suffix"
                    },
                )
            } else {
                snackbar.showSnackbar("已保存$suffix")
            }
        } else {
            val error = result.exceptionOrNull()
            val msg = error?.message.orEmpty()
            when {
                msg.contains("请先在设置里") || msg.contains("还没有配置") || msg.contains("空笔记") -> {
                    snackbar.showSnackbar("已保存$suffix")
                }
                msg.contains("已有内容") -> snackbar.showSnackbar("已保存$suffix")
                else -> snackbar.showSnackbar("已保存。自动标题/标签未完成：${msg.ifBlank { "已保留当前内容" }}$suffix")
            }
        }
    }

    /**
     * 0.10.0 保存时的卡片盒自动分配：仅当这篇笔记还没有任何卡片盒时触发；
     * 0.16.5：若「AI分配卡片盒」能力关闭，则跳过并返回「AI分配卡片盒能力已关闭」。
     */
    suspend fun autoAssignBoxIfNeeded(force: Boolean = false): String? {
        val currentSettings = settingsRepo.get()
        if (!currentSettings.aiEnableBox) return "AI分配卡片盒能力已关闭"
        val stored = repository.getById(noteId) ?: return null
        if (!force && stored.boxId.isNotBlank()) return null
        return ai.suggestExistingBox(noteId).fold(
            onSuccess = { boxName ->
                val box = repository.assignNoteToBoxByName(noteId, boxName)
                if (box != null) "已分配卡片盒「${box.name}」" else null
            },
            onFailure = { error ->
                val msg = error.message.orEmpty()
                if (msg.contains("没有找到合适的卡片盒") || msg.contains("能力已关闭")) msg else null
            },
        )
    }

    /** 保存 + fill-only AI pass (never overwrites manual content). */
    fun saveExplicit() {
        if (saving) return
        saving = true
        scope.launch {
            repository.saveMarkdown(noteId, titleState.value, currentContentMarkdown())
            val boxMessage = autoAssignBoxIfNeeded()
            runFillOnlyPass(extraMessages = listOfNotNull(boxMessage))
            saving = false
        }
    }

    fun runAiGeneration(overwrite: Boolean) {
        if (generating) return
        generating = true
        scope.launch {
            val contentNow = currentContentMarkdown()
            if (titleState.value.isBlank() && contentNow.isBlank()) {
                snackbar.showSnackbar("请先输入笔记内容再使用 AI 自动生成")
                generating = false
                return@launch
            }
            repository.saveMarkdown(noteId, titleState.value, contentNow)
            val currentSettings = settingsRepo.get()
            val boxMessage = autoAssignBoxIfNeeded(force = overwrite)
            if (overwrite) {
                val notices = ArrayList<String>()
                boxMessage?.let { notices += it }
                if (!currentSettings.aiEnableTitle) notices += "AI生成标题能力已关闭"
                if (!currentSettings.aiEnableTag) notices += "AI生成标签能力已关闭"
                if (!currentSettings.aiEnableTitle && !currentSettings.aiEnableTag) {
                    snackbar.showSnackbar(notices.joinToString("；"))
                    generating = false
                    return@launch
                }
                ai.generateTitleAndTags(titleState.value, contentNow)
                    .onSuccess { suggestion ->
                        var didTitle = false
                        var didTag = false
                        if (currentSettings.aiEnableTitle && suggestion.title.isNotBlank()) {
                            val nextTitle = suggestion.title.take(40)
                            repository.updateTitle(noteId, nextTitle)
                            title = nextTitle
                            didTitle = true
                        }
                        if (currentSettings.aiEnableTag) {
                            val tags = suggestion.tags
                                .map { Tag.normalizePath(it) }
                                .filter { it.isNotEmpty() }
                                .distinct()
                            if (tags.isNotEmpty()) {
                                repository.setNoteTagPaths(noteId, tags)
                                didTag = true
                            }
                        }
                        noteTags = repository.getTagsForNote(noteId)
                        val head = when {
                            didTitle && didTag -> "AI 已生成标题与标签"
                            didTitle -> "AI 已生成标题"
                            didTag -> "AI 已生成标签"
                            else -> "AI 生成完成"
                        }
                        val suffix = if (notices.isEmpty()) "" else "；${notices.joinToString("，")}"
                        snackbar.showSnackbar("$head$suffix")
                    }
                    .onFailure { error ->
                        val suffix = if (notices.isEmpty()) "" else "；${notices.joinToString("，")}"
                        snackbar.showSnackbar((error.message ?: "生成失败，已保留当前内容") + suffix)
                    }
            } else {
                runFillOnlyPass(extraMessages = listOfNotNull(boxMessage))
            }
            generating = false
        }
    }

    /**
     * 0.16.0 顶栏 AI 命令按钮：一键自动生成标题、分配标签并分配卡片盒。
     * 若笔记已有标题、标签或卡片盒，弹窗询问「只补充空位」或「覆盖重新生成」。
     */
    fun generateTitleTags() {
        if (generating) return
        scope.launch {
            val contentNow = currentContentMarkdown()
            if (titleState.value.isBlank() && contentNow.isBlank()) {
                snackbar.showSnackbar("请先输入笔记内容再使用 AI 自动生成")
                return@launch
            }
            val note = repository.getById(noteId)
            val hasExisting = note != null &&
                (note.title.isNotBlank() || note.boxId.isNotBlank() || repository.getTagsForNote(noteId).isNotEmpty())
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
                if (modeState.value == EditorMode.SOURCE) {
                    insertTextAtCaret("![图片]($path)")
                } else {
                    val index = focusedIndex()
                    val indent = blocks.getOrNull(index)?.indent ?: 0
                    insertAfter(index, newBlock(BlockType.IMAGE, indent = indent, mediaPath = path))
                    insertAfter(focusedIndex(), newBlock(BlockType.PARAGRAPH, indent = indent), focus = true)
                }
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
                if (modeState.value == EditorMode.SOURCE) {
                    insertTextAtCaret("[视频]($path)")
                } else {
                    val index = focusedIndex()
                    val indent = blocks.getOrNull(index)?.indent ?: 0
                    insertAfter(index, newBlock(BlockType.VIDEO, indent = indent, mediaPath = path))
                    insertAfter(focusedIndex(), newBlock(BlockType.PARAGRAPH, indent = indent), focus = true)
                }
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
                if (modeState.value == EditorMode.SOURCE) {
                    insertTextAtCaret("[文件: ${path.substringAfterLast('/')}]($path)")
                } else {
                    val index = focusedIndex()
                    val indent = blocks.getOrNull(index)?.indent ?: 0
                    val name = path.substringAfterLast('/')
                    insertAfter(index, newBlock(BlockType.FILE, indent = indent, text = name, mediaPath = path))
                    insertAfter(focusedIndex(), newBlock(BlockType.PARAGRAPH, indent = indent), focus = true)
                }
            }.onFailure {
                snackbar.showSnackbar("无法插入文件")
            }
        }
    }

    BackHandler { leave() }

    /** 用系统应用打开附件（视频/文件/音频），失败给 snackbar。 */
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

    val currentFontLabel = blocks.getOrNull(focusedIndex())?.blockFontSize?.label ?: BlockFontSize.NORMAL.label

    WantScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WantTopBar(
                title = "$charCount 字",
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    WantTextButton(
                        onClick = { saveExplicit() },
                        enabled = !saving,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("保存")
                    }
                    WantTextButton(
                        onClick = { generateTitleTags() },
                        enabled = !generating && !saving,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = "AI 生成标题、标签和卡片盒",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (generating) "…" else "AI",
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    // 0.11.0 N16-N17：源码模式收进右上角「高级」按钮，不在主工具栏/顶栏直接露出。
                    Box {
                        WantTextButton(
                            onClick = { showAdvancedMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("高级")
                        }
                        WantDropdownMenu(
                            expanded = showAdvancedMenu,
                            onDismissRequest = { showAdvancedMenu = false },
                        ) {
                            WantDropdownMenuItem(
                                text = {
                                    Text(
                                        if (mode == EditorMode.WYSIWYG) "源码模式" else "所见即所得模式",
                                    )
                                },
                                onClick = {
                                    showAdvancedMenu = false
                                    switchMode(
                                        if (mode == EditorMode.WYSIWYG) EditorMode.SOURCE else EditorMode.WYSIWYG,
                                    )
                                },
                            )
                        }
                    }
                    }
                },
            )
        },
        bottomBar = {
            OutlineToolbar(
                fontLabel = currentFontLabel,
                sourceMode = mode == EditorMode.SOURCE,
                currentHeadingLevel = blocks.getOrNull(focusedIndex())
                    ?.takeIf { it.blockType == BlockType.HEADING }
                    ?.headingLevel ?: 0,
                onAt = { showLinkPicker = true },
                onQuote = { insertQuote() },
                onAddTask = { addTypedBlock(BlockType.TASK) },
                onOrdered = { addTypedBlock(BlockType.ORDERED) },
                onUnordered = { addTypedBlock(BlockType.UNORDERED) },
                onSelectHeading = { level -> setHeadingLevel(level) },
                onTable = { addTypedBlock(BlockType.TABLE) },
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
                // 0.10.0：卡片盒选择行（手动选择立即保存）。
                EditorBoxBar(
                    boxName = boxes.firstOrNull { it.id == noteRow?.boxId.orEmpty() }?.name,
                    onPick = { showBoxPicker = true },
                )
                if (mode == EditorMode.SOURCE) {
                    // 源码模式：上半屏编辑 Markdown 源，下半屏实时预览
                    // （图片/多媒体/标题/列表/表格/缩进/引用/强调/任务/代码）。
                    SourceEditorPane(
                        value = sourceText,
                        onValueChange = { onSourceChanged(it) },
                        editorFont = editorFont,
                        linkTitles = linkTitles,
                        onOpenAttachment = { path -> openAttachment(path) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
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
                                    openAttachment(path)
                                },
                            )
                        }
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
        // 与阅读态共用同一个加标签弹窗。
        TagAddDialog(
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

    if (showBoxPicker) {
        // 与阅读态共用同一个弹窗（可搜索、无匹配可新建并归入本篇）。
        BoxPickerDialog(
            boxes = boxes,
            currentBoxId = noteRow?.boxId,
            onPick = { box ->
                showBoxPicker = false
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
                        scope.launch { snackbar.showSnackbar("已新建「${box.name}」并归入本篇") }
                    }
                }
            },
            onDismiss = { showBoxPicker = false },
        )
    }

    if (showOverwriteDialog) {
        WantAlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title = { Text("AI 生成标题、标签与卡片盒") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "这篇笔记已有标题、标签或卡片盒。选择应用方式：",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    WantTextButton(onClick = {
                        showOverwriteDialog = false
                        runAiGeneration(overwrite = false)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("只补充空位（不动已有内容）")
                    }
                    WantTextButton(onClick = {
                        showOverwriteDialog = false
                        runAiGeneration(overwrite = true)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("重新生成并覆盖标题、标签与卡片盒", color = MaterialTheme.colorScheme.error)
                    }
                    WantTextButton(onClick = { showOverwriteDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("取消")
                    }
                }
            },
            confirmButton = {},
        )
    }
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
    // 标题块按级别放大加粗；其他块用块级字号设置。
    val isHeading = block.blockType == BlockType.HEADING && block.headingLevel in 1..6
    val headingSp = when (block.headingLevel.coerceIn(1, 6)) {
        1 -> 26
        2 -> 22
        3 -> 19
        4 -> 17
        5 -> 15
        else -> 14
    }
    val baseSp = if (isHeading) headingSp else block.blockFontSize.sp
    val fontSize = (baseSp * scale).sp
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
                WantCheckbox(
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
            BlockType.QUOTE -> {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 10.dp)
                        .width(3.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
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
            BlockType.TABLE -> {
                // 表格块的编辑面是原始竖线行（多行），所见即所得模式下
                // 直接改单元格内容；渲染交给源码模式的预览面板。
                WantSurface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontFamily = FontFamily.Monospace,
                            fontSize = (12 * scale).sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onFocus() },
                    )
                }
            }
            else -> {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = fontFamily,
                        fontSize = fontSize,
                        lineHeight = (baseSp * scale * 1.5f).sp,
                        fontWeight = if (isHeading) FontWeight.SemiBold else null,
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
    // 0.10.0：本地附件与远程 http(s) 图片统一由 NoteImage 真实渲染。
    NoteImage(
        path = path,
        modifier = modifier.padding(vertical = 6.dp),
        fallbackLabel = "图片无法显示",
    )
}

@Composable
private fun LocalVideoBlock(path: String, onOpen: () -> Unit) {
    val media = LocalMedia.current
    val file = media.fileFor(path)
    WantSurface(
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
    sourceMode: Boolean,
    currentHeadingLevel: Int,
    onAt: () -> Unit,
    onQuote: () -> Unit,
    onAddTask: () -> Unit,
    onOrdered: () -> Unit,
    onUnordered: () -> Unit,
    onSelectHeading: (Int) -> Unit,
    onTable: () -> Unit,
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
    var showHeadingMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("@", Icons.Outlined.AlternateEmail, onAt)
        Box {
            ToolButton(
                label = if (!sourceMode && currentHeadingLevel in 1..6) "H$currentHeadingLevel" else "标题",
                icon = Icons.Outlined.Title,
                onClick = { showHeadingMenu = true },
            )
            WantDropdownMenu(
                expanded = showHeadingMenu,
                onDismissRequest = { showHeadingMenu = false },
            ) {
                val levels = listOf(
                    0 to "正文",
                    1 to "一级标题 (H1)",
                    2 to "二级标题 (H2)",
                    3 to "三级标题 (H3)",
                    4 to "四级标题 (H4)",
                    5 to "五级标题 (H5)",
                    6 to "六级标题 (H6)",
                )
                levels.forEach { (lvl, label) ->
                    WantDropdownMenuItem(
                        text = {
                            Text(
                                text = if (!sourceMode && currentHeadingLevel == lvl) "✓ $label" else label,
                                fontWeight = if (lvl in 1..6) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            showHeadingMenu = false
                            onSelectHeading(lvl)
                        },
                    )
                }
            }
        }
        WantTextButton(onClick = onQuote, modifier = Modifier.padding(horizontal = 0.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("引用", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (!sourceMode) {
            ToolButton("表格", Icons.Outlined.TableChart, onTable)
            ToolButton("任务", Icons.Outlined.CheckBox, onAddTask)
            ToolButton("有序", Icons.Outlined.FormatListNumbered, onOrdered)
            ToolButton("无序", Icons.AutoMirrored.Outlined.FormatListBulleted, onUnordered)
            ToolButton("分割线", Icons.Outlined.HorizontalRule, onDivider)
            ToolButton("字号$fontLabel", Icons.Outlined.FormatSize, onFontSize)
            ToolButton("缩进", Icons.AutoMirrored.Outlined.FormatIndentIncrease, onIndent)
            ToolButton("反缩进", Icons.AutoMirrored.Outlined.FormatIndentDecrease, onOutdent)
        }
        ToolButton("粗", Icons.Outlined.FormatBold, onBold)
        ToolButton("斜", Icons.Outlined.FormatItalic, onItalic)
        ToolButton("删除线", Icons.Outlined.StrikethroughS, onStrike)
        ToolButton("高亮", Icons.Outlined.Highlight, onHighlight)
        ToolButton("图片", Icons.Outlined.Image, onImage)
        ToolButton("视频", Icons.Outlined.Videocam, onVideo)
        ToolButton("文件", Icons.Outlined.AttachFile, onFile)
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    WantTextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 0.dp)) {
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
            WantTextButton(onClick = onPick) {
                Text("选择标签")
            }
        }
    }
}

/** 0.10.0：编辑器里的卡片盒行——显示当前归属，点按打开选择对话框。 */
@Composable
private fun EditorBoxBar(
    boxName: String?,
    onPick: () -> Unit,
) {
    WantTextButton(onClick = onPick, modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (boxName.isNullOrBlank()) "卡片盒：未分配（保存时自动分配）" else "卡片盒：$boxName",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun LocalFileBlock(path: String, label: String, onOpen: () -> Unit) {
    val media = LocalMedia.current
    val file = media.fileFor(path)
    WantSurface(
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

/** 源码模式没有块；@ 提及用这个虚拟锚点标记「来自整篇文本」。 */
private const val SOURCE_MENTION_ANCHOR = "__source__"

/** 400ms 防抖保存的快照：模式 + 标题 + 当前模式的正文 Markdown。 */
private data class EditorSnapshot(
    val mode: EditorMode,
    val title: String,
    val content: String,
)

/**
 * 源码模式的主编辑面：上半屏整篇 Markdown 文本（等宽字体），下半屏
 * MarkdownPreview 实时预览。任务勾选、双链解析与附件打开在预览里可用。
 */
@Composable
private fun SourceEditorPane(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    editorFont: FontFamily,
    linkTitles: Map<String, String>,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalFontScale.current
    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
                fontSize = (14 * scale).sp,
                lineHeight = (20 * scale).sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.outline,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "预览",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MarkdownPreview(
            content = value.text,
            linkTitles = linkTitles,
            onOpenAttachment = onOpenAttachment,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

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
    WantSurface(
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
                WantTextButton(onClick = onDismiss) { Text("关闭") }
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
    WantAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@ 链接到笔记") },
        text = {
            Column {
                WantTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索笔记",
                    placeholder = "留空显示最近笔记",
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
            WantTextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
