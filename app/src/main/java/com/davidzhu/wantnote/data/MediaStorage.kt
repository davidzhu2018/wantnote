package com.davidzhu.wantnote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MediaStorage(private val context: Context) {
    private val mediaDir: File
        get() = File(context.filesDir, "media").apply { mkdirs() }

    fun fileFor(relativePath: String): File {
        return File(context.filesDir, relativePath)
    }

    fun importImage(uri: Uri, compress: Boolean): String {
        return if (compress) {
            importCompressedImage(uri)
        } else {
            copyRaw(uri, guessExtension(uri, "jpg"))
        }
    }

    fun importVideo(uri: Uri): String {
        return copyRaw(uri, guessExtension(uri, "mp4"))
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
}
