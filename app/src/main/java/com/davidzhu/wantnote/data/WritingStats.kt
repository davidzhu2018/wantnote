package com.davidzhu.wantnote.data

import java.util.Calendar
import java.util.Locale

/** 热力图时间颗粒度。 */
enum class HeatmapGranularity(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
}

/** 热力图单个时间格子的统计数据。 */
data class HeatmapCell(
    val key: String,
    val label: String,
    val count: Int,
    /** 0..4 颜色深度等级（0 表示无记录，4 表示最高频区间）。 */
    val level: Int,
)

/** 单个维度的计数项（标签、卡片盒、时段）。 */
data class StatBucket(
    val id: String,
    val label: String,
    val count: Int,
)

/** 笔记综合数据统计结果（纯 Kotlin，JVM 可单测）。 */
data class WritingStatsSummary(
    val totalNotes: Int,
    val totalBoxes: Int,
    val totalTags: Int,
    val totalChars: Int,
    val byBox: List<StatBucket>,
    val byTag: List<StatBucket>,
    val byPeriod: List<StatBucket>,
    val peakPeriodLabel: String,
)

/**
 * 0.17.2：笔记数据统计与创作热力图计算引擎。
 */
object WritingStats {

    data class HeatmapPresetColor(
        val name: String,
        val hex: String,
        val argb: Long,
    )

    val HEATMAP_COLORS = listOf(
        HeatmapPresetColor("绿色", "#2EA043", 0xFF2EA043L),
        HeatmapPresetColor("蓝色", "#1A73E8", 0xFF1A73E8L),
        HeatmapPresetColor("紫色", "#7C3AED", 0xFF7C3AEDL),
        HeatmapPresetColor("橙色", "#EA580C", 0xFFEA580CL),
        HeatmapPresetColor("粉色", "#DB2777", 0xFFDB2777L),
        HeatmapPresetColor("青色", "#0D9488", 0xFF0D9488L),
    )

    fun colorArgbForHex(hex: String): Long {
        val clean = hex.trim().uppercase(Locale.US)
        HEATMAP_COLORS.firstOrNull { it.hex.equals(clean, ignoreCase = true) }?.let { return it.argb }
        val raw = clean.removePrefix("#")
        if (raw.length == 6) {
            raw.toLongOrNull(16)?.let { return 0xFF000000L or it }
        }
        return HEATMAP_COLORS.first().argb
    }

    fun summarize(
        items: List<NoteWithDetails>,
        boxes: List<NoteBox>,
        tags: List<Tag>,
    ): WritingStatsSummary {
        val boxNameById = boxes.associate { it.id to it.name }
        val boxCounts = LinkedHashMap<String, Int>()
        boxes.forEach { boxCounts[it.id] = 0 }
        var unassigned = 0
        var totalChars = 0

        val tagCounts = LinkedHashMap<String, Int>()
        tags.forEach { tagCounts[it.id] = 0 }

        val periodCounts = IntArray(4) // 0: 凌晨 0-6, 1: 上午 6-12, 2: 下午 12-18, 3: 晚上 18-24
        val cal = Calendar.getInstance()

        items.forEach { item ->
            totalChars += item.note.title.length + item.note.body.length
            val bid = item.note.boxId
            if (bid.isNotBlank() && boxNameById.containsKey(bid)) {
                boxCounts[bid] = (boxCounts[bid] ?: 0) + 1
            } else {
                unassigned += 1
            }
            item.tags.forEach { t ->
                tagCounts[t.id] = (tagCounts[t.id] ?: 0) + 1
            }
            val ts = if (item.note.createdAt > 0L) item.note.createdAt else item.note.updatedAt
            cal.timeInMillis = ts
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val slot = when (hour) {
                in 0..5 -> 0
                in 6..11 -> 1
                in 12..17 -> 2
                else -> 3
            }
            periodCounts[slot] += 1
        }

        val byBox = ArrayList<StatBucket>()
        boxes.forEach { box ->
            byBox += StatBucket(box.id, box.name, boxCounts[box.id] ?: 0)
        }
        if (unassigned > 0 || byBox.isEmpty()) {
            byBox += StatBucket("", "未分配", unassigned)
        }
        byBox.sortWith(compareByDescending<StatBucket> { it.count }.thenBy { it.label })

        val byTag = tags.map { tag ->
            StatBucket(tag.id, tag.path, tagCounts[tag.id] ?: 0)
        }.sortedWith(compareByDescending<StatBucket> { it.count }.thenBy { it.label })

        val periodLabels = listOf("凌晨 (00:00-06:00)", "上午 (06:00-12:00)", "下午 (12:00-18:00)", "晚上 (18:00-24:00)")
        val byPeriod = periodLabels.mapIndexed { idx, label ->
            StatBucket("p$idx", label, periodCounts[idx])
        }
        val peakIdx = periodCounts.indices.maxByOrNull { periodCounts[it] } ?: 3
        val peakLabel = if (items.isEmpty()) "暂无记录" else periodLabels[peakIdx]

        return WritingStatsSummary(
            totalNotes = items.size,
            totalBoxes = boxes.size,
            totalTags = tags.size,
            totalChars = totalChars,
            byBox = byBox,
            byTag = byTag,
            byPeriod = byPeriod,
            peakPeriodLabel = peakLabel,
        )
    }

    /**
     * 按选定颗粒度（日 / 周 / 月 / 年）构建热力图格子。
     * - 日：最近 84 天（12 周 × 7 天）
     * - 周：最近 24 周
     * - 月：最近 12 个月
     * - 年：最近 5 年（若历史更早则覆盖至最早年份）
     */
    fun buildHeatmap(
        timestamps: List<Long>,
        granularity: HeatmapGranularity,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<HeatmapCell> {
        val cal = Calendar.getInstance()
        val counts = HashMap<String, Int>()
        timestamps.forEach { ts ->
            if (ts <= 0L) return@forEach
            cal.timeInMillis = ts
            val key = bucketKey(cal, granularity)
            counts[key] = (counts[key] ?: 0) + 1
        }

        val orderedKeys = ArrayList<Pair<String, String>>()
        when (granularity) {
            HeatmapGranularity.DAY -> {
                val c = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -83)
                }
                repeat(84) {
                    val key = bucketKey(c, HeatmapGranularity.DAY)
                    val label = String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1,
                        c.get(Calendar.DAY_OF_MONTH),
                    )
                    orderedKeys += key to label
                    c.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            HeatmapGranularity.WEEK -> {
                val c = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    firstDayOfWeek = Calendar.MONDAY
                    add(Calendar.WEEK_OF_YEAR, -23)
                }
                repeat(24) {
                    val key = bucketKey(c, HeatmapGranularity.WEEK)
                    val y = c.get(Calendar.YEAR)
                    val w = c.get(Calendar.WEEK_OF_YEAR)
                    orderedKeys += key to "${y}年 第${w}周"
                    c.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            HeatmapGranularity.MONTH -> {
                val c = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -11)
                }
                repeat(12) {
                    val key = bucketKey(c, HeatmapGranularity.MONTH)
                    val label = String.format(
                        Locale.US,
                        "%04d年%02d月",
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1,
                    )
                    orderedKeys += key to label
                    c.add(Calendar.MONTH, 1)
                }
            }
            HeatmapGranularity.YEAR -> {
                val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val currentYear = nowCal.get(Calendar.YEAR)
                val earliestYear = timestamps.filter { it > 0L }.minOfOrNull { ts ->
                    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.YEAR)
                } ?: currentYear
                val startYear = minOf(currentYear - 4, earliestYear)
                for (year in startYear..currentYear) {
                    orderedKeys += year.toString() to "${year}年"
                }
            }
        }

        val maxCount = orderedKeys.maxOfOrNull { (k, _) -> counts[k] ?: 0 } ?: 0
        return orderedKeys.map { (key, label) ->
            val count = counts[key] ?: 0
            HeatmapCell(
                key = key,
                label = label,
                count = count,
                level = intensityLevel(count, maxCount),
            )
        }
    }

    private fun bucketKey(cal: Calendar, granularity: HeatmapGranularity): String {
        return when (granularity) {
            HeatmapGranularity.DAY -> String.format(
                Locale.US,
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
            HeatmapGranularity.WEEK -> {
                val copy = (cal.clone() as Calendar).apply { firstDayOfWeek = Calendar.MONDAY }
                String.format(Locale.US, "%04d-W%02d", copy.get(Calendar.YEAR), copy.get(Calendar.WEEK_OF_YEAR))
            }
            HeatmapGranularity.MONTH -> String.format(
                Locale.US,
                "%04d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
            )
            HeatmapGranularity.YEAR -> cal.get(Calendar.YEAR).toString()
        }
    }

    fun intensityLevel(count: Int, maxCount: Int): Int {
        if (count <= 0 || maxCount <= 0) return 0
        val ratio = count.toFloat() / maxCount.toFloat()
        return when {
            ratio <= 0.25f -> 1
            ratio <= 0.50f -> 2
            ratio <= 0.75f -> 3
            else -> 4
        }
    }
}
