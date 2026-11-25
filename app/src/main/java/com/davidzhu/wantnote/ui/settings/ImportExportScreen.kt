package com.davidzhu.wantnote.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.davidzhu.wantnote.ui.components.WantAlertDialog
import com.davidzhu.wantnote.ui.components.WantButton
import com.davidzhu.wantnote.ui.components.WantLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.davidzhu.wantnote.ui.components.WantTextButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.davidzhu.wantnote.ui.components.WantTab
import com.davidzhu.wantnote.ui.components.WantTabRow
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.transfer.ExportBundle
import com.davidzhu.wantnote.transfer.ImportFailure
import com.davidzhu.wantnote.ui.LocalTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                0 -> ImportTab(snackbar = snackbar)
                1 -> ExportTab(snackbar = snackbar)
                else -> EcosystemTab(snackbar = snackbar)
            }
        }
    }
}

/** 导入 Tab：本地 Markdown / HTML / ZIP 文件导入（原「导入&导出」的导入半边）。 */
@Composable
private fun ImportTab(snackbar: SnackbarHostState) {
    val transfer = LocalTransfer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                transfer.importUris(uris) { processed, count, label ->
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
                if (result.success > 0) {
                    snackbar.showSnackbar("已导入 ${result.success} 篇笔记$missing")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "支持单个或多个 .md / .markdown / .html / .htm，也支持把这些文件打成的 .zip。每个文件生成一篇笔记；zip 里的图片/附件若被引用，会一并拷入媒体库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        SectionTitle("选择文件")
        WantTextButton(
            onClick = {
                if (busy) return@WantTextButton
                multiLauncher.launch(arrayOf("*/*"))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("选择 Markdown / HTML 文件") }
        Spacer(modifier = Modifier.height(8.dp))
        WantTextButton(
            onClick = {
                if (busy) return@WantTextButton
                zipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("选择 ZIP 压缩包") }

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

        Spacer(modifier = Modifier.height(24.dp))
        SectionTitle("说明")
        Text(
            "Markdown 块映射：# 标题、段落、- [ ] / - [x] 任务、1. 有序列表、- 无序列表、--- 分割线、缩进；行内 **粗体**、*斜体*、==高亮==、~~删除线~~ 会原样保留。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** 导出 Tab：全部笔记导出为 Markdown / ZIP（原「导入&导出」的导出半边）。 */
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
