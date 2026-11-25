package com.davidzhu.wantnote.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.davidzhu.wantnote.data.AppSettings
import com.davidzhu.wantnote.data.DayNightMode
import com.davidzhu.wantnote.data.ThemeColorSet
import com.davidzhu.wantnote.data.ThemeCustoms
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

/**
 * 内置主题的出厂配色（0.9.0 起「内置主题配色」允许被覆盖，
 * 覆盖值持久化在 AppSettings.themeCustomJson，可一键重置）。
 */
fun builtinColorSet(palette: ThemePalette): ThemeColorSet {
    val p = Palettes[palette] ?: return ThemeColorSet()
    return ThemeColorSet(
        lightBg = p.lightBg.toArgb().toLong() and 0xFFFFFFFFL,
        lightInk = p.lightInk.toArgb().toLong() and 0xFFFFFFFFL,
        lightMuted = p.lightMuted.toArgb().toLong() and 0xFFFFFFFFL,
        lightVariant = p.lightVariant.toArgb().toLong() and 0xFFFFFFFFL,
        lightOutline = p.lightOutline.toArgb().toLong() and 0xFFFFFFFFL,
        darkBg = p.darkBg.toArgb().toLong() and 0xFFFFFFFFL,
        darkInk = p.darkInk.toArgb().toLong() and 0xFFFFFFFFL,
        darkMuted = p.darkMuted.toArgb().toLong() and 0xFFFFFFFFL,
        darkVariant = p.darkVariant.toArgb().toLong() and 0xFFFFFFFFL,
        darkOutline = p.darkOutline.toArgb().toLong() and 0xFFFFFFFFL,
        highlight = p.highlight.toArgb().toLong() and 0xFFFFFFFFL,
    )
}

/** ARGB long → Compose Color（主题编辑器与 ThemeCustom 存储用）。 */
fun colorFromArgb(value: Long): Color = Color(value.toInt())

/**
 * 解析当前主题的生效配色：
 * - 内置主题：出厂色 + themeCustomJson 中的覆盖（可重置）。
 * - USER_ 前缀自建主题：自建值缺省回退 PAPER 出厂色。
 * - 未知/被删主题：fromStorage 回退 PAPER 出厂色，保证永远有可用配色。
 */
private fun resolvePalette(
    themeName: String,
    customs: Map<String, ThemeColorSet>,
): Palette {
    val paper = Palettes.getValue(ThemePalette.PAPER)
    val resolved = ThemeCustoms.resolve(
        themeName = themeName,
        builtIn = builtinColorSet(ThemePalette.fromStorage(themeName)),
        customs = customs,
        fallback = builtinColorSet(ThemePalette.PAPER),
    ) ?: return paper
    fun argb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
    fun pick(value: Long?, base: Color): Color = colorFromArgb(value ?: argb(base))
    return Palette(
        lightBg = pick(resolved.lightBg, paper.lightBg),
        lightInk = pick(resolved.lightInk, paper.lightInk),
        lightMuted = pick(resolved.lightMuted, paper.lightMuted),
        lightVariant = pick(resolved.lightVariant, paper.lightVariant),
        lightOutline = pick(resolved.lightOutline, paper.lightOutline),
        darkBg = pick(resolved.darkBg, paper.darkBg),
        darkInk = pick(resolved.darkInk, paper.darkInk),
        darkMuted = pick(resolved.darkMuted, paper.darkMuted),
        darkVariant = pick(resolved.darkVariant, paper.darkVariant),
        darkOutline = pick(resolved.darkOutline, paper.darkOutline),
        highlight = pick(resolved.highlight, paper.highlight),
    )
}

@Composable
fun WantNoteTheme(
    settings: AppSettings = AppSettings.DEFAULT,
    fontFamily: FontFamily = FontFamily.Default,
    content: @Composable () -> Unit,
) {
    val customs = remember(settings.themeCustomJson) { ThemeCustoms.parse(settings.themeCustomJson) }
    val palette = resolvePalette(settings.themeName, customs)
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

    // 0.13.0：整套 chrome 换成 Miuix（小米 HyperOS 风）。MiuixTheme 是应用主题，
    // 用户主题（配色 / 日夜 / 字体缩放）先解析成同一套 palette，再映射进 Miuix 的
    // Colors；MaterialTheme 留在内层只作为自定义版面（Markdown / 图谱 / 卡片）
    // 的 typography 与色值 token，保证两者永远一致，不会出现两套视觉语言。
    val miuixColors = remember(palette, dark) { miuixColorsFor(palette, dark) }
    val miuixTextStyles = remember(scale, fontFamily) { scaledTextStyles(scale, fontFamily) }
    CompositionLocalProvider(
        LocalWantNoteColors provides WantNoteColors(highlight = palette.highlight),
        LocalFontScale provides scale,
        LocalEditorFontFamily provides fontFamily,
    ) {
        MiuixTheme(colors = miuixColors, textStyles = miuixTextStyles) {
            MaterialTheme(
                colorScheme = scheme,
                typography = scaledTypography(scale, fontFamily),
                content = content,
            )
        }
    }
}

/**
 * WantNote palette → Miuix [MiuixColors]。
 *
 * Miuix 的语义槽位（primary 用于开关 / 按钮 / 聚焦描边，secondaryVariant 是默认
 * 按钮底色，surfaceContainer 是卡片 / 弹窗底色，secondaryContainer 是输入框底色）
 * 全部从用户当前主题解析出来的 palette 取值，所以换肤、日夜、字体缩放都不受影响。
 */
private fun miuixColorsFor(palette: Palette, dark: Boolean): MiuixColors {
    val bg = if (dark) palette.darkBg else palette.lightBg
    val ink = if (dark) palette.darkInk else palette.lightInk
    val muted = if (dark) palette.darkMuted else palette.lightMuted
    val variant = if (dark) palette.darkVariant else palette.lightVariant
    val outline = if (dark) palette.darkOutline else palette.lightOutline
    val error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val onError = if (dark) Color(0xFF601410) else Color.White
    return if (dark) {
        miuixDarkColorScheme(
            primary = ink,
            onPrimary = bg,
            primaryVariant = ink,
            onPrimaryVariant = bg,
            primaryContainer = variant,
            onPrimaryContainer = ink,
            error = error,
            onError = onError,
            errorContainer = variant,
            onErrorContainer = ink,
            secondary = variant,
            onSecondary = ink,
            secondaryVariant = variant,
            onSecondaryVariant = ink,
            secondaryContainer = variant,
            onSecondaryContainer = muted,
            secondaryContainerVariant = variant,
            onSecondaryContainerVariant = muted,
            tertiaryContainer = variant,
            onTertiaryContainer = palette.highlight,
            tertiaryContainerVariant = variant,
            background = bg,
            onBackground = ink,
            onBackgroundVariant = muted,
            surface = bg,
            onSurface = ink,
            surfaceVariant = variant,
            onSurfaceSecondary = muted,
            onSurfaceVariantSummary = muted,
            onSurfaceVariantActions = muted,
            surfaceContainer = variant,
            onSurfaceContainer = ink,
            onSurfaceContainerVariant = muted,
            surfaceContainerHigh = variant,
            onSurfaceContainerHigh = ink,
            surfaceContainerHighest = variant,
            onSurfaceContainerHighest = ink,
            outline = outline,
            dividerLine = outline.copy(alpha = 0.6f),
        )
    } else {
        miuixLightColorScheme(
            primary = ink,
            onPrimary = bg,
            primaryVariant = ink,
            onPrimaryVariant = bg,
            primaryContainer = variant,
            onPrimaryContainer = ink,
            error = error,
            onError = onError,
            errorContainer = variant,
            onErrorContainer = ink,
            secondary = variant,
            onSecondary = ink,
            secondaryVariant = variant,
            onSecondaryVariant = ink,
            secondaryContainer = variant,
            onSecondaryContainer = muted,
            secondaryContainerVariant = variant,
            onSecondaryContainerVariant = muted,
            tertiaryContainer = variant,
            onTertiaryContainer = palette.highlight,
            tertiaryContainerVariant = variant,
            background = bg,
            onBackground = ink,
            onBackgroundVariant = muted,
            surface = bg,
            onSurface = ink,
            surfaceVariant = variant,
            onSurfaceSecondary = muted,
            onSurfaceVariantSummary = muted,
            onSurfaceVariantActions = muted,
            surfaceContainer = variant,
            onSurfaceContainer = ink,
            onSurfaceContainerVariant = muted,
            surfaceContainerHigh = variant,
            onSurfaceContainerHigh = ink,
            surfaceContainerHighest = variant,
            onSurfaceContainerHighest = ink,
            outline = outline,
            dividerLine = outline.copy(alpha = 0.6f),
        )
    }
}

private fun scaledTypography(scale: Float, fontFamily: FontFamily): Typography {
    val base = Typography()
    fun androidx.compose.ui.text.TextStyle.scaled() = copy(
        fontFamily = fontFamily,
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

/**
 * Miuix 的字体令牌同样吃用户字号缩放与自定义字体，
 * 保证 chrome（顶栏 / 按钮 / 弹窗 / 列表）与自绘版面字重字号一致。
 */
private fun scaledTextStyles(scale: Float, fontFamily: FontFamily): TextStyles {
    val base = defaultTextStyles()
    fun TextStyle.scaled() = copy(
        fontFamily = fontFamily,
        fontSize = (fontSize.value * scale).sp,
        lineHeight = if (lineHeight == TextUnit.Unspecified) {
            lineHeight
        } else {
            (lineHeight.value * scale).sp
        },
    )
    return base.copy(
        main = base.main.scaled(),
        paragraph = base.paragraph.scaled(),
        body1 = base.body1.scaled(),
        body2 = base.body2.scaled(),
        button = base.button.scaled(),
        footnote1 = base.footnote1.scaled(),
        footnote2 = base.footnote2.scaled(),
        headline1 = base.headline1.scaled(),
        headline2 = base.headline2.scaled(),
        subtitle = base.subtitle.scaled(),
        title1 = base.title1.scaled(),
        title2 = base.title2.scaled(),
        title3 = base.title3.scaled(),
        title4 = base.title4.scaled(),
    )
}
