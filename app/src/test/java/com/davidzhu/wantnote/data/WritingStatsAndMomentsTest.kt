package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 0.17.2：验证数据统计（卡片盒/标签/时段维度）、创作热力图（日/周/月/年颗粒度与默认绿色）
 * 以及朋友圈视图模式枚举。
 */
class WritingStatsAndMomentsTest {

    @Test
    fun homeViewModeIncludesMoments() {
        assertEquals(HomeViewMode.MOMENTS, HomeViewMode.fromStorage("MOMENTS"))
        assertEquals("朋友圈", HomeViewMode.MOMENTS.label)
    }

    @Test
    fun writingStatsSummarizesNotesBoxesTagsAndPeriods() {
        val calMorning = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 20, 9, 30, 0)
        }.timeInMillis
        val calNight = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 21, 21, 15, 0)
        }.timeInMillis

        val boxA = NoteBox(id = "b1", name = "工作", createdAt = 1L)
        val boxB = NoteBox(id = "b2", name = "灵感", createdAt = 1L)
        val tag1 = Tag(id = "t1", path = "开发", color = 0xFF0000, createdAt = 1L)

        val n1 = NoteWithDetails(
            note = Note(id = "n1", title = "一", body = "内容A", createdAt = calMorning, updatedAt = calMorning, boxId = "b1"),
            blocks = emptyList(),
            tags = listOf(tag1),
            noteBox = boxA,
        )
        val n2 = NoteWithDetails(
            note = Note(id = "n2", title = "二", body = "内容B", createdAt = calNight, updatedAt = calNight, boxId = "b1"),
            blocks = emptyList(),
            tags = listOf(tag1),
            noteBox = boxA,
        )
        val n3 = NoteWithDetails(
            note = Note(id = "n3", title = "三", body = "内容C", createdAt = calNight, updatedAt = calNight, boxId = ""),
            blocks = emptyList(),
            tags = emptyList(),
            noteBox = null,
        )

        val summary = WritingStats.summarize(listOf(n1, n2, n3), listOf(boxA, boxB), listOf(tag1))
        assertEquals(3, summary.totalNotes)
        assertEquals(2, summary.totalBoxes)
        assertEquals(1, summary.totalTags)
        assertEquals(2, summary.byBox.first { it.id == "b1" }.count)
        assertEquals(0, summary.byBox.first { it.id == "b2" }.count)
        assertEquals(1, summary.byBox.first { it.id == "" }.count)
        assertEquals(2, summary.byTag.first { it.id == "t1" }.count)
        assertTrue(summary.peakPeriodLabel.contains("晚上"))
    }

    @Test
    fun heatmapSupportsDayWeekMonthYearGranularitiesAndDefaultsGreen() {
        assertEquals("#2EA043", AppSettings.DEFAULT.heatmapColorHex)
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 27, 10, 0, 0)
        }.timeInMillis
        val timestamps = listOf(now, now, now - 86_400_000L)

        val dayCells = WritingStats.buildHeatmap(timestamps, HeatmapGranularity.DAY, nowMillis = now)
        assertEquals(84, dayCells.size)
        assertEquals(2, dayCells.last().count)
        assertEquals(4, dayCells.last().level)

        val weekCells = WritingStats.buildHeatmap(timestamps, HeatmapGranularity.WEEK, nowMillis = now)
        assertEquals(24, weekCells.size)
        assertTrue(weekCells.any { it.count > 0 })

        val monthCells = WritingStats.buildHeatmap(timestamps, HeatmapGranularity.MONTH, nowMillis = now)
        assertEquals(12, monthCells.size)
        assertEquals(3, monthCells.last().count)

        val yearCells = WritingStats.buildHeatmap(timestamps, HeatmapGranularity.YEAR, nowMillis = now)
        assertTrue(yearCells.size >= 5)
        assertEquals(3, yearCells.last().count)
    }
}
