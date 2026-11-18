package com.davidzhu.wantnote.data

import org.json.JSONObject

/**
 * 0.9.0 自定义主题的数据模型与序列化（纯 JVM，可单测）。
 *
 * - 「内置主题覆盖」：key = 内置 [ThemePalette] 名（PAPER/INK/SAGE/ROSE），
 *   value 只包含要覆盖的颜色；重置 = 从 JSON 里删除该 key。
 * - 「用户自建主题」：key = `USER_<名称>`，value 为完整 11 色。
 * - 全部持久化在 `AppSettings.themeCustomJson`；`themeName` 既可以是内置
 *   名，也可以是 USER_ 前缀的自建 key。
 */
data class ThemeColorSet(
    val lightBg: Long? = null,
    val lightInk: Long? = null,
    val lightMuted: Long? = null,
    val lightVariant: Long? = null,
    val lightOutline: Long? = null,
    val darkBg: Long? = null,
    val darkInk: Long? = null,
    val darkMuted: Long? = null,
    val darkVariant: Long? = null,
    val darkOutline: Long? = null,
    val highlight: Long? = null,
) {
    /** 用内置色补齐空位，得到一套完整配色。 */
    fun mergedOver(base: ThemeColorSet): ThemeColorSet {
        return ThemeColorSet(
            lightBg = lightBg ?: base.lightBg,
            lightInk = lightInk ?: base.lightInk,
            lightMuted = lightMuted ?: base.lightMuted,
            lightVariant = lightVariant ?: base.lightVariant,
            lightOutline = lightOutline ?: base.lightOutline,
            darkBg = darkBg ?: base.darkBg,
            darkInk = darkInk ?: base.darkInk,
            darkMuted = darkMuted ?: base.darkMuted,
            darkVariant = darkVariant ?: base.darkVariant,
            darkOutline = darkOutline ?: base.darkOutline,
            highlight = highlight ?: base.highlight,
        )
    }

    fun toJson(): String {
        val obj = JSONObject()
        FIELDS.forEach { (name, getter) ->
            val value = getter(this)
            if (value != null) obj.put(name, value)
        }
        return obj.toString()
    }

    companion object {
        val FIELDS: List<Pair<String, (ThemeColorSet) -> Long?>> = listOf(
            "lightBg" to { it.lightBg },
            "lightInk" to { it.lightInk },
            "lightMuted" to { it.lightMuted },
            "lightVariant" to { it.lightVariant },
            "lightOutline" to { it.lightOutline },
            "darkBg" to { it.darkBg },
            "darkInk" to { it.darkInk },
            "darkMuted" to { it.darkMuted },
            "darkVariant" to { it.darkVariant },
            "darkOutline" to { it.darkOutline },
            "highlight" to { it.highlight },
        )

        /** 全字段可编辑的稳定顺序（主题编辑器 UI 用）。 */
        val LABELS: Map<String, String> = mapOf(
            "lightBg" to "日间·背景",
            "lightInk" to "日间·主文字",
            "lightMuted" to "日间·次要文字",
            "lightVariant" to "日间·卡片底色",
            "lightOutline" to "日间·描边",
            "darkBg" to "夜间·背景",
            "darkInk" to "夜间·主文字",
            "darkMuted" to "夜间·次要文字",
            "darkVariant" to "夜间·卡片底色",
            "darkOutline" to "夜间·描边",
            "highlight" to "高亮色",
        )

        fun fromJson(text: String): ThemeColorSet {
            val obj = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            fun color(name: String): Long? {
                if (!obj.has(name)) return null
                val raw = obj.optLong(name, Long.MIN_VALUE)
                if (raw != Long.MIN_VALUE) return raw
                // 兼容 "#RRGGBB(AA)" 字符串写法。
                val hex = obj.optString(name).removePrefix("#")
                return hex.toLongOrNull(16)
            }
            return ThemeColorSet(
                lightBg = color("lightBg"),
                lightInk = color("lightInk"),
                lightMuted = color("lightMuted"),
                lightVariant = color("lightVariant"),
                lightOutline = color("lightOutline"),
                darkBg = color("darkBg"),
                darkInk = color("darkInk"),
                darkMuted = color("darkMuted"),
                darkVariant = color("darkVariant"),
                darkOutline = color("darkOutline"),
                highlight = color("highlight"),
            )
        }
    }
}

object ThemeCustoms {
    const val USER_PREFIX = "USER_"

    fun isUserTheme(name: String): Boolean = name.startsWith(USER_PREFIX)

    /** 用户自建主题的存储 key。 */
    fun userKey(displayName: String): String = USER_PREFIX + displayName.trim()

    /** 用户自建主题的显示名。 */
    fun displayName(key: String): String = key.removePrefix(USER_PREFIX)

    /** 解析 themeCustomJson；损坏的条目跳过，不抛异常。 */
    fun parse(json: String): Map<String, ThemeColorSet> {
        if (json.isBlank()) return emptyMap()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, ThemeColorSet>()
        root.keys().forEach { key ->
            val entry = root.optJSONObject(key) ?: return@forEach
            out[key] = ThemeColorSet.fromJson(entry.toString())
        }
        return out
    }

    fun serialize(map: Map<String, ThemeColorSet>): String {
        val root = JSONObject()
        map.forEach { (key, value) -> root.put(key, JSONObject(value.toJson())) }
        return root.toString()
    }

    /**
     * 当前主题的生效配色：内置主题应用覆盖（若有），自建主题缺省回退到
     * PAPER 再应用自建值。未知主题名返回 null（由调用方回退默认）。
     */
    fun resolve(
        themeName: String,
        builtIn: ThemeColorSet?,
        customs: Map<String, ThemeColorSet>,
        fallback: ThemeColorSet,
    ): ThemeColorSet? {
        return when {
            isUserTheme(themeName) -> {
                val stored = customs[themeName] ?: return null
                stored.mergedOver(fallback)
            }
            builtIn != null -> customs[themeName]?.mergedOver(builtIn) ?: builtIn
            else -> null
        }
    }
}
