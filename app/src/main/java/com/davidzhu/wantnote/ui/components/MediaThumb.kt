package com.davidzhu.wantnote.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Videocam
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.data.BlockType
import com.davidzhu.wantnote.data.NoteBlock
import com.davidzhu.wantnote.ui.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MediaThumb(
    path: String,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val media = LocalMedia.current
    val bitmap by produceState<Bitmap?>(initialValue = null, path, isVideo) {
        value = withContext(Dispatchers.IO) { media.decodeThumbnail(path, isVideo) }
    }
    val image = bitmap
    Box(
        modifier = modifier.clip(RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = if (isVideo) "视频缩略图" else "图片缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
            if (isVideo) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                    modifier = Modifier.size(28.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image,
                    contentDescription = if (isVideo) "视频" else "图片",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun NoteMediaThumb(
    media: NoteBlock?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    val block = media ?: return
    MediaThumb(
        path = block.mediaPath,
        isVideo = block.blockType == BlockType.VIDEO,
        modifier = modifier,
        corner = corner,
    )
}
