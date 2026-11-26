package com.davidzhu.wantnote.data

/**
 * 0.14.0 Q3/Q4：Miuix 液态玻璃分档与低版本退路策略（纯 Kotlin，JVM 可直接单测）。
 *
 * - API 33+（Android 13+）：支持 AGSL `RuntimeShader` 液态玻璃光泽折射与高光边；
 * - API 31–32（Android 12）：降级为 `RenderEffect` 磨砂玻璃质感 + 立体描边与阴影；
 * - API 24–30（Android 7–11）：稳妥退路——纯 Compose 多层半透明玻璃渐变、顶部高光棱边与
 *   更清晰的立体阴影，不依赖高版本图形类，确保 `minSdk = 24` 无崩溃风险。
 */
enum class LiquidGlassTier {
    /** API 33+：AGSL RuntimeShader 液态玻璃高光折射 + 多层立体描边与阴影。 */
    LIQUID_GLASS_SHADER,

    /** API 31–32：RenderEffect 磨砂玻璃 + 立体高光描边与阴影。 */
    FROSTED_RENDER_EFFECT,

    /** API 24–30：低版本纯 Compose 分层立体玻璃退路（零高版本 API 依赖）。 */
    LAYERED_FALLBACK,
    ;

    companion object {
        const val API_RENDER_EFFECT = 31
        const val API_RUNTIME_SHADER = 33

        fun forApiLevel(apiLevel: Int): LiquidGlassTier = when {
            apiLevel >= API_RUNTIME_SHADER -> LIQUID_GLASS_SHADER
            apiLevel >= API_RENDER_EFFECT -> FROSTED_RENDER_EFFECT
            else -> LAYERED_FALLBACK
        }
    }
}

/** 使用液态玻璃材质的界面层级角色。 */
enum class LiquidGlassSurfaceRole {
    /** 悬浮底栏 / 顶栏等主导航容器。 */
    BAR,

    /** 主页笔记卡片、设置分组卡片。 */
    CARD,

    /** 弹窗与悬浮下拉菜单。 */
    DIALOG,

    /** 详情页卡片盒胶囊、标签加号按钮、分段控件。 */
    CHIP,

    /** 居中悬浮新建按钮（FAB）。 */
    FAB,
}

/**
 * 某档位 + 角色 + 明暗模式下的液态玻璃参数。
 *
 * 所有 alpha 都在 `[0f, 1f]` 范围内；低版本退路适当提高底色不透明度以保证文字对比度，
 * 同时通过顶部高光与阴影保留立体层次。
 */
data class LiquidGlassStyle(
    val tier: LiquidGlassTier,
    val surfaceAlpha: Float,
    val topSheenAlpha: Float,
    val bottomSheenAlpha: Float,
    val rimTopAlpha: Float,
    val rimBottomAlpha: Float,
    val shadowElevationDp: Float,
    val ambientShadowAlpha: Float,
    val spotShadowAlpha: Float,
    val blurRadiusDp: Float,
    val refractionStrength: Float,
)

object LiquidGlassSpec {

    fun resolve(
        apiLevel: Int,
        role: LiquidGlassSurfaceRole,
        isDark: Boolean,
    ): LiquidGlassStyle {
        val tier = LiquidGlassTier.forApiLevel(apiLevel)
        val isFallback = tier == LiquidGlassTier.LAYERED_FALLBACK

        // 低版本无系统模糊/折射，底色略实以保可读性；高版本更通透以凸显液态玻璃层次。
        val surfaceAlpha = when (role) {
            LiquidGlassSurfaceRole.BAR -> when {
                isFallback && isDark -> 0.94f
                isFallback -> 0.95f
                isDark -> 0.80f
                else -> 0.84f
            }
            LiquidGlassSurfaceRole.CARD -> when {
                isFallback && isDark -> 0.96f
                isFallback -> 0.97f
                isDark -> 0.86f
                else -> 0.90f
            }
            LiquidGlassSurfaceRole.DIALOG -> when {
                isFallback && isDark -> 0.97f
                isFallback -> 0.98f
                isDark -> 0.90f
                else -> 0.92f
            }
            LiquidGlassSurfaceRole.CHIP -> when {
                isFallback && isDark -> 0.92f
                isFallback -> 0.94f
                isDark -> 0.78f
                else -> 0.82f
            }
            LiquidGlassSurfaceRole.FAB -> 1.0f
        }

        val topSheenAlpha = when (role) {
            LiquidGlassSurfaceRole.FAB -> if (isDark) 0.30f else 0.36f
            LiquidGlassSurfaceRole.BAR, LiquidGlassSurfaceRole.DIALOG -> if (isDark) 0.16f else 0.44f
            LiquidGlassSurfaceRole.CARD -> if (isDark) 0.12f else 0.34f
            LiquidGlassSurfaceRole.CHIP -> if (isDark) 0.14f else 0.38f
        }

        val bottomSheenAlpha = when (role) {
            LiquidGlassSurfaceRole.FAB -> if (isDark) 0.10f else 0.08f
            else -> if (isDark) 0.03f else 0.06f
        }

        // 顶部左侧镜面高光棱边 + 底部右侧柔和暗边，塑造立体玻璃厚度。
        val rimTopAlpha = when (role) {
            LiquidGlassSurfaceRole.FAB -> if (isDark) 0.45f else 0.62f
            LiquidGlassSurfaceRole.BAR, LiquidGlassSurfaceRole.DIALOG -> if (isDark) 0.28f else 0.78f
            LiquidGlassSurfaceRole.CARD -> if (isDark) 0.22f else 0.68f
            LiquidGlassSurfaceRole.CHIP -> if (isDark) 0.26f else 0.72f
        }

        val rimBottomAlpha = if (isDark) 0.08f else 0.18f

        val shadowElevationDp = when (role) {
            LiquidGlassSurfaceRole.BAR -> 14f
            LiquidGlassSurfaceRole.DIALOG -> 18f
            LiquidGlassSurfaceRole.FAB -> 12f
            LiquidGlassSurfaceRole.CARD -> 9f
            LiquidGlassSurfaceRole.CHIP -> 4f
        }

        val ambientShadowAlpha = if (isDark) 0.42f else 0.20f
        val spotShadowAlpha = if (isDark) 0.62f else 0.34f

        val blurRadiusDp = when (tier) {
            LiquidGlassTier.LIQUID_GLASS_SHADER -> when (role) {
                LiquidGlassSurfaceRole.BAR, LiquidGlassSurfaceRole.DIALOG -> 18f
                LiquidGlassSurfaceRole.CARD -> 10f
                LiquidGlassSurfaceRole.CHIP -> 8f
                LiquidGlassSurfaceRole.FAB -> 0f
            }
            LiquidGlassTier.FROSTED_RENDER_EFFECT -> when (role) {
                LiquidGlassSurfaceRole.BAR, LiquidGlassSurfaceRole.DIALOG -> 14f
                LiquidGlassSurfaceRole.CARD -> 8f
                LiquidGlassSurfaceRole.CHIP -> 6f
                LiquidGlassSurfaceRole.FAB -> 0f
            }
            LiquidGlassTier.LAYERED_FALLBACK -> 0f
        }

        val refractionStrength = when (tier) {
            LiquidGlassTier.LIQUID_GLASS_SHADER -> when (role) {
                LiquidGlassSurfaceRole.BAR, LiquidGlassSurfaceRole.DIALOG -> 0.24f
                LiquidGlassSurfaceRole.CARD -> 0.14f
                LiquidGlassSurfaceRole.CHIP -> 0.18f
                LiquidGlassSurfaceRole.FAB -> 0.22f
            }
            LiquidGlassTier.FROSTED_RENDER_EFFECT,
            LiquidGlassTier.LAYERED_FALLBACK,
            -> 0f
        }

        return LiquidGlassStyle(
            tier = tier,
            surfaceAlpha = surfaceAlpha,
            topSheenAlpha = topSheenAlpha,
            bottomSheenAlpha = bottomSheenAlpha,
            rimTopAlpha = rimTopAlpha,
            rimBottomAlpha = rimBottomAlpha,
            shadowElevationDp = shadowElevationDp,
            ambientShadowAlpha = ambientShadowAlpha,
            spotShadowAlpha = spotShadowAlpha,
            blurRadiusDp = blurRadiusDp,
            refractionStrength = refractionStrength,
        )
    }
}
