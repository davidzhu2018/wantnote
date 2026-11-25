package com.davidzhu.wantnote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.DinoxRuleSyncMode
import com.davidzhu.wantnote.data.DinoxRuleType
import com.davidzhu.wantnote.data.DinoxSyncRule
import com.davidzhu.wantnote.data.DinoxSyncRuleCodec
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.sync.dinox.DinoxBoxRefreshState
import com.davidzhu.wantnote.sync.dinox.DinoxBoxSyncWorker
import com.davidzhu.wantnote.sync.dinox.DinoxSyncRuleTargetResolver
import com.davidzhu.wantnote.sync.dinox.DinoxSyncService
import com.davidzhu.wantnote.ui.LocalDinox
import com.davidzhu.wantnote.ui.LocalNotes
import kotlinx.coroutines.launch

/** 自动同步常用间隔预设（分钟；用户也可自行输入自定义分钟数）。 */
private val AutoSyncIntervalPresets = listOf(
    15 to "15 分钟",
    30 to "30 分钟",
    60 to "1 小时",
    360 to "6 小时",
    720 to "12 小时",
    1440 to "24 小时",
)

/**
 * 数据管理 → 生态互联（0.11.0）。
 *
 * - Dinox Token：测试成功才用 Android Keystore 加密保存，绝不记录或打印明文。
 * - 从 Dinox 卡片盒导入本机：单向、用户发起；来源选择器使用本地数据库缓存 + 最多每两小时刷新。
 * - 卡片盒同步（0.11.0 新增）：支持「一对一」（可选沿用 Dinox 卡片盒名称）与「多对一」规则，
 *   可手动全量/增量同步或按用户设定间隔自动同步（WorkManager + Room 持久化）。
 * - 已移除旧版重复的「从 Dinox 同步到本机」入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcosystemTab(snackbar: SnackbarHostState) {
    val notesRepo = LocalNotes.current
    val dinox = LocalDinox.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var hasToken by remember { mutableStateOf(false) }

    val localBoxes by notesRepo.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val cachedRemoteBoxes by dinox.observeCachedBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val boxRefresh by dinox.boxRefreshState.collectAsStateWithLifecycle()
    val syncRules by dinox.observeSyncRules().collectAsStateWithLifecycle(initialValue = emptyList())

    // ---- 单向导入状态 ----
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var selectedSources by remember { mutableStateOf<List<String>>(emptyList()) }
    var followDinoxNames by remember { mutableStateOf(true) }
    var targetMenuOpen by remember { mutableStateOf(false) }
    var targetBoxId by remember { mutableStateOf<String?>(null) }

    // ---- 0.11.0 卡片盒同步规则弹窗状态 ----
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DinoxSyncRule?>(null) }
    var deleteRuleTarget by remember { mutableStateOf<DinoxSyncRule?>(null) }
    var runningRuleId by remember { mutableStateOf<String?>(null) }

    /**
     * 0.12.0：打开 Dinox 卡片盒选择器或点击「重试」时，通过轻量 `GET /api/openapi/zettelboxes`
     * 与两小时缓存 + 失败冷却策略处理：
     * - 有缓存：立刻展示列表；过期时后台轻量刷新；
     * - 无缓存且轻量请求进行中：仅短加载；
     * - 失败且无缓存：立即给出不含敏感信息的短错误和「重试」，重开不自动循环阻塞。
     */
    fun requestRemoteBoxes(forceRetry: Boolean = false) {
        scope.launch {
            dinox.ensureBoxesForPicker(forceRetry = forceRetry)
        }
    }

    LaunchedEffect(Unit) {
        dinox.importSealedToken()
        hasToken = dinox.hasToken()
    }

    fun runOperation(operation: suspend () -> Result<String>) {
        if (busy) return
        busy = true
        lastError = null
        scope.launch {
            operation()
                .onSuccess { message ->
                    hasToken = dinox.hasToken()
                    snackbar.showSnackbar(message)
                }
                .onFailure { error -> lastError = error.message ?: "操作失败" }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "第三方互联。当前已接入：Dinox（AI 笔记）。Token 在 Dinox App → " +
                "设置 → 同步设置 → API Token 获取；请求发往 aisdk.chatgo.pro 开放接口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTitle("Dinox Token")
        if (hasToken) {
            Text(
                "✓ 已保存 Token（Android Keystore 加密，不进日志/备份明文）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "尚未保存 Token",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("粘贴 Dinox API Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    runOperation {
                        dinox.testAndSaveToken(tokenInput).also { result ->
                            if (result.isSuccess) {
                                tokenInput = ""
                                // 保存新 Token 后后台预加载卡片盒列表，不阻塞提示。
                                scope.launch { dinox.preloadBoxesIfNeeded() }
                            }
                        }
                    }
                },
                enabled = !busy && tokenInput.isNotBlank(),
            ) { Text("测试并保存") }
            if (hasToken) {
                OutlinedButton(onClick = { runOperation { dinox.testSavedToken() } }, enabled = !busy) {
                    Text("重新测试")
                }
                OutlinedButton(
                    onClick = {
                        runOperation {
                            dinox.clearToken()
                            selectedSources = emptyList()
                            Result.success("已清除本机 Token")
                        }
                    },
                    enabled = !busy,
                ) { Text("清除") }
            }
        }
        lastError?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ---- 1. 从 Dinox 导入到本机（单向、用户发起） ----
        SectionTitle("从 Dinox 导入到本机")
        Text(
            "一次性单向导入：多选 Dinox 卡片盒作为来源，导入到同名本地卡片盒（沿用 Dinox 卡片盒名称，没有则新建）或指定某一个本地卡片盒。" +
                "卡片盒名单已在本地缓存，最多每两小时后台更新一次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = {
                    sourceMenuOpen = true
                    requestRemoteBoxes(forceRetry = false)
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (selectedSources.isEmpty()) "选择来源卡片盒（可多选）" else "已选 ${selectedSources.size} 个来源卡片盒",
                    modifier = Modifier.weight(1f),
                )
                Text("▾", style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                when {
                    cachedRemoteBoxes.isEmpty() && boxRefresh.refreshing -> DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 10.dp).size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("正在获取卡片盒…")
                            }
                        },
                        onClick = {},
                    )
                    cachedRemoteBoxes.isEmpty() && boxRefresh.lastError != null -> DropdownMenuItem(
                        text = { Text("${boxRefresh.lastError.orEmpty()}（点此重试）", color = MaterialTheme.colorScheme.error) },
                        onClick = { requestRemoteBoxes(forceRetry = true) },
                    )
                    else -> {
                        if (boxRefresh.refreshing) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "正在后台更新卡片盒列表…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {},
                            )
                        }
                        if (boxRefresh.lastError != null && cachedRemoteBoxes.isNotEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "刷新失败：${boxRefresh.lastError.orEmpty()}（已保留缓存，点此重试）",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = { requestRemoteBoxes(forceRetry = true) },
                            )
                        }
                        if (cachedRemoteBoxes.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Dinox 里还没有卡片盒（点此刷新）") },
                                onClick = { requestRemoteBoxes(forceRetry = true) },
                            )
                        }
                        cachedRemoteBoxes.forEach { name ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = name in selectedSources,
                                            onCheckedChange = null,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        Text(name)
                                    }
                                },
                                onClick = {
                                    selectedSources = if (name in selectedSources) {
                                        selectedSources - name
                                    } else {
                                        selectedSources + name
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (cachedRemoteBoxes.isEmpty() && !boxRefresh.refreshing && boxRefresh.lastError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    boxRefresh.lastError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { requestRemoteBoxes(forceRetry = true) }) {
                    Text("重试")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = followDinoxNames,
                onClick = { followDinoxNames = true },
                label = { Text("沿用 Dinox 卡片盒名字") },
            )
            FilterChip(
                selected = !followDinoxNames,
                onClick = { followDinoxNames = false },
                label = { Text("导入到指定卡片盒") },
            )
        }
        if (!followDinoxNames) {
            Box {
                OutlinedButton(
                    onClick = { targetMenuOpen = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val name = targetBoxId?.let { id -> localBoxes.firstOrNull { it.id == id }?.name }
                    Text(
                        name?.let { "目标：$it" } ?: "选择本地目标卡片盒（单选）",
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(expanded = targetMenuOpen, onDismissRequest = { targetMenuOpen = false }) {
                    if (localBoxes.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("还没有本地卡片盒，可到 设置 → 卡片盒管理 创建") },
                            onClick = {},
                        )
                    }
                    localBoxes.forEach { box ->
                        DropdownMenuItem(
                            text = { Text(if (box.id == NoteBox.DEFAULT_ID) "${box.name}（默认）" else box.name) },
                            onClick = {
                                targetBoxId = box.id
                                targetMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        Button(
            onClick = {
                val target = if (followDinoxNames) {
                    DinoxSyncService.ImportTarget.FollowDinoxNames
                } else {
                    val id = targetBoxId ?: return@Button
                    DinoxSyncService.ImportTarget.LocalBox(id)
                }
                val sources = selectedSources
                if (sources.isEmpty()) return@Button
                busy = true
                lastError = null
                scope.launch {
                    dinox.importFromBoxes(sources, target)
                        .onSuccess { message ->
                            selectedSources = emptyList()
                            snackbar.showSnackbar(message)
                        }
                        .onFailure { error -> lastError = error.message ?: "导入失败" }
                    busy = false
                }
            },
            enabled = !busy && selectedSources.isNotEmpty() && (followDinoxNames || targetBoxId != null),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "正在导入…" else "开始导入") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ---- 2. 0.11.0 卡片盒同步（多对一 / 一对一；手动全量增量 / 自动间隔） ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("卡片盒同步")
            TextButton(
                onClick = {
                    editingRule = null
                    showRuleEditor = true
                    requestRemoteBoxes(forceRetry = false)
                },
                enabled = !busy,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("新建规则", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Text(
            "按规则将 Dinox 卡片盒同步到本机卡片盒：支持多对一（多个 Dinox 盒 → 一个本机已有盒）" +
                "和一对一（单个 Dinox 盒 → 本机目标盒，或沿用 Dinox 卡片盒名称）；每条规则可设为手动（全量/增量）或按设定间隔自动执行。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (syncRules.isEmpty()) {
            Text(
                "还没有同步规则。点击右上角「新建规则」开始配置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            syncRules.forEach { rule ->
                SyncRuleCard(
                    rule = rule,
                    localBoxes = localBoxes,
                    busy = busy || runningRuleId != null,
                    isRunningThis = runningRuleId == rule.id,
                    onRunFull = {
                        if (busy || runningRuleId != null) return@SyncRuleCard
                        runningRuleId = rule.id
                        lastError = null
                        scope.launch {
                            dinox.runSyncRule(rule.id, full = true)
                                .onSuccess { snackbar.showSnackbar(it) }
                                .onFailure { lastError = it.message ?: "全量同步失败" }
                            runningRuleId = null
                        }
                    },
                    onRunIncremental = {
                        if (busy || runningRuleId != null) return@SyncRuleCard
                        runningRuleId = rule.id
                        lastError = null
                        scope.launch {
                            dinox.runSyncRule(rule.id, full = false)
                                .onSuccess { snackbar.showSnackbar(it) }
                                .onFailure { lastError = it.message ?: "增量同步失败" }
                            runningRuleId = null
                        }
                    },
                    onEdit = {
                        editingRule = rule
                        showRuleEditor = true
                        requestRemoteBoxes(forceRetry = false)
                    },
                    onDelete = { deleteRuleTarget = rule },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ---- 3. 发送到 Dinox 说明 ----
        SectionTitle("发送到 Dinox")
        Text(
            "在主页长按笔记卡片选择「发送到Dinox」，从本地缓存的 Dinox 卡片盒中选择目标" +
                "（最多每两小时后台更新），再把这篇笔记的 Markdown、标题、标签与原创建时间发送到 Dinox。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showRuleEditor) {
        SyncRuleEditorDialog(
            initial = editingRule,
            remoteBoxes = cachedRemoteBoxes,
            localBoxes = localBoxes,
            boxRefresh = boxRefresh,
            onRequestBoxes = { force -> requestRemoteBoxes(forceRetry = force) },
            onSave = { draft ->
                val result = dinox.saveSyncRule(draft)
                result.onSuccess { saved ->
                    DinoxBoxSyncWorker.scheduleRule(context, saved)
                    showRuleEditor = false
                    editingRule = null
                    scope.launch { snackbar.showSnackbar("已保存同步规则") }
                }
                result.exceptionOrNull()?.message
            },
            onDismiss = {
                showRuleEditor = false
                editingRule = null
            },
        )
    }

    deleteRuleTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteRuleTarget = null },
            title = { Text("删除同步规则？") },
            text = {
                Text(
                    "确定删除规则「${target.sourceBoxes.joinToString("、")}」吗？" +
                        "删除后将停止该规则的自动同步，已同步到本机的笔记不会被删除。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.id
                        deleteRuleTarget = null
                        DinoxBoxSyncWorker.cancelRule(context, id)
                        scope.launch {
                            dinox.deleteSyncRule(id)
                            snackbar.showSnackbar("已删除同步规则")
                        }
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteRuleTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SyncRuleCard(
    rule: DinoxSyncRule,
    localBoxes: List<NoteBox>,
    busy: Boolean,
    isRunningThis: Boolean,
    onRunFull: () -> Unit,
    onRunIncremental: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sources = rule.sourceBoxes
    val targetLabel = when {
        rule.typeOption == DinoxRuleType.ONE_TO_ONE && rule.followDinoxName -> {
            val name = sources.firstOrNull().orEmpty()
            "沿用 Dinox 卡片盒名称（$name）"
        }
        else -> {
            localBoxes.firstOrNull { it.id == rule.targetBoxId }?.name
                ?: "（目标卡片盒已删除，请编辑）"
        }
    }
    val modeLabel = when (rule.modeOption) {
        DinoxRuleSyncMode.MANUAL -> "手动同步"
        DinoxRuleSyncMode.AUTO -> "自动同步 · 每 ${DinoxSyncRuleCodec.formatInterval(rule.autoIntervalMinutes)}"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${rule.typeOption.label} · $modeLabel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, enabled = !busy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑规则", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除规则", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = "Dinox：${sources.joinToString("、")}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "本机：$targetLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.lastRunAt > 0L || rule.lastStatus.isNotBlank()) {
                val timePart = if (rule.lastRunAt > 0L) Dates.formatTime(rule.lastRunAt) else ""
                val statusPart = rule.lastStatus
                Text(
                    text = listOf(timePart, statusPart).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rule.modeOption == DinoxRuleSyncMode.MANUAL) {
                    Button(onClick = onRunFull, enabled = !busy) {
                        Text(if (isRunningThis) "同步中…" else "全量同步")
                    }
                    OutlinedButton(onClick = onRunIncremental, enabled = !busy) {
                        Text("增量同步")
                    }
                } else {
                    OutlinedButton(onClick = onRunIncremental, enabled = !busy) {
                        Text(if (isRunningThis) "同步中…" else "立即增量")
                    }
                    OutlinedButton(onClick = onRunFull, enabled = !busy) {
                        Text("立即全量")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncRuleEditorDialog(
    initial: DinoxSyncRule?,
    remoteBoxes: List<String>,
    localBoxes: List<NoteBox>,
    boxRefresh: DinoxBoxRefreshState,
    onRequestBoxes: (Boolean) -> Unit,
    onSave: suspend (DinoxSyncRule) -> String?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ruleType by remember(initial) { mutableStateOf(initial?.typeOption ?: DinoxRuleType.ONE_TO_ONE) }
    var selectedSources by remember(initial) { mutableStateOf(initial?.sourceBoxes ?: emptyList()) }
    var followDinoxName by remember(initial) {
        mutableStateOf(
            if (initial != null) {
                initial.typeOption == DinoxRuleType.ONE_TO_ONE && initial.followDinoxName
            } else {
                true
            },
        )
    }
    var targetBoxId by remember(initial) { mutableStateOf(initial?.targetBoxId?.takeIf { it.isNotBlank() }) }
    var syncMode by remember(initial) { mutableStateOf(initial?.modeOption ?: DinoxRuleSyncMode.MANUAL) }
    var intervalText by remember(initial) {
        val existing = initial?.autoIntervalMinutes ?: 0
        mutableStateOf(if (existing >= DinoxSyncRuleTargetResolver.MIN_AUTO_INTERVAL_MINUTES) existing.toString() else "60")
    }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var targetMenuOpen by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val availableRemoteBoxes = remember(remoteBoxes, selectedSources) {
        (remoteBoxes + selectedSources).distinct().sorted()
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initial == null) "新建同步规则" else "编辑同步规则") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("规则类型", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DinoxRuleType.entries.forEach { option ->
                        FilterChip(
                            selected = ruleType == option,
                            onClick = {
                                ruleType = option
                                errorText = null
                                if (option == DinoxRuleType.ONE_TO_ONE) {
                                    selectedSources = selectedSources.take(1)
                                } else {
                                    // 多对一仅支持同步到本机已有卡片盒，不支持沿用名称。
                                    followDinoxName = false
                                }
                            },
                            label = { Text(option.label) },
                        )
                    }
                }

                Text(
                    if (ruleType == DinoxRuleType.ONE_TO_ONE) "来源：Dinox 卡片盒（单选）" else "来源：Dinox 卡片盒（可多选）",
                    style = MaterialTheme.typography.labelLarge,
                )
                Box {
                    OutlinedButton(
                        onClick = {
                            sourceMenuOpen = true
                            onRequestBoxes(false)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val label = when {
                            selectedSources.isEmpty() -> if (ruleType == DinoxRuleType.ONE_TO_ONE) {
                                "选择 Dinox 卡片盒"
                            } else {
                                "选择多个 Dinox 卡片盒"
                            }
                            ruleType == DinoxRuleType.ONE_TO_ONE -> selectedSources.first()
                            else -> "已选 ${selectedSources.size} 个：${selectedSources.joinToString("、")}"
                        }
                        Text(label, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("▾", style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                        when {
                            availableRemoteBoxes.isEmpty() && boxRefresh.refreshing -> DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(end = 10.dp).size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text("正在获取卡片盒…")
                                    }
                                },
                                onClick = {},
                            )
                            availableRemoteBoxes.isEmpty() && boxRefresh.lastError != null -> DropdownMenuItem(
                                text = { Text("${boxRefresh.lastError.orEmpty()}（点此重试）", color = MaterialTheme.colorScheme.error) },
                                onClick = { onRequestBoxes(true) },
                            )
                            else -> {
                                if (boxRefresh.refreshing) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "正在后台更新卡片盒列表…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        onClick = {},
                                    )
                                }
                                if (boxRefresh.lastError != null && availableRemoteBoxes.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "刷新失败：${boxRefresh.lastError.orEmpty()}（已保留缓存，点此重试）",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = { onRequestBoxes(true) },
                                    )
                                }
                                if (availableRemoteBoxes.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Dinox 里还没有卡片盒（点此刷新）") },
                                        onClick = { onRequestBoxes(true) },
                                    )
                                }
                                availableRemoteBoxes.forEach { name ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (ruleType == DinoxRuleType.MANY_TO_ONE) {
                                                    Checkbox(
                                                        checked = name in selectedSources,
                                                        onCheckedChange = null,
                                                        modifier = Modifier.padding(end = 4.dp),
                                                    )
                                                }
                                                Text(name)
                                            }
                                        },
                                        onClick = {
                                            errorText = null
                                            if (ruleType == DinoxRuleType.ONE_TO_ONE) {
                                                selectedSources = listOf(name)
                                                sourceMenuOpen = false
                                            } else {
                                                selectedSources = if (name in selectedSources) {
                                                    selectedSources - name
                                                } else {
                                                    selectedSources + name
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (availableRemoteBoxes.isEmpty() && !boxRefresh.refreshing && boxRefresh.lastError != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            boxRefresh.lastError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRequestBoxes(true) }) {
                            Text("重试")
                        }
                    }
                }

                Text("目标：本机卡片盒", style = MaterialTheme.typography.labelLarge)
                if (ruleType == DinoxRuleType.ONE_TO_ONE) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = followDinoxName,
                            onClick = {
                                followDinoxName = true
                                errorText = null
                            },
                            label = { Text("沿用 Dinox 卡片盒名称") },
                        )
                        FilterChip(
                            selected = !followDinoxName,
                            onClick = {
                                followDinoxName = false
                                errorText = null
                            },
                            label = { Text("指定本机卡片盒") },
                        )
                    }
                }
                if (ruleType == DinoxRuleType.ONE_TO_ONE && followDinoxName) {
                    Text(
                        "同步时检查本机有没有同名卡片盒：没有则自动新建同名盒，已有则直接写入该盒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box {
                        OutlinedButton(
                            onClick = { targetMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val name = targetBoxId?.let { id -> localBoxes.firstOrNull { it.id == id }?.name }
                            Text(
                                name?.let { "本机目标：$it" } ?: "选择本机已有卡片盒",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Text("▾", style = MaterialTheme.typography.labelLarge)
                        }
                        DropdownMenu(expanded = targetMenuOpen, onDismissRequest = { targetMenuOpen = false }) {
                            if (localBoxes.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("还没有本机卡片盒，请先到 设置 → 卡片盒管理 创建") },
                                    onClick = {},
                                )
                            }
                            localBoxes.forEach { box ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (box.id == NoteBox.DEFAULT_ID) "${box.name}（默认）" else box.name)
                                    },
                                    onClick = {
                                        targetBoxId = box.id
                                        targetMenuOpen = false
                                        errorText = null
                                    },
                                )
                            }
                        }
                    }
                }

                Text("同步方式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DinoxRuleSyncMode.entries.forEach { option ->
                        FilterChip(
                            selected = syncMode == option,
                            onClick = {
                                syncMode = option
                                errorText = null
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (syncMode == DinoxRuleSyncMode.MANUAL) {
                    Text(
                        "保存后可在规则卡片上手动执行「全量同步」或「增量同步」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("自动同步间隔", style = MaterialTheme.typography.labelMedium)
                    val parsedMinutes = intervalText.trim().toIntOrNull()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AutoSyncIntervalPresets.forEach { (minutes, label) ->
                            FilterChip(
                                selected = parsedMinutes == minutes,
                                onClick = {
                                    intervalText = minutes.toString()
                                    errorText = null
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { raw ->
                            intervalText = raw.filter { it.isDigit() }.take(5)
                            errorText = null
                        },
                        label = { Text("自定义间隔（分钟，至少 ${DinoxSyncRuleTargetResolver.MIN_AUTO_INTERVAL_MINUTES}）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                errorText?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val minutes = if (syncMode == DinoxRuleSyncMode.AUTO) {
                        intervalText.trim().toIntOrNull() ?: 0
                    } else {
                        0
                    }
                    if (syncMode == DinoxRuleSyncMode.AUTO && minutes < DinoxSyncRuleTargetResolver.MIN_AUTO_INTERVAL_MINUTES) {
                        errorText = "自动同步间隔至少为 ${DinoxSyncRuleTargetResolver.MIN_AUTO_INTERVAL_MINUTES} 分钟"
                        return@TextButton
                    }
                    val draft = DinoxSyncRule(
                        id = initial?.id.orEmpty(),
                        ruleType = ruleType.name,
                        sourceBoxesJson = DinoxSyncRuleCodec.encodeSourceBoxes(selectedSources),
                        targetBoxId = if (ruleType == DinoxRuleType.ONE_TO_ONE && followDinoxName) "" else targetBoxId.orEmpty(),
                        followDinoxName = ruleType == DinoxRuleType.ONE_TO_ONE && followDinoxName,
                        syncMode = syncMode.name,
                        autoIntervalMinutes = minutes,
                    )
                    val validationError = DinoxSyncRuleTargetResolver.validate(draft)
                    if (validationError != null) {
                        errorText = validationError
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        val saveErr = onSave(draft)
                        if (saveErr != null) {
                            errorText = saveErr
                        }
                        saving = false
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") }
        },
    )
}
