package com.davidzhu.wantnote.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.theme.InkMuted
import com.davidzhu.wantnote.ui.theme.Paper
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
) {
    val repository = LocalNotes.current
    var query by rememberSaveable { mutableStateOf("") }
    val resultsFlow = remember(query) {
        if (query.isBlank()) flowOf(emptyList<Note>()) else repository.search(query.trim())
    }
    val results by resultsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("按关键字搜标题或正文") },
                singleLine = true,
                colors = TextFieldDefaults.colors(),
            )
            if (query.isBlank()) {
                Text(
                    text = "输入关键字搜索标题或正文。",
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else if (results.isEmpty()) {
                Text(
                    text = "没有找到匹配的笔记。",
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(results, key = { it.id }) { note ->
                        val title = note.title.trim().ifBlank {
                            note.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "无标题"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenNote(note.id) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (note.body.isNotBlank()) {
                                Text(
                                    text = note.body.trim(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
