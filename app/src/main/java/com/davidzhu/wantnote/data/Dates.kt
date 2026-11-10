package com.davidzhu.wantnote.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class YearMonth(val year: Int, val month: Int) {
    fun previous(): YearMonth {
        return if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
    }

    fun next(): YearMonth {
        return if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    }

    fun label(): String = "${year}年${month}月"
}

object Dates {
    fun nowCalendar(): Calendar = Calendar.getInstance()

    fun currentYearMonth(): YearMonth {
        val cal = nowCalendar()
        return YearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun startOfDay(epochMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDay(epochMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(epochMs)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis - 1
    }

    fun startOfDay(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun isSameDay(a: Long, b: Long): Boolean = startOfDay(a) == startOfDay(b)

    fun belongsToDay(note: Note, dayStart: Long): Boolean {
        val dayEnd = dayStart + 24L * 60L * 60L * 1000L - 1
        return (note.createdAt in dayStart..dayEnd) || (note.updatedAt in dayStart..dayEnd)
    }

    fun formatTime(epochMs: Long): String {
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    fun formatTimestamp(epochMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    fun formatDay(epochMs: Long): String {
        return SimpleDateFormat("yyyy年M月d日 EEE", Locale.getDefault()).format(Date(epochMs))
    }

    fun formatDayShort(epochMs: Long): String {
        return SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(epochMs))
    }

    fun dayKey(epochMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMs))
    }

    fun daysInMonthGrid(yearMonth: YearMonth): List<Long?> {
        val first = Calendar.getInstance()
        first.clear()
        first.set(Calendar.YEAR, yearMonth.year)
        first.set(Calendar.MONTH, yearMonth.month - 1)
        first.set(Calendar.DAY_OF_MONTH, 1)
        first.set(Calendar.HOUR_OF_DAY, 0)
        first.set(Calendar.MINUTE, 0)
        first.set(Calendar.SECOND, 0)
        first.set(Calendar.MILLISECOND, 0)

        val firstDayOfWeek = first.firstDayOfWeek
        val dow = first.get(Calendar.DAY_OF_WEEK)
        var leading = dow - firstDayOfWeek
        if (leading < 0) leading += 7

        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<Long?>()
        repeat(leading) { cells.add(null) }
        for (day in 1..daysInMonth) {
            cells.add(startOfDay(yearMonth.year, yearMonth.month, day))
        }
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    fun weekdayLabels(): List<String> {
        val first = Calendar.getInstance().firstDayOfWeek
        val names = listOf("日", "一", "二", "三", "四", "五", "六")
        // Calendar.SUNDAY = 1 ... SATURDAY = 7
        return (0 until 7).map { names[(first - 1 + it) % 7] }
    }

    fun startOfWeek(epochMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(epochMs)
        val first = cal.firstDayOfWeek
        while (cal.get(Calendar.DAY_OF_WEEK) != first) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.timeInMillis
    }
}
