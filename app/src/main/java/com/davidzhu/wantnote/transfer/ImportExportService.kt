package com.davidzhu.wantnote.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.davidzhu.wantnote.data.BlockFontSize
import com.davidzhu.wantnote.data.MediaStorage
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.data.NoteRepository
import com.davidzhu.wantnote.data.SearchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ImportFailure(val fileName: String, val reason: String)

data class ImportResult(
    val success: Int,
    val failures: List<ImportFailure>,
    val missingMedia: Int,
)

data class ExportNote(
    val fileName: String,
    val title: String,
    val markdown: String,
)

data class ExportBundle(
    val notes: List<ExportNote>,
    val attachments: Map<String, File>,
    val suggestedZipName: String,
    val suggestedMarkdownName: String,
) {
    val isZip: Boolean get() = notes.size > 1 || attachments.isNotEmpty()
}

/**
 * Markdown / HTML 导入与 Markdown 导出。
 *
 * 导入：每个 .md/.markdown/.html/.htm 生成一篇笔记；zip 里的文件逐个处理，
 * 单个失败只记录文件名+原因，不影响其余文件。zip 内被引用的图片/附件会
 * 拷进 media/，无法找到的资源会跳过并计入 missingMedia。
 *
 * 导出：每篇笔记一个 .md；多篇或含附件时打成 zip（附件放在 media/，
 * Markdown 用 ../media/xxx 相对引用），只有一篇且无附件时直接输出 .md。
 */
class ImportExportService(
    private val context: Context,
    private val notes: NoteRepository,
    private val media: MediaStorage,
) {
    suspend fun importUris(
        uris: List<Uri>,
        onProgress: (done: Int, total: Int, label: String) -> Unit = { _, _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val tasks = ArrayList<ImportTask>()
        val tempZips = ArrayList<File>()
        val failures = ArrayList<ImportFailure>()
        try {
            uris.forEach { uri ->
                val display = displayName(uri)
                if (isZip(uri, display)) {
                    val temp = File.createTempFile("wantnote-import-", ".zip", context.cacheDir)
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw TransferException("无法读取所选 zip")
                        input.use { source -> temp.outputStream().use { dest -> source.copyTo(dest) } }
                        tempZips += temp
                        val entryNames = ZipFile(temp).use { zip ->
                            java.util.Collections.list(zip.entries())
                                .filter { !it.isDirectory }
                                .map { it.name }
                                .filter { isSupportedName(it) && !it.startsWith("__MACOSX/") && !it.contains("/.") }
                        }
                        if (entryNames.isEmpty()) {
                            failures += ImportFailure(display, "zip 里没有 .md / .markdown / .html / .htm 文件")
                        } else {
                            entryNames.forEach { name ->
                                tasks += ImportTask(
                                    displayName = name.substringAfterLast('/'),
                                    sourceLabel = name,
                                    zipFile = temp,
                                    entryName = name,
                                    uri = null,
                                )
                            }
                        }
                    } catch (error: Exception) {
                        failures += ImportFailure(display, messageOf(error, "无法读取 zip"))
                    }
                } else if (isSupportedName(display) || isSupportedUri(uri)) {
                    tasks += ImportTask(
                        displayName = display,
                        sourceLabel = display,
                        zipFile = null,
                        entryName = null,
                        uri = uri,
                    )
                } else {
                    failures += ImportFailure(display, "不支持的文件类型，只支持 .md / .markdown / .html / .htm 或这些文件打成的 .zip")
                }
            }

            if (tasks.isEmpty()) {
                return@withContext ImportResult(0, failures, 0)
            }

            var success = 0
            var missingMedia = 0
            var done = 0
            val total = tasks.size
            tasks.forEach { task ->
                onProgress(done, total, task.sourceLabel)
                try {
                    val text = readTaskText(task)
                    if (looksBinary(text)) {
                        throw TransferException("文件包含二进制内容，不是可识别的 Markdown/HTML 文本")
                    }
                    val extension = task.displayName.substringAfterLast('.', "").lowercase()
                    val isHtml = extension == "html" || extension == "htm" ||
                        MarkdownCodec.looksLikeHtml(text)
                    val mediaCache = HashMap<String, String?>()
                    val parsed = MarkdownCodec.parseDocument(
                        text = text,
                        fallbackTitle = fileTitle(task.displayName),
                        isHtml = isHtml,
                        resolveMedia = { ref ->
                            if (task.zipFile == null) {
                                null
                            } else {
                                mediaCache.getOrPut(ref) { copyZipMedia(task, ref) }
                            }
                        },
                    )
                    if (parsed.blocks.isEmpty() && parsed.title.isBlank()) {
                        throw TransferException("没有可导入的文本内容")
                    }
                    createNote(parsed)
                    missingMedia += parsed.missingMedia.size
                    success += 1
                } catch (error: Exception) {
                    failures += ImportFailure(task.sourceLabel, messageOf(error, "导入失败"))
                }
                done += 1
                onProgress(done, total, task.sourceLabel)
            }

            ImportResult(success, failures, missingMedia)
        } finally {
            tempZips.forEach { temp -> runCatching { temp.delete() } }
        }
    }

    suspend fun buildExport(): ExportBundle = withContext(Dispatchers.IO) {
        val allNotes = notes.getAllNotes()
        val blocksByNote = notes.getAllBlocks().groupBy { it.noteId }
        val attachments = LinkedHashMap<String, File>()
        val references = HashMap<String, String>()
        val usedNames = HashSet<String>()

        allNotes.forEach { note ->
            blocksByNote[note.id].orEmpty().sortedBy { it.sortOrder }.forEach { block ->
                val path = block.mediaPath
                if (path.isBlank() || references.containsKey(path)) return@forEach
                val file = media.fileFor(path)
                if (!file.exists() || !file.isFile) return@forEach
                val name = uniqueName(file.name, usedNames)
                val zipPath = "media/$name"
                references[path] = zipPath
                attachments[zipPath] = file
            }
        }

        val noteNames = HashSet<String>()
        val exportNotes = allNotes.map { note ->
            val sourceBlocks = blocksByNote[note.id].orEmpty().sortedBy { it.sortOrder }
            val title = note.title.ifBlank { SearchText.displayTitle(note) }.trim()
            val markdownBlocks = sourceBlocks.map { block ->
                MarkdownBlock(
                    type = block.blockType,
                    text = block.text,
                    indent = block.indent,
                    checked = block.checked,
                    mediaPath = block.mediaPath,
                )
            }
            val markdown = MarkdownCodec.render(
                title = title,
                blocks = markdownBlocks,
                mediaRef = { path ->
                    references[path]?.let { "../$it" } ?: path
                },
            )
            val fileName = uniqueName(
                sanitizeFileName(title.ifBlank { "未命名笔记" }) + ".md",
                noteNames,
            )
            ExportNote(fileName = fileName, title = title, markdown = markdown)
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        ExportBundle(
            notes = exportNotes,
            attachments = attachments,
            suggestedZipName = "WantNote-导出-$stamp.zip",
            suggestedMarkdownName = exportNotes.firstOrNull()?.fileName ?: "WantNote.md",
        )
    }

    fun writeExport(bundle: ExportBundle, output: OutputStream, asZip: Boolean) {
        try {
            if (!asZip && bundle.notes.size == 1) {
                output.use { it.write(bundle.notes.first().markdown.toByteArray(StandardCharsets.UTF_8)) }
                return
            }
            ZipOutputStream(output).use { zip ->
                bundle.notes.forEach { note ->
                    zip.putNextEntry(ZipEntry("notes/${note.fileName}"))
                    zip.write(note.markdown.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
                bundle.attachments.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            throw TransferException("导出失败：${messageOf(error, "无法写入文件")}")
        }
    }

    private suspend fun createNote(parsed: MarkdownNote) {
        val now = System.currentTimeMillis()
        val noteId = UUID.randomUUID().toString()
        val noteTitle = parsed.title.ifBlank { "导入的笔记" }.take(200)
        val blocks = parsed.blocks.mapIndexed { index, parsedBlock ->
            NoteBlock(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                type = parsedBlock.type.name,
                text = parsedBlock.text,
                indent = parsedBlock.indent.coerceIn(0, 8),
                checked = parsedBlock.checked,
                fontSize = BlockFontSize.NORMAL.name,
                mediaPath = parsedBlock.mediaPath,
                sortOrder = index,
            )
        }.ifEmpty {
            listOf(
                NoteBlock.paragraph(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    text = "",
                    sortOrder = 0,
                ),
            )
        }
        val note = Note(
            id = noteId,
            title = noteTitle,
            body = SearchText.fromBlocks(blocks),
            createdAt = now,
            updatedAt = now,
        )
        notes.insertImportedNote(note, blocks)
    }

    private fun readTaskText(task: ImportTask): String {
        val bytes = if (task.zipFile != null && task.entryName != null) {
            ZipFile(task.zipFile).use { zip ->
                val entry = zip.getEntry(task.entryName)
                    ?: throw TransferException("zip 中找不到 ${task.entryName}")
                zip.getInputStream(entry).use { input ->
                    readLimited(input, MAX_TEXT_BYTES)
                }
            }
        } else {
            val uri = task.uri ?: throw TransferException("无法读取文件")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw TransferException("无法打开文件")
            input.use { readLimited(it, MAX_TEXT_BYTES) }
        }
        if (bytes.isEmpty()) return ""
        // 常见二进制文件（图片、压缩包等）含 NUL；不要硬当成 Markdown 解析。
        if (bytes.take(4096).any { it == 0.toByte() }) {
            throw TransferException("文件包含二进制内容，不是可识别的 Markdown/HTML 文本")
        }
        val utf8 = decodeUtf8Strict(bytes)
        if (utf8 != null) return utf8
        // 中文 Markdown 常见 GBK/GB18030 编码；仅在 UTF-8 解码失败时回退。
        return runCatching { String(bytes, charset("GB18030")) }.getOrDefault(
            String(bytes, StandardCharsets.UTF_8),
        )
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) {
                throw TransferException("文件过大（超过 ${limit / (1024 * 1024)} MB），已跳过")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun copyZipMedia(task: ImportTask, rawRef: String): String? {
        val zipFile = task.zipFile ?: return null
        val ref = normalizeReference(rawRef)
        if (ref.isEmpty()) return null
        ZipFile(zipFile).use { zip ->
            val all = java.util.Collections.list(zip.entries()).filter { !it.isDirectory }
            val baseName = ref.substringAfterLast('/')
            val match = all.firstOrNull { it.name == ref }
                ?: all.firstOrNull { it.name == "media/$ref" }
                ?: all.firstOrNull { it.name.substringAfterLast('/') == baseName }
                ?: return null
            val extension = MarkdownCodec.extensionOf(match.name).ifBlank { "bin" }
            val stored = "media/${UUID.randomUUID()}.$extension"
            media.writeIncoming(stored, zip.getInputStream(match))
            return stored
        }
    }

    private fun normalizeReference(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("<") && value.endsWith(">")) value = value.substring(1, value.length - 1)
        value = value.substringBefore('#').substringBefore('?')
        value = runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
        value = value.replace('\\', '/')
        while (value.startsWith("./") || value.startsWith("../")) {
            value = value.substring(2)
        }
        value = value.trimStart('/')
        return value.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }.joinToString("/")
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "导入文件"
    }

    private fun isZip(uri: Uri, name: String): Boolean {
        val lower = name.lowercase()
        if (lower.endsWith(".zip")) return true
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty().lowercase()
        return mime == "application/zip" || mime == "application/x-zip-compressed"
    }

    private fun isSupportedName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("md", "markdown", "html", "htm")
    }

    private fun isSupportedUri(uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty().lowercase()
        return mime.startsWith("text/") || mime == "application/octet-stream"
    }

    private fun fileTitle(name: String): String {
        val base = name.substringAfterLast('/').substringBeforeLast('.')
        return base.trim().ifBlank { "导入的笔记" }
    }

    private fun looksBinary(text: String): Boolean {
        return text.take(4096).any { it == '\u0000' }
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? {
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .ifBlank { "未命名笔记" }
        return cleaned.take(60)
    }

    private fun uniqueName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 2
        while (true) {
            val candidate = "$base-$index$ext"
            if (used.add(candidate)) return candidate
            index += 1
        }
    }

    private fun messageOf(error: Throwable, fallback: String): String {
        val message = error.message?.trim().orEmpty()
        return message.ifBlank { fallback }
    }

    private data class ImportTask(
        val displayName: String,
        val sourceLabel: String,
        val zipFile: File?,
        val entryName: String?,
        val uri: Uri?,
    )

    companion object {
        private const val MAX_TEXT_BYTES = 32 * 1024 * 1024
    }
}
