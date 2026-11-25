package com.davidzhu.wantnote.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.ui.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 0.10.0：块级图片统一渲染——本地附件与 http(s) 远程图片都真实显示。
 * 编辑器（所见即所得 IMAGE 块）与源码模式预览共用；加载失败时给出可读
 * 的失败提示（含来源），不再留下原始 Markdown 源码。
 */
@Composable
fun NoteImage(
    path: String,
    modifier: Modifier = Modifier,
    maxSide: Int = 1080,
    fallbackLabel: String = "图片无法显示",
) {
    val media = LocalMedia.current
    val bitmap by produceState<Bitmap?>(initialValue = null, path, maxSide) {
        value = withContext(Dispatchers.IO) { media.loadPreview(path, maxSide) }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "图片",
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$fallbackLabel（$path）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
