package com.davidzhu.wantnote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.DayNightMode
import com.davidzhu.wantnote.data.ThemePalette

val Paper = Color(0xFFF7F2EA)
val Ink = Color(0xFF3F2A1A)
val InkMuted = Color(0xFF8A7A6B)
val HighlightYellow = Color(0xFFFFE08A)
val AccentLine = Color(0xFFD9CBB8)

data class WantNoteColors(
    val highlight: Color,
)

val LocalWantNoteColors = staticCompositionLocalOf {
    WantNoteColors(highlight = HighlightYellow)
}

val LocalFontScale = staticCompositionLocalOf { 1.0f }

private data class Palette(
    val lightBg: Color,
    val lightInk: Color,
    val lightMuted: Color,
    val lightVariant: Color,
    val lightOutline: Color,
    val darkBg: Color,
    val darkInk: Color,
    val darkMuted: Color,
    val darkVariant: Color,
    val darkOutline: Color,
    val highlight: Color,
)

private val Palettes = mapOf(
    ThemePalette.PAPER to Palette(
        lightBg = Color(0xFFF7F2EA),
        lightInk = Color(0xFF3F2A1A),
        lightMuted = Color(0xFF8A7A6B),
        lightVariant = Color(0xFFF1E8DC),
        lightOutline = Color(0xFFD9CBB8),
        darkBg = Color(0xFF1C1712),
        darkInk = Color(0xFFF0E6D8),
        darkMuted = Color(0xFFB4A392),
        darkVariant = Color(0xFF2A231C),
        darkOutline = Color(0xFF4A3F34),
        highlight = Color(0xFFE8C04A),
    ),
    ThemePalette.INK to Palette(
        lightBg = Color(0xFFF3F3F1),
        lightInk = Color(0xFF1A1A1A),
        lightMuted = Color(0xFF6F6F6F),
        lightVariant = Color(0xFFE7E7E4),
        lightOutline = Color(0xFFCCCCC8),
        darkBg = Color(0xFF121212),
        darkInk = Color(0xFFEDEDED),
        darkMuted = Color(0xFFA3A3A3),
        darkVariant = Color(0xFF1E1E1E),
        darkOutline = Color(0xFF3A3A3A),
        highlight = Color(0xFFE8C04A),
    ),
    ThemePalette.SAGE to Palette(
        lightBg = Color(0xFFEEF3EE),
        lightInk = Color(0xFF1E3A2F),
        lightMuted = Color(0xFF5E7A6E),
        lightVariant = Color(0xFFDCE8DF),
        lightOutline = Color(0xFFC3D2C7),
        darkBg = Color(0xFF121A16),
        darkInk = Color(0xFFD8E6DC),
        darkMuted = Color(0xFF9BB3A6),
        darkVariant = Color(0xFF1C2922),
        darkOutline = Color(0xFF33463C),
        highlight = Color(0xFFC5DD7A),
    ),
    ThemePalette.ROSE to Palette(
        lightBg = Color(0xFFF8EEEE),
        lightInk = Color(0xFF3A1F24),
        lightMuted = Color(0xFF8A6A70),
        lightVariant = Color(0xFFF1DEE1),
        lightOutline = Color(0xFFE0C6CB),
        darkBg = Color(0xFF1A1214),
        darkInk = Color(0xFFF0DDE0),
        darkMuted = Color(0xFFC4A8AD),
        darkVariant = Color(0xFF2A1C1F),
        darkOutline = Color(0xFF4A3338),
        highlight = Color(0xFFF0B4C0),
    ),
)

@Composable
fun WantNoteTheme(
    settings: AppSettings = AppSettings.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = Palettes[settings.palette] ?: Palettes.getValue(ThemePalette.PAPER)
    val dark = when (settings.dayNightMode) {
        DayNightMode.LIGHT -> false
        DayNightMode.DARK -> true
        DayNightMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scale = settings.fontScaleOption.scale
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.darkInk,
            onPrimary = palette.darkBg,
            background = palette.darkBg,
            onBackground = palette.darkInk,
            surface = palette.darkBg,
            onSurface = palette.darkInk,
            surfaceVariant = palette.darkVariant,
            onSurfaceVariant = palette.darkMuted,
            outline = palette.darkOutline,
            tertiary = palette.highlight,
            secondary = palette.darkMuted,
            onSecondary = palette.darkBg,
        )
    } else {
        lightColorScheme(
            primary = palette.lightInk,
            onPrimary = palette.lightBg,
            background = palette.lightBg,
            onBackground = palette.lightInk,
            surface = palette.lightBg,
            onSurface = palette.lightInk,
            surfaceVariant = palette.lightVariant,
            onSurfaceVariant = palette.lightMuted,
            outline = palette.lightOutline,
            tertiary = palette.highlight,
            secondary = palette.lightMuted,
            onSecondary = palette.lightBg,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = scheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalWantNoteColors provides WantNoteColors(highlight = palette.highlight),
        LocalFontScale provides scale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = scaledTypography(scale),
            content = content,
        )
    }
}

private fun scaledTypography(scale: Float): Typography {
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.scaled() = copy(
        fontSize = (fontSize.value * scale).sp,
        lineHeight = (lineHeight.value * scale).sp,
    )
    return base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}
