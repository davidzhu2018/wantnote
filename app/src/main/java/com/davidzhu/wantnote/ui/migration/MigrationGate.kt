package com.davidzhu.wantnote.ui.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import com.davidzhu.wantnote.ui.components.WantLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantSurface
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SettingsRepository
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalSettings
import kotlinx.coroutines.launch

/**
 * 0.9.0 强制迁移闸门（不可跳过）：
 *
 * - 从 0.8.0 升级后第一次启动时，把全部历史「块/正文」数据迁移成
 *   Markdown 笔记（每篇一个 notes/<id>.md），保留标题、标签、任务、
 *   `[[note:id]]` 双链与媒体引用；笔记 ID 不变。
 * - 用户必须点「开始迁移」，界面只有进行度和结果，没有跳过入口。
 * - 可中断续跑：迁移按「mdPath 为空」的笔记逐篇推进，任何一刻退出
 *   （杀进程/崩溃），下次启动回到同一个闸门从未迁移的那篇继续，
 *   已迁移的笔记不会被重复处理。
 */
@Composable
fun MigrationGate(onDone: () -> Unit) {
    val repository = LocalNotes.current
    val settingsRepo = LocalSettings.current
    val settings by settingsRepo.observe().collectAsStateWithLifecycle(initialValue = AppSettings.DEFAULT)
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(MigrationPhase.CHECKING) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var currentLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 启动检查：0.9.0 标记已置位 → 直接放行；否则看还有没有待迁移的笔记。
    // 全部迁完（或空库/新装）也自动置位放行。
    LaunchedEffect(Unit) {
        if (settings.markdownMigrationDone) {
            phase = MigrationPhase.FINISHED
            return@LaunchedEffect
        }
        val pending = repository.pendingMarkdownMigration()
        total = pending.size
        if (pending.isEmpty()) {
            settingsRepo.update { it.copy(markdownMigrationDone = true) }
            phase = MigrationPhase.FINISHED
        } else {
            phase = MigrationPhase.PROMPT
        }
    }

    if (phase == MigrationPhase.FINISHED) {
        LaunchedEffect(Unit) { onDone() }
        WantSurface(modifier = Modifier.fillMaxSize()) {}
        return
    }

    WantSurface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (phase) {
            MigrationPhase.CHECKING -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WantCircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在检查数据…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            MigrationPhase.PROMPT, MigrationPhase.RUNNING, MigrationPhase.FAILED -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("升级到 Markdown", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "想记 0.9.0 把笔记正文升级为标准的 Markdown 文档：" +
                            "每篇笔记保存为一个 .md 文件，标题、标签、任务、笔记链接、" +
                            "图片和视频全部保留，笔记 ID 不变。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "检测到 $total 篇笔记需要迁移。迁移必须完成后才能继续使用，" +
                            "过程中可以中断，再次打开会从没迁移的那篇继续，不会重复或丢失内容。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    when (phase) {
                        MigrationPhase.PROMPT -> {
                            WantButton(
                                onClick = {
                                    phase = MigrationPhase.RUNNING
                                    errorMessage = null
                                    scope.launch {
                                        runMigration(
                                            repository = repository,
                                            settingsRepo = settingsRepo,
                                            onProgress = { finished, label ->
                                                done = finished
                                                currentLabel = label
                                            },
                                            onError = { message ->
                                                errorMessage = message
                                                phase = MigrationPhase.FAILED
                                            },
                                            onFinished = {
                                                phase = MigrationPhase.FINISHED
                                            },
                                            onTotal = { total = it },
                                            onNoteStarted = { label -> currentLabel = label },
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("开始迁移（$total 篇）")
                            }
                        }
                        MigrationPhase.RUNNING -> {
                            val progress = if (total > 0) done.toFloat() / total else 0f
                            WantLinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "已迁移 $done / $total 篇",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (currentLabel.isNotBlank()) {
                                Text(
                                    "正在处理：$currentLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        MigrationPhase.FAILED -> {
                            Text(
                                errorMessage ?: "迁移失败，内容未被改动。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            WantButton(
                                onClick = {
                                    phase = MigrationPhase.RUNNING
                                    errorMessage = null
                                    scope.launch {
                                        runMigration(
                                            repository = repository,
                                            settingsRepo = settingsRepo,
                                            onProgress = { finished, label ->
                                                done = finished
                                                currentLabel = label
                                            },
                                            onError = { message ->
                                                errorMessage = message
                                                phase = MigrationPhase.FAILED
                                            },
                                            onFinished = { phase = MigrationPhase.FINISHED },
                                            onTotal = { total = it },
                                            onNoteStarted = { label -> currentLabel = label },
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("重试迁移")
                            }
                        }
                        else -> Unit
                    }
                }
            }
            MigrationPhase.FINISHED -> Unit
        }
    }
}

private enum class MigrationPhase {
    CHECKING,
    PROMPT,
    RUNNING,
    FAILED,
    FINISHED,
}

/**
 * 逐篇迁移：每次取「还没有 .md 文件」的第一篇，成功计数，失败立即停下
 * （内容不动，可以重试）。全部完成后置位 markdownMigrationDone。
 */
private suspend fun runMigration(
    repository: NoteRepository,
    settingsRepo: SettingsRepository,
    onNoteStarted: (String) -> Unit,
    onProgress: (Int, String) -> Unit,
    onTotal: (Int) -> Unit,
    onError: (String) -> Unit,
    onFinished: () -> Unit,
) {
    var finished = 0
    while (true) {
        val pending = runCatching { repository.pendingMarkdownMigration() }.getOrNull()
        if (pending == null) {
            onError("无法读取笔记列表，请重试。")
            return
        }
        if (pending.isEmpty()) break
        onTotal(pending.size + finished)
        val note = pending.first()
        val label = note.title.ifBlank { "无标题笔记" }
        onNoteStarted(label)
        val ok = runCatching { repository.migrateNoteToMarkdown(note) }
        if (ok.getOrDefault(false)) {
            finished += 1
            onProgress(finished, label)
        } else {
            onError(
                "「${label.take(20)}」迁移失败：" +
                    (ok.exceptionOrNull()?.message?.take(60) ?: "原因未知") +
                    "。这篇笔记没有被改动，可重试。",
            )
            return
        }
    }
    runCatching { settingsRepo.update { it.copy(markdownMigrationDone = true) } }
    onFinished()
}
