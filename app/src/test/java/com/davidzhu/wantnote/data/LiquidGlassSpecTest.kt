package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.14.0 Q3/Q4 锁定行为测试：
 * - API 33+ 启用完整液态玻璃（AGSL RuntimeShader）；
 * - API 31–32 启用 RenderEffect 磨砂玻璃档；
 * - API 24–30 走纯 Compose 立体玻璃退路（零 blur / 零 refraction，底色更实以保可读性）。
 */
class LiquidGlassSpecTest {

    @Test
    fun tierMatchesApiThresholds() {
        assertEquals(LiquidGlassTier.LAYERED_FALLBACK, LiquidGlassTier.forApiLevel(24))
        assertEquals(LiquidGlassTier.LAYERED_FALLBACK, LiquidGlassTier.forApiLevel(30))
        assertEquals(LiquidGlassTier.FROSTED_RENDER_EFFECT, LiquidGlassTier.forApiLevel(31))
        assertEquals(LiquidGlassTier.FROSTED_RENDER_EFFECT, LiquidGlassTier.forApiLevel(32))
        assertEquals(LiquidGlassTier.LIQUID_GLASS_SHADER, LiquidGlassTier.forApiLevel(33))
        assertEquals(LiquidGlassTier.LIQUID_GLASS_SHADER, LiquidGlassTier.forApiLevel(36))
        assertEquals(LiquidGlassTier.LIQUID_GLASS_SHADER, LiquidGlassTier.forApiLevel(37))
    }

    @Test
    fun lowApiFallbackDisablesBlurAndRefractionAndBoostsOpacity() {
        for (role in listOf(
            LiquidGlassSurfaceRole.BAR,
            LiquidGlassSurfaceRole.CARD,
            LiquidGlassSurfaceRole.DIALOG,
            LiquidGlassSurfaceRole.CHIP,
        )) {
            for (isDark in listOf(false, true)) {
                val fallback = LiquidGlassSpec.resolve(apiLevel = 26, role = role, isDark = isDark)
                val modern = LiquidGlassSpec.resolve(apiLevel = 35, role = role, isDark = isDark)

                assertEquals(LiquidGlassTier.LAYERED_FALLBACK, fallback.tier)
                assertEquals(0f, fallback.blurRadiusDp, 0.0001f)
                assertEquals(0f, fallback.refractionStrength, 0.0001f)
                assertTrue(
                    "低版本退路底色应更实以保障可读性: $role dark=$isDark",
                    fallback.surfaceAlpha > modern.surfaceAlpha,
                )
                assertTrue(
                    "低版本仍须保留立体阴影: $role",
                    fallback.shadowElevationDp > 0f,
                )
                assertTrue(
                    "高版本应具备液态玻璃折射强度: $role",
                    modern.refractionStrength > 0f,
                )
            }
        }
    }

    @Test
    fun allStylesKeepValidAlphaAndDepthInvariants() {
        for (api in listOf(24, 30, 31, 33, 37)) {
            for (role in LiquidGlassSurfaceRole.entries) {
                for (isDark in listOf(false, true)) {
                    val s = LiquidGlassSpec.resolve(api, role, isDark)
                    assertTrue(s.surfaceAlpha in 0.5f..1.0f)
                    assertTrue(s.topSheenAlpha in 0f..1f)
                    assertTrue(s.bottomSheenAlpha in 0f..1f)
                    assertTrue(s.rimTopAlpha in 0f..1f)
                    assertTrue(s.rimBottomAlpha in 0f..1f)
                    assertTrue(s.shadowElevationDp > 0f)
                }
            }
        }
    }
}
