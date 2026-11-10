package com.davidzhu.wantnote.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidzhu.wantnote.data.Markup
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.theme.Ink
import com.davidzhu.wantnote.ui.theme.InkMuted
import com.davidzhu.wantnote.ui.theme.Paper
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    noteId: String,
    onBack: () -> Unit,
) {
    val repository = LocalNotes.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue()) }
    val titleState = rememberUpdatedState(title)
    val bodyState = rememberUpdatedState(body.text)

    LaunchedEffect(noteId) {
        val note = repository.getById(noteId)
        if (note != null) {
            title = note.title
            body = TextFieldValue(note.body, TextRange(note.body.length))
        }
        ready = true
    }

    LaunchedEffect(ready, noteId) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { title to body.text }
            .distinctUntilChanged()
            .debounce(400)
            .collect { (t, b) ->
                repository.save(noteId, t, b)
            }
    }

    fun leave() {
        scope.launch {
            repository.save(noteId, titleState.value, bodyState.value)
            repository.deleteIfBlank(noteId)
            onBack()
        }
    }

    BackHandler { leave() }
    DisposableEffect(noteId) {
        onDispose {
            // Best-effort flush if the screen is removed without BackHandler.
        }
    }

    Scaffold(
        containerColor = Paper,
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("速记") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
        bottomBar = {
            FormatBar(
                onWrap = { marker ->
                    val start = body.selection.min
                    val end = body.selection.max
                    val (next, lo, hi) = Markup.wrap(body.text, start, end, marker)
                    body = TextFieldValue(next, TextRange(lo, hi))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink, fontSize = 22.sp),
                cursorBrush = SolidColor(Ink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text("标题（可选）", color = InkMuted, style = MaterialTheme.typography.titleLarge, fontSize = 22.sp)
                    }
                    inner()
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 18.sp, lineHeight = 28.sp),
                cursorBrush = SolidColor(Ink),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                decorationBox = { inner ->
                    if (body.text.isEmpty()) {
                        Text("写点什么…不必先起标题", color = InkMuted, style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp)
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun FormatBar(onWrap: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatButton("粗", onClick = { onWrap("**") }) {
            Text("粗", fontWeight = FontWeight.Bold, color = Ink)
        }
        FormatButton("斜", onClick = { onWrap("*") }) {
            Text("斜", fontStyle = FontStyle.Italic, color = Ink)
        }
        FormatButton("高亮", onClick = { onWrap("==") }) {
            Text("高亮", color = Ink)
        }
        FormatButton("删除线", onClick = { onWrap("~~") }) {
            Text("删除线", textDecoration = TextDecoration.LineThrough, color = Ink)
        }
    }
}

@Composable
private fun FormatButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    TextButton(onClick = onClick) { content() }
}
