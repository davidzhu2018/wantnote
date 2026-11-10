package com.davidzhu.wantnote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF7F2EA)
val Ink = Color(0xFF3F2A1A)
val InkMuted = Color(0xFF8A7A6B)
val HighlightYellow = Color(0xFFFFE08A)
val AccentLine = Color(0xFFD9CBB8)

private val colors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1E8DC),
    onSurfaceVariant = InkMuted,
    outline = AccentLine,
    tertiary = HighlightYellow,
)

@Composable
fun WantNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
