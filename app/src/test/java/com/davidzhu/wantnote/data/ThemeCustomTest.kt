package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.9.0 主题序列化：覆盖/自建主题的 JSON 编解码、合并语义（内置色 +
 * 覆盖）、重置（删除条目）与用户主题 key 规则。
 */
class ThemeCustomTest {

    private val paperLike = ThemeColorSet(
        lightBg = 0xFFF7F2EA,
        lightInk = 0xFF3F2A1A,
        lightMuted = 0xFF8A7A6B,
        lightVariant = 0xFFF1E8DC,
        lightOutline = 0xFFD9CBB8,
        darkBg = 0xFF1C1712,
        darkInk = 0xFFF0E6D8,
        darkMuted = 0xFFB4A392,
        darkVariant = 0xFF2A231C,
        darkOutline = 0xFF4A3F34,
        highlight = 0xFFE8C04A,
    )

    @Test
    fun serializeRoundTripKeepsAllFields() {
        val json = paperLike.toJson()
        val parsed = ThemeColorSet.fromJson(json)
        assertEquals(paperLike, parsed)
    }

    @Test
    fun partialOverrideKeepsOnlyProvidedFields() {
        val override = ThemeColorSet(lightBg = 0xFF123456, highlight = 0xFF654321)
        val parsed = ThemeColorSet.fromJson(override.toJson())
        assertNull(parsed.lightInk)
        assertNull(parsed.darkBg)
        assertEquals(0xFF123456, parsed.lightBg)
        assertEquals(0xFF654321, parsed.highlight)
    }

    @Test
    fun mergedOverFallsBackToBase() {
        val override = ThemeColorSet(lightBg = 0xFF111111)
        val merged = override.mergedOver(paperLike)
        assertEquals(0xFF111111, merged.lightBg)
        assertEquals(paperLike.lightInk, merged.lightInk)
        assertEquals(paperLike.highlight, merged.highlight)
        assertEquals(paperLike, paperLike.mergedOver(paperLike))
    }

    @Test
    fun customsMapRoundTrips() {
        val map = mapOf(
            "PAPER" to ThemeColorSet(lightBg = 0xFF223344),
            ThemeCustoms.userKey("我的夜航") to paperLike,
        )
        val json = ThemeCustoms.serialize(map)
        val parsed = ThemeCustoms.parse(json)
        assertEquals(map, parsed)
        assertTrue(ThemeCustoms.isUserTheme(ThemeCustoms.userKey("我的夜航")))
        assertFalse(ThemeCustoms.isUserTheme("PAPER"))
        assertEquals("我的夜航", ThemeCustoms.displayName(ThemeCustoms.userKey("我的夜航")))
    }

    @Test
    fun corruptJsonIsTolerated() {
        assertTrue(ThemeCustoms.parse("").isEmpty())
        assertTrue(ThemeCustoms.parse("不是json{{{").isEmpty())
        // 合法外层 + 损坏条目：条目被跳过。
        val parsed = ThemeCustoms.parse("""{"PAPER": {"lightBg": 123}, "BAD": "str"}""")
        assertTrue(parsed.containsKey("PAPER"))
        assertFalse(parsed.containsKey("BAD"))
    }

    @Test
    fun hexStringColorsAccepted() {
        val parsed = ThemeColorSet.fromJson("""{"lightBg": "FF3F2A1A"}""")
        assertEquals(0xFF3F2A1A, parsed.lightBg)
    }

    @Test
    fun resolvePrefersOverrideThenCustomThenFallback() {
        val customs = mapOf(
            "PAPER" to ThemeColorSet(highlight = 0xFF00FF00),
            ThemeCustoms.userKey("自定义") to ThemeColorSet(lightBg = 0xFFABCDEF),
        )
        val paper = paperLike
        // 内置主题 + 覆盖。
        val resolvedPaper = ThemeCustoms.resolve("PAPER", paper, customs, paper)!!
        assertEquals(0xFF00FF00, resolvedPaper.highlight)
        assertEquals(paper.lightBg, resolvedPaper.lightBg)
        // 自建主题回退到 fallback 基色。
        val resolvedUser = ThemeCustoms.resolve(ThemeCustoms.userKey("自定义"), null, customs, paper)!!
        assertEquals(0xFFABCDEF, resolvedUser.lightBg)
        assertEquals(paper.highlight, resolvedUser.highlight)
        // 被删除的自建主题 → null（调用方回退出厂）。
        assertNull(ThemeCustoms.resolve(ThemeCustoms.userKey("已删"), null, customs, paper))
    }
}
