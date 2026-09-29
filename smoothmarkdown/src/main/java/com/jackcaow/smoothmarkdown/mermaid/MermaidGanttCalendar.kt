package com.jackcaow.smoothmarkdown.mermaid

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

internal enum class MermaidGanttTickTier { DAY, WEEK, MONTH }

internal data class MermaidGanttCalendarTick(
    val day: Long,
    val x: Float,
    val label: String,
    val tier: MermaidGanttTickTier,
)

/** The Flutter Gantt header changes from day to week to month labels as the range grows. */
internal fun MermaidGanttPlacement.calendarTicks(): List<MermaidGanttCalendarTick> {
    val totalDays = (maxDay - minDay + 1).coerceAtLeast(1)
    val dayWidth = chartWidth / totalDays
    val scale = when {
        dayWidth >= 20f && totalDays <= 60 -> MermaidGanttTickTier.DAY
        dayWidth >= 5f || totalDays <= 120 -> MermaidGanttTickTier.WEEK
        else -> MermaidGanttTickTier.MONTH
    }
    val ticks = mutableListOf<MermaidGanttCalendarTick>()
    fun add(day: Long, label: String, tier: MermaidGanttTickTier) {
        if (day in minDay..maxDay) {
            ticks += MermaidGanttCalendarTick(day, chartX + (day - minDay) * dayWidth, label, tier)
        }
    }
    fun date(day: Long) = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
        timeInMillis = day * 86_400_000L
    }
    val monthFormat = SimpleDateFormat("MMM yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val shortMonthFormat = SimpleDateFormat("MMM", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val first = date(minDay)
    if (scale != MermaidGanttTickTier.MONTH) {
        // Flutter places a month label at the start of the visible range, then at each new month.
        add(minDay, if (scale == MermaidGanttTickTier.DAY) shortMonthFormat.format(first.time)
            else monthFormat.format(first.time), MermaidGanttTickTier.MONTH)
        val month = date(minDay).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        repeat(512) {
            val day = month.timeInMillis / 86_400_000L
            if (day > maxDay) return@repeat
            add(day, if (scale == MermaidGanttTickTier.DAY) shortMonthFormat.format(month.time)
                else monthFormat.format(month.time), MermaidGanttTickTier.MONTH)
            month.add(Calendar.MONTH, 1)
        }
    }
    when (scale) {
        MermaidGanttTickTier.DAY -> {
            for (offset in 0 until totalDays.toInt()) {
                val day = minDay + offset
                add(day, if (dayWidth >= 25f) date(day).get(Calendar.DAY_OF_MONTH).toString() else "",
                    MermaidGanttTickTier.DAY)
            }
        }
        MermaidGanttTickTier.WEEK -> {
            val monday = date(minDay)
            while (monday.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                monday.add(Calendar.DAY_OF_MONTH, 1)
            }
            repeat(512) {
                val day = monday.timeInMillis / 86_400_000L
                if (day > maxDay) return@repeat
                add(day, "${monday.get(Calendar.MONTH) + 1}/${monday.get(Calendar.DAY_OF_MONTH)}",
                    MermaidGanttTickTier.WEEK)
                monday.add(Calendar.DAY_OF_MONTH, 7)
            }
        }
        MermaidGanttTickTier.MONTH -> {
            add(minDay, monthFormat.format(first.time), MermaidGanttTickTier.MONTH)
            val month = date(minDay).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, 1)
            }
            repeat(512) {
                val day = month.timeInMillis / 86_400_000L
                if (day > maxDay) return@repeat
                add(day, monthFormat.format(month.time), MermaidGanttTickTier.MONTH)
                month.add(Calendar.MONTH, 1)
            }
        }
    }
    return ticks.sortedWith(compareBy<MermaidGanttCalendarTick> { it.day }.thenBy { it.tier })
}
