package com.davidzhu.wantnote.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davidzhu.wantnote.data.LiquidGlassSpec
import com.davidzhu.wantnote.data.LiquidGlassSurfaceRole
import com.davidzhu.wantnote.data.LiquidGlassTier
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 0.14.0 Q3/Q4：Miuix 之上的液态玻璃材质修饰器。
 *
 * - API 33+（Android 13+）：启用 AGSL `RuntimeShader` 液态玻璃菲涅尔折射光泽 + 镜面高光棱边 + 立体投影；
 * - API 31–32（Android 12）：启用多层半透明玻璃光泽 + 柔和折射高光渐变 + 立体投影；
 * - API 24–30（Android 7–11）：自动退路到纯 Compose 高对比立体玻璃层，不引用任何 API 31+/33+ 类。
 *
 * 严格保持 `minSdk = 24`，并完全尊重当前 `MiuixTheme` 配色、用户自定义主题与深浅模式。
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    role: LiquidGlassSurfaceRole,
    baseColor: Color = MiuixTheme.colorScheme.surface,
    elevationOverride: Dp? = null,
    borderWidth: Dp = 1.dp,
    apiLevel: Int = Build.VERSION.SDK_INT,
): Modifier {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f || isSystemInDarkTheme()
    val style = remember(apiLevel, role, isDark) {
        LiquidGlassSpec.resolve(apiLevel = apiLevel, role = role, isDark = isDark)
    }

    val fill = baseColor.copy(alpha = style.surfaceAlpha)
    val highlightBase = if (isDark) Color.White else Color.White
    val shadowEdgeBase = if (isDark) Color.Black else MiuixTheme.colorScheme.outline

    val sheenBrush = remember(fill, style.topSheenAlpha, style.bottomSheenAlpha, isDark) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to highlightBase.copy(alpha = style.topSheenAlpha),
                0.42f to Color.Transparent,
                1.0f to shadowEdgeBase.copy(alpha = style.bottomSheenAlpha),
            ),
        )
    }

    val rimBrush = remember(style.rimTopAlpha, style.rimBottomAlpha, isDark, shadowEdgeBase) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to highlightBase.copy(alpha = style.rimTopAlpha),
                0.35f to highlightBase.copy(alpha = style.rimTopAlpha * 0.45f),
                0.75f to shadowEdgeBase.copy(alpha = style.rimBottomAlpha * 0.6f),
                1.0f to shadowEdgeBase.copy(alpha = style.rimBottomAlpha),
            ),
        )
    }

    val elevation = elevationOverride ?: style.shadowElevationDp.dp
    val shaderModifier = if (
        style.tier == LiquidGlassTier.LIQUID_GLASS_SHADER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        style.refractionStrength > 0f
    ) {
        Modifier.liquidGlassAgslSheen(
            strength = style.refractionStrength,
            isDark = isDark,
        )
    } else {
        Modifier
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = style.ambientShadowAlpha),
            spotColor = Color.Black.copy(alpha = style.spotShadowAlpha),
        )
        .clip(shape)
        .background(fill)
        .background(sheenBrush)
        .then(shaderModifier)
        .border(width = borderWidth, brush = rimBrush, shape = shape)
}

/**
 * API 33+ AGSL 液态玻璃折射光泽着色器：
 * 在玻璃表面叠加柔和的曲率菲涅尔高光与轻微色散光泽带，增强液态立体质感且不遮挡文字。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Modifier.liquidGlassAgslSheen(
    strength: Float,
    isDark: Boolean,
): Modifier = drawWithCache {
    val shader = runCatching {
        RuntimeShader(LIQUID_GLASS_AGSL).apply {
            setFloatUniform("uSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
            setFloatUniform("uStrength", strength)
            setFloatUniform("uDark", if (isDark) 1.0f else 0.0f)
        }
    }.getOrNull()

    val brush = shader?.let { ShaderBrush(it) }
    onDrawBehind {
        if (brush != null) {
            drawRect(brush = brush)
        } else {
            // 运行时若个别 ROM 禁用 AGSL，静默回退到柔和斜向高光。
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = strength * 0.45f),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
        }
    }
}

private const val LIQUID_GLASS_AGSL = """
uniform float2 uSize;
uniform float uStrength;
uniform float uDark;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(uSize, float2(1.0, 1.0));
    // 玻璃曲面菲涅尔边缘权重（靠近四周尤其是顶部边缘更亮）
    float edgeX = min(uv.x, 1.0 - uv.x);
    float edgeY = min(uv.y, 1.0 - uv.y);
    float rim = smoothstep(0.18, 0.0, min(edgeX, edgeY));

    // 斜向液态折射高光带
    float diag = uv.x * 0.45 + uv.y * 0.85;
    float wave = exp(-pow((diag - 0.16) * 4.2, 2.0));
    float topBand = smoothstep(0.32, 0.0, uv.y) * 0.65;

    // 微妙冷暖色散（模拟液态玻璃边缘折射）
    float tintShift = (uv.x - 0.5) * 0.08;
    float3 specColor = float3(1.0 + tintShift, 1.0, 1.0 - tintShift);

    float intensity = (wave * 0.55 + topBand * 0.45 + rim * 0.45) * uStrength;
    float alpha = clamp(intensity * (uDark > 0.5 ? 0.55 : 0.95), 0.0, 0.32);
    return half4(half3(specColor * alpha), half(alpha));
}
"""
