package com.davidzhu.wantnote.ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.NoteBox
import com.davidzhu.wantnote.transfer.ExportBundle
import com.davidzhu.wantnote.transfer.ImportFailure
import com.davidzhu.wantnote.transfer.ImportSourceMode
import com.davidzhu.wantnote.ui.LocalNotes
import com.davidzhu.wantnote.ui.LocalTransfer
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantDropdownMenu
import com.davidzhu.wantnote.ui.components.WantDropdownMenuItem
import com.davidzhu.wantnote.ui.components.WantLinearProgressIndicator
import com.davidzhu.wantnote.ui.components.WantListItem
import com.davidzhu.wantnote.ui.components.WantTab
import com.davidzhu.wantnote.ui.components.WantTabRow
import com.davidzhu.wantnote.ui.components.WantTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 0.10.0：「数据管理」外层 Tab 拆为 导入 / 导出 / 生态互联 三个。 */
private val DataTabs = listOf("导入", "导出", "生态互联")

@Composable
fun ImportExportScreen(onBack: () -> Unit) {
    var dataTabState by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    SettingsSubScaffold(title = "数据管理", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WantTabRow(selectedTabIndex = dataTabState) {
                DataTabs.forEachIndexed { index, label ->
                    WantTab(
                        selected = dataTabState == index,
                        onClick = { dataTabState = index },
                        text = { Text(label) },
                    )
                }
            }
            when (dataTabState) {
                0 -> ImportHubTab(snackbar = snackbar)
                1 -> ExportTab(snackbar = snackbar)
                else -> EcosystemHubTab(snackbar = snackbar)
            }
        }
    }
}

/**
 * 0.19.0：导入功能两级菜单。
 * 一级菜单：通用导入、flomo压缩包导入、得到大脑压缩包导入、喵记多压缩包导入、Seeds压缩包导入。
 */
@Composable
private fun ImportHubTab(snackbar: SnackbarHostState) {
    var selectedMode by remember { mutableStateOf<ImportSourceMode?>(null) }

    BackHandler(enabled = selectedMode != null) {
        selectedMode = null
    }

    val mode = selectedMode
    if (mode == null) {
        val titleStyle = MiuixTheme.textStyles.body1
        val descSizeSp = (titleStyle.fontSize.value - 2f).coerceAtLeast(11f).sp
        val entries = listOf(
            Triple(ImportSourceMode.GENERAL, "通用导入", "支持 .md / .html / .zip，完整支持 Markdown 语法与双链，可选目标卡片盒"),
            Triple(ImportSourceMode.FLOMO_ZIP, "flomo压缩包导入", "选择 flomo 导出的 ZIP 包（内含 HTML，自动转 Markdown 并归入所选卡片盒）"),
            Triple(ImportSourceMode.DEDAO_ZIP, "得到大脑压缩包导入", "选择得到大脑导出的 ZIP 包（内含 HTML，自动转 Markdown 并归入所选卡片盒）"),
            Triple(ImportSourceMode.MIAOJIDUO_ZIP, "喵记多压缩包导入", "选择喵记多导出的 ZIP 包（同时支持包内 HTML 与 Markdown 文件）"),
            Triple(ImportSourceMode.SEEDS_ZIP, "Seeds压缩包导入", "选择 Seeds 导出的 ZIP 包（支持包内 Markdown 文件与附件）"),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "选择导入来源，所有导入方式均支持指定导入到目标卡片盒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
            ) {
                entries.forEachIndexed { idx, (m, title, subtitle) ->
                    WantListItem(
                        headlineContent = {
                            Text(
                                text = title,
                                style = titleStyle,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = subtitle,
                                style = MiuixTheme.textStyles.footnote1.copy(fontSize = descSizeSp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = m },
                    )
                    if (idx < entries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedMode = null }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回导入列表")
                Text(
                    text = "导入 / ${mode.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ImportModeSubPage(mode = mode, snackbar = snackbar)
        }
    }
}

/** 通用卡片盒选择下拉框（导入、Readwise、微信读书复用）。 */
@Composable
internal fun BoxTargetDropdown(
    label: String,
    boxes: List<NoteBox>,
    selectedBoxId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = boxes.firstOrNull { it.id == selectedBoxId }?.name ?: "不指定（未分配）"
    Box(modifier = modifier.fillMaxWidth()) {
        WantTextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = selectedName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
        }
        WantDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            WantDropdownMenuItem(
                text = { Text(if (selectedBoxId.isEmpty()) "✓ 不指定（未分配）" else "不指定（未分配）") },
                onClick = {
                    expanded = false
                    onSelect("")
                },
            )
            boxes.forEach { box ->
                WantDropdownMenuItem(
                    text = { Text(if (box.id == selectedBoxId) "✓ ${box.name}" else box.name) },
                    onClick = {
                        expanded = false
                        onSelect(box.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImportModeSubPage(
    mode: ImportSourceMode,
    snackbar: SnackbarHostState,
) {
    val transfer = LocalTransfer.current
    val notesRepo = LocalNotes.current
    val boxes by notesRepo.observeBoxes().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var targetBoxId by remember(mode) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var currentFile by remember { mutableStateOf("") }
    var failures by remember { mutableStateOf<List<ImportFailure>>(emptyList()) }

    fun beginImport(uris: List<Uri>) {
        if (busy || uris.isEmpty()) {
            if (uris.isEmpty() && !busy) {
                scope.launch { snackbar.showSnackbar("没有选择文件") }
            }
            return
        }
        busy = true
        done = 0
        total = 0
        currentFile = ""
        failures = emptyList()
        scope.launch {
            runCatching {
                transfer.importUris(
                    uris = uris,
                    targetBoxId = targetBoxId,
                    mode = mode,
                ) { processed, count, label ->
                    done = processed
                    total = count
                    currentFile = label
                }
            }.onSuccess { result ->
                failures = result.failures
                val missing = if (result.missingMedia > 0) {
                    "，${result.missingMedia} 个附件未在文件中找到已跳过"
                } else {
                    ""
                }
                val boxSuffix = boxes.firstOrNull { it.id == targetBoxId }?.let { "至卡片盒「${it.name}」" }.orEmpty()
                if (result.success > 0) {
                    snackbar.showSnackbar("已导入 ${result.success} 篇笔记$boxSuffix$missing")
                } else if (result.failures.isEmpty()) {
                    snackbar.showSnackbar("没有可导入的笔记")
                }
            }.onFailure {
                snackbar.showSnackbar(it.message ?: "导入失败")
            }
            busy = false
            currentFile = ""
        }
    }

    val multiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> beginImport(uris) }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) beginImport(listOf(uri)) else {
            scope.launch { snackbar.showSnackbar("已取消选择文件") }
        }
    }

    val desc = when (mode) {
        ImportSourceMode.GENERAL ->
            "支持单个或多个 .md / .markdown / .html / .htm 及 .zip，完整保留 Markdown 语法（标题、列表、表格、代码、Mermaid、LaTeX、图片/附件）与 [[双链]]。"
        ImportSourceMode.FLOMO_ZIP ->
            "请选择从 flomo 导出的 ZIP 压缩包。包内 HTML 文件会自动拆分并转换为 Markdown 存入想记数据库，图片/附件一并导入。"
        ImportSourceMode.DEDAO_ZIP ->
            "请选择从得到大脑导出的 ZIP 压缩包。包内 HTML 文件会自动转换为 Markdown 存入想记数据库。"
        ImportSourceMode.MIAOJIDUO_ZIP ->
            "请选择从喵记多导出的 ZIP 压缩包。同时支持包内 HTML 与 Markdown 文件自动解析入库，并拷贝关联附件。"
        ImportSourceMode.SEEDS_ZIP ->
            "请选择从 Seeds 导出的 ZIP 压缩包。支持包内 Markdown 文件与附件自动解析入库。"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        BoxTargetDropdown(
            label = "导入到卡片盒",
            boxes = boxes,
            selectedBoxId = targetBoxId,
            onSelect = { targetBoxId = it },
        )

        Spacer(modifier = Modifier.height(16.dp))
        SectionTitle("选择文件")

        if (mode == ImportSourceMode.GENERAL) {
            WantTextButton(
                onClick = {
                    if (busy) return@WantTextButton
                    multiLauncher.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("选择 Markdown / HTML 文件") }
            Spacer(modifier = Modifier.height(8.dp))
            WantButton(
                onClick = {
                    if (busy) return@WantButton
                    zipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("选择 ZIP 压缩包") }
        } else {
            // flomo / 得到大脑 / 喵记多 / Seeds 压缩包导入均只提供单一「选择zip包」按钮
            WantButton(
                onClick = {
                    if (busy) return@WantButton
                    zipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("选择zip包") }
        }

        if (busy) {
            Spacer(modifier = Modifier.height(20.dp))
            if (total > 0) {
                WantLinearProgressIndicator(
                    progress = { done.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "已处理 $done / $total",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                WantLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (currentFile.isNotBlank()) {
                Text(
                    "当前：$currentFile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (failures.isNotEmpty()) {
        WantAlertDialog(
            onDismissRequest = { failures = emptyList() },
            title = { Text("部分文件导入失败") },
            text = {
                Text(
                    failures.take(10).joinToString("\n") { "${it.fileName}：${it.reason}" } +
                        if (failures.size > 10) "\n…另有 ${failures.size - 10} 个文件失败" else "",
                )
            },
            confirmButton = {
                WantTextButton(onClick = { failures = emptyList() }) { Text("知道了") }
            },
        )
    }
}

/** 导出 Tab：全部笔记导出为 Markdown / ZIP。 */
@Composable
private fun ExportTab(snackbar: SnackbarHostState) {
    val transfer = LocalTransfer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var currentFile by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<ExportBundle?>(null) }

    fun writeExport(bundle: ExportBundle, uri: Uri?, asZip: Boolean) {
        if (uri == null) {
            busy = false
            scope.launch { snackbar.showSnackbar("已取消导出") }
            return
        }
        scope.launch {
            runCatching {
                val output = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("无法写入所选位置")
                }
                withContext(Dispatchers.IO) {
                    transfer.writeExport(bundle, output, asZip)
                }
            }.onSuccess {
                snackbar.showSnackbar(
                    if (asZip) {
                        "已导出 ${bundle.notes.size} 篇笔记和 ${bundle.attachments.size} 个附件"
                    } else {
                        "已导出 Markdown 文件"
                    },
                )
            }.onFailure { snackbar.showSnackbar(it.message ?: "导出失败") }
            busy = false
            currentFile = ""
            pendingExport = null
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> writeExport(pendingExport ?: return@rememberLauncherForActivityResult, uri, asZip = true) }

    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> writeExport(pendingExport ?: return@rememberLauncherForActivityResult, uri, asZip = false) }

    fun startExport() {
        if (busy) return
        busy = true
        currentFile = "正在整理笔记…"
        scope.launch {
            runCatching { transfer.buildExport() }
                .onSuccess { bundle ->
                    if (bundle.notes.isEmpty()) {
                        busy = false
                        currentFile = ""
                        snackbar.showSnackbar("还没有可导出的笔记")
                        return@onSuccess
                    }
                    pendingExport = bundle
                    currentFile = ""
                    if (bundle.isZip) {
                        exportZipLauncher.launch(bundle.suggestedZipName)
                    } else {
                        exportMdLauncher.launch(bundle.suggestedMarkdownName)
                    }
                }
                .onFailure {
                    busy = false
                    currentFile = ""
                    snackbar.showSnackbar(it.message ?: "导出失败")
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "导出格式为 Markdown：每篇笔记一个 .md。多篇笔记或含本地附件时会打成 zip（附件放在 media/，用相对路径引用）；只有一篇且无附件时直接保存 .md。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        SectionTitle("导出全部笔记")
        WantButton(
            onClick = { startExport() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("导出 Markdown") }
        if (busy) {
            Spacer(modifier = Modifier.height(20.dp))
            WantLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                currentFile.ifBlank { "正在导出…" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
