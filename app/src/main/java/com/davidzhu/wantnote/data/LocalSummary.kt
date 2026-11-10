package com.davidzhu.wantnote.data

data class LocalSummary(
    val totalNotes: Int,
    val weekNotes: Int,
    val monthNotes: Int,
    val totalChars: Int,
    val openTasks: Int,
    val doneTasks: Int,
    val highlights: List<String>,
    val recentTitles: List<String>,
    val busiestDays: List<Pair<String, Int>>,
    val narrative: String,
)

object LocalInsights {
    fun summarize(items: List<NoteWithBlocks>, now: Long = System.currentTimeMillis()): LocalSummary {
        val weekStart = Dates.startOfWeek(now)
        val monthCal = Dates.nowCalendar().apply { timeInMillis = now }
        monthCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        monthCal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        monthCal.set(java.util.Calendar.MINUTE, 0)
        monthCal.set(java.util.Calendar.SECOND, 0)
        monthCal.set(java.util.Calendar.MILLISECOND, 0)
        val monthStart = monthCal.timeInMillis

        val highlights = items.flatMap { item ->
            SearchText.extractHighlights(item.note.title + "\n" + item.note.body)
        }.distinct().take(10)

        val recentTitles = items.take(6).map { SearchText.displayTitle(it.note) }

        val dayCounts = items.groupingBy { Dates.dayKey(it.note.updatedAt) }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        var openTasks = 0
        var doneTasks = 0
        var chars = 0
        for (item in items) {
            chars += item.note.title.length + item.blocks.sumOf { it.text.length }
            for (block in item.blocks) {
                if (block.blockType == BlockType.TASK) {
                    if (block.checked) doneTasks++ else openTasks++
                }
            }
        }

        val weekNotes = items.count { it.note.createdAt >= weekStart }
        val monthNotes = items.count { it.note.createdAt >= monthStart }

        val taskPart = if (openTasks + doneTasks == 0) {
            "还没有任务。"
        } else {
            "待办完成 $doneTasks/${openTasks + doneTasks}。"
        }
        val highlightPart = if (highlights.isEmpty()) {
            "还没有高亮摘录。"
        } else {
            "最近高亮：${highlights.take(3).joinToString("、")}。"
        }
        val recentPart = if (recentTitles.isEmpty()) {
            "还没有笔记可供回顾。"
        } else {
            "最近写下了：${recentTitles.take(3).joinToString("、")}。"
        }
        val narrative = "共 ${items.size} 条笔记，本周新增 $weekNotes 条，本月新增 $monthNotes 条。$taskPart$highlightPart$recentPart"

        return LocalSummary(
            totalNotes = items.size,
            weekNotes = weekNotes,
            monthNotes = monthNotes,
            totalChars = chars,
            openTasks = openTasks,
            doneTasks = doneTasks,
            highlights = highlights,
            recentTitles = recentTitles,
            busiestDays = dayCounts,
            narrative = narrative,
        )
    }
}
