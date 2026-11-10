package com.davidzhu.wantnote.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.LocalInsights
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.ui.LocalAi
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onOpenNote: (String) -> Unit, onOpenAiSettings: () -> Unit = {}) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val ai = LocalAi.current
    val items by repository.observeAllWithBlocks().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val summary = remember(items) { LocalInsights.summarize(items) }
    val weekStart = remember { Dates.startOfWeek() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var running by remember { mutableStateOf(false) }
    val reviewNotes = remember(items, weekStart) {
        items.filter { it.note.updatedAt >= weekStart || it.note.createdAt >= weekStart }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("洞察") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("笔记回顾", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = "本周写过或改过的笔记，全部来自本机内容，可离线查看。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (reviewNotes.isEmpty()) {
                Text(
                    "这一周还没有可回顾的笔记。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            } else {
                reviewNotes.forEach { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onOpenNote(item.note.id) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = SearchText.displayTitle(item.note),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = Dates.formatTimestamp(item.note.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val preview = SearchText.preview(item.note).ifBlank {
                                SearchText.firstMeaningfulLine(item.note.body)
                            }
                            if (preview.isNotBlank()) {
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("提炼总结", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (settings.aiSummaryModelId.isBlank()) {
                    "配置 AI 模型后，可以用所选模型生成本页提炼。未配置时仍显示本机统计。"
                } else {
                    "使用已选择的模型生成本页提炼。本机统计始终保留在下方。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (settings.aiSummaryProviderId.isBlank() || settings.aiSummaryModelId.isBlank()) {
                            onOpenAiSettings()
                            return@Button
                        }
                        if (running) return@Button
                        running = true
                        scope.launch {
                            val corpus = items.take(40).joinToString("\n\n") { item ->
                                val title = SearchText.displayTitle(item.note)
                                val body = item.note.body.take(800)
                                "《$title》\n$body"
                            }
                            val result = ai.generateSummary(corpus)
                            result.onSuccess { text ->
                                settingsRepo.update {
                                    it.copy(aiSummaryText = text, aiSummaryUpdatedAt = System.currentTimeMillis())
                                }
                            }.onFailure { error ->
                                snackbar.showSnackbar(error.message ?: "提炼失败，已保留本机统计")
                            }
                            running = false
                        }
                    },
                    enabled = !running,
                ) {
                    Text(if (running) "正在提炼…" else if (settings.aiSummaryModelId.isBlank()) "去配置 AI" else "用 AI 提炼")
                }
                if (settings.aiSummaryText.isNotBlank()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                settingsRepo.update { it.copy(aiSummaryText = "", aiSummaryUpdatedAt = 0L) }
                            }
                        },
                    ) { Text("清除 AI 总结") }
                }
            }
            if (settings.aiSummaryText.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI 提炼", style = MaterialTheme.typography.titleSmall)
                        if (settings.aiSummaryUpdatedAt > 0L) {
                            Text(
                                Dates.formatTimestamp(settings.aiSummaryUpdatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = settings.aiSummaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = summary.narrative,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("笔记", summary.totalNotes.toString(), Modifier.weight(1f))
                StatCard("本周新增", summary.weekNotes.toString(), Modifier.weight(1f))
                StatCard("本月新增", summary.monthNotes.toString(), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("待办未完成", summary.openTasks.toString(), Modifier.weight(1f))
                StatCard("待办已完成", summary.doneTasks.toString(), Modifier.weight(1f))
                StatCard("正文字数", summary.totalChars.toString(), Modifier.weight(1f))
            }

            if (summary.highlights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("高亮摘录", style = MaterialTheme.typography.titleMedium)
                summary.highlights.forEach { highlight ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
                    ) {
                        Text(highlight, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (summary.busiestDays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("活跃日期", style = MaterialTheme.typography.titleMedium)
                summary.busiestDays.forEach { (day, count) ->
                    Text(
                        text = "$day · $count 条",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
