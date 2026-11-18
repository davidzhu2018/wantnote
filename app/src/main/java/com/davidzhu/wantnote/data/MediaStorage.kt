package com.davidzhu.wantnote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class MediaStorage(private val context: Context) {
    private val root: File
        get() = AppStorage.baseDir(context)

    private val mediaDir: File
        get() = File(root, "media").apply { mkdirs() }

    private val previewCache = LruCache<String, Bitmap>(32)

    fun fileFor(relativePath: String): File {
        return File(root, relativePath)
    }

    fun importImage(uri: Uri, compress: Boolean): String {
        return if (compress) {
            importCompressedImage(uri)
        } else {
            copyRaw(uri, guessExtension(uri, "jpg"))
        }
    }

    fun importVideo(uri: Uri, compress: Boolean = false): String {
        // Full transcode is not implemented in this package; the flag is
        // accepted so callers honor the setting. The source file is copied.
        return copyRaw(uri, guessExtension(uri, "mp4"))
    }

    fun importFile(uri: Uri): String {
        return copyRaw(uri, guessExtension(uri, "bin"))
    }

    private fun copyRaw(uri: Uri, extension: String): String {
        val name = "${UUID.randomUUID()}.$extension"
        val dest = File(mediaDir, name)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选文件" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return "media/$name"
    }

    private fun importCompressedImage(uri: Uri): String {
        val source = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选图片" }
            BitmapFactory.decodeStream(input)
        }
        requireNotNull(source) { "无法解码所选图片" }
        val rotated = applyExifRotation(uri, source)
        val scaled = scaleDown(rotated, 1600)
        val name = "${UUID.randomUUID()}.jpg"
        val dest = File(mediaDir, name)
        FileOutputStream(dest).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        if (scaled !== rotated && scaled !== source) scaled.recycle()
        return "media/$name"
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun decodePreview(relativePath: String, maxSide: Int = 1080): Bitmap? {
        val file = fileFor(relativePath)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val sample = Integer.highestOneBit((longest / maxSide).coerceAtLeast(1))
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    fun decodeThumbnail(relativePath: String, isVideo: Boolean, maxSide: Int = 360): Bitmap? {
        val key = "$relativePath|$isVideo|$maxSide"
        previewCache.get(key)?.let { return it }
        val decoded = if (isVideo) {
            decodeVideoThumbnail(relativePath, maxSide)
        } else {
            decodePreview(relativePath, maxSide)
        }
        if (decoded != null) {
            previewCache.put(key, decoded)
        }
        return decoded
    }

    fun decodeVideoThumbnail(relativePath: String, maxSide: Int = 360): Bitmap? {
        val file = fileFor(relativePath)
        if (!file.exists()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (frame == null) null else scaleDown(frame, maxSide)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun guessExtension(uri: Uri, fallback: String): String {
        val type = context.contentResolver.getType(uri).orEmpty().lowercase()
        val fromType = when {
            type.contains("png") -> "png"
            type.contains("jpeg") || type.contains("jpg") -> "jpg"
            type.contains("webp") -> "webp"
            type.contains("gif") -> "gif"
            type.contains("mp4") -> "mp4"
            type.contains("webm") -> "webm"
            type.contains("3gp") -> "3gp"
            type.contains("mkv") -> "mkv"
            else -> null
        }
        if (fromType != null) return fromType
        val path = uri.lastPathSegment.orEmpty()
        val dot = path.lastIndexOf('.')
        if (dot in 0 until path.lastIndex) {
            val ext = path.substring(dot + 1).lowercase().filter { it.isLetterOrDigit() }
            if (ext.isNotEmpty() && ext.length <= 5) return ext
        }
        return fallback
    }

    fun fontsDir(): File = File(root, "fonts").apply { mkdirs() }

    fun importFont(uri: Uri): String {
        val ext = guessExtension(uri, "ttf").lowercase()
        val safeExt = if (ext in setOf("ttf", "otf", "ttc", "otc")) ext else "ttf"
        val name = "${UUID.randomUUID()}.$safeExt"
        val dest = File(fontsDir(), name)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取所选字体" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        require(dest.length() > 0L) { "字体文件为空" }
        return "fonts/$name"
    }

    fun listMediaFiles(): List<File> {
        val dir = mediaDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
    }

    fun relativePathOf(file: File): String {
        val root = AppStorage.relativeRoot(context)
        val absolute = file.absolutePath
        return if (absolute.startsWith(root)) absolute.removePrefix(root) else "media/${file.name}"
    }

    fun deleteRelative(relativePath: String): Boolean {
        val file = fileFor(relativePath)
        return file.exists() && file.delete()
    }

    fun sha256(relativePath: String): String {
        val file = fileFor(relativePath)
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    fun writeIncoming(relativePath: String, input: java.io.InputStream) {
        val dest = fileFor(relativePath)
        dest.parentFile?.mkdirs()
        dest.outputStream().use { output -> input.copyTo(output) }
    }

    enum class AttachmentKind { FILE, IMAGE, VIDEO }

    data class StoredAttachment(
        val relativePath: String,
        val file: File,
        val kind: AttachmentKind,
        val size: Long,
        val referenced: Boolean,
        val noteIds: List<String>,
    )

    companion object {
        fun kindOf(relativePath: String, blockType: String? = null): AttachmentKind {
            if (blockType == BlockType.IMAGE.name) return AttachmentKind.IMAGE
            if (blockType == BlockType.VIDEO.name) return AttachmentKind.VIDEO
            if (blockType == BlockType.FILE.name) return AttachmentKind.FILE
            val ext = relativePath.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> AttachmentKind.IMAGE
                "mp4", "webm", "3gp", "mkv", "mov", "avi", "m4v" -> AttachmentKind.VIDEO
                else -> AttachmentKind.FILE
            }
        }
    }

}
