package com.davidzhu.wantnote.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.EditorFontOption
import com.davidzhu.wantnote.data.AppStorage
import java.io.File

val LocalEditorFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

fun loadEditorFontFamily(context: Context, settings: AppSettings): FontFamily {
    return try {
        when (val option = settings.editorFont) {
            EditorFontOption.SYSTEM -> FontFamily.Default
            EditorFontOption.CUSTOM -> {
                val relative = settings.customFontPath
                if (relative.isBlank()) {
                    FontFamily.Default
                } else {
                    val file = File(AppStorage.baseDir(context), relative)
                    if (!file.exists()) FontFamily.Default else FontFamily(Font(file = file, weight = FontWeight.Normal))
                }
            }
            else -> {
                val path = option.assetPath ?: return FontFamily.Default
                FontFamily(Font(path, context.assets, FontWeight.Normal))
            }
        }
    } catch (_: Exception) {
        FontFamily.Default
    }
}
