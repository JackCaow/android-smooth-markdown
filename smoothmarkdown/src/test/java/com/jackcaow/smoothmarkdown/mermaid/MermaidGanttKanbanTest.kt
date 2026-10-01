package com.jackcaow.smoothmarkdown.mermaid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidGanttKanbanTest {
    @Test fun ganttMatchesFlutterSectionStatusDependencyAndEndDateFixtures() {
        val diagram = MermaidParser.parse("""
            gantt
              title Software Development Timeline
              dateFormat YYYY-MM-DD
              axisFormat %b %d
              excludes weekends
              todayMarker off
              section Planning
                Requirements :done, req, 2024-01-01, 14d
                Design :active, design, after req, 10d
              section Development
                Frontend :crit, front, 2024-01-25, 2024-02-05
                Release :milestone, rel, after front, 0d
        """.trimIndent())!!
        assertEquals(MermaidKind.Gantt, diagram.kind)
        val data = diagram.gantt!!
        assertEquals("Software Development Timeline", data.title)
        assertEquals(listOf("Planning", "Development"), data.sections)
        assertEquals(listOf(MermaidGanttStatus.Done, MermaidGanttStatus.Active,
            MermaidGanttStatus.Critical, MermaidGanttStatus.Milestone), data.tasks.map { it.status })
        assertEquals(listOf("req"), data.task("design")!!.dependencies)
        assertEquals(data.task("req")!!.endDay + 1, data.task("design")!!.startDay)
        assertEquals(12, data.task("front")!!.endDay - data.task("front")!!.startDay + 1)
        assertEquals(data.task("rel")!!.startDay, data.task("rel")!!.endDay)
        assertEquals("weekends", data.excludes)
        assertEquals("%b %d", data.axisFormat)
        assertTrue(!data.todayMarker)
        val placement = MermaidLayout.compute(diagram).gantt!!
        assertEquals(4, placement.tasks.size)
        assertTrue(placement.tasks[1].rowY > placement.tasks[0].rowY)
        assertTrue(placement.tasks[1].bar.x > placement.tasks[0].bar.x)
    }

    @Test fun ganttSupportsAlternateDatesAndRejectsInvalidOrEmptyCharts() {
        val diagram = MermaidParser.parse("gantt\nTask A :a, 01/01/2024, 2w\nTask B :b, 01-15-2024, 3d")!!
        assertEquals(14, diagram.gantt!!.task("a")!!.endDay - diagram.gantt!!.task("a")!!.startDay + 1)
        assertNull(MermaidParser.parse("gantt\n title Empty"))
        assertNull(MermaidParser.parse("gantt\n Task :x, 2024-02-30, 3d"))
        assertNull(MermaidParser.parse("gantt\n Task :x, after missing, 3d"))
        assertNull(MermaidParser.parse("gantt\n Task :x, 2024-01-01, 3d\n Unknown directive"))
    }

    @Test fun ganttTodayMarkerUsesTheSameInclusiveCalendarRangeAsFlutter() {
        val source = "gantt\nTask :a, 2024-01-01, 3d"
        val on = MermaidParser.parse(source)!!.gantt!!
        val placement = MermaidLayout.compute(MermaidParser.parse(source)!!).gantt!!
        val firstDay = on.minDay
        val dayWidth = placement.chartWidth / 3f
        assertEquals(placement.chartX, placement.todayMarkerX(firstDay, on.todayMarker)!!, 0.001f)
        assertEquals(placement.chartX + 2f * dayWidth,
            placement.todayMarkerX(firstDay + 2, on.todayMarker)!!, 0.001f)
        assertNull(placement.todayMarkerX(firstDay - 1, on.todayMarker))
        assertNull(placement.todayMarkerX(firstDay + 3, on.todayMarker))

        val off = MermaidParser.parse("gantt\ntodayMarker off\nTask :a, 2024-01-01, 3d")!!.gantt!!
        assertNull(placement.todayMarkerX(firstDay, off.todayMarker))
    }

    @Test fun ganttHeaderUsesUtcDayWeekAndMonthTicksAtFlutterScaleThresholds() {
        fun ticks(start: String, duration: String): List<MermaidGanttCalendarTick> {
            val diagram = MermaidParser.parse("gantt\nTask :a, $start, $duration")!!
            return MermaidLayout.compute(diagram).gantt!!.calendarTicks()
        }

        val days = ticks("2024-01-30", "4d")
        assertEquals(listOf("30", "31", "1", "2"),
            days.filter { it.tier == MermaidGanttTickTier.DAY }.map { it.label })
        assertEquals(listOf("Jan", "Feb"),
            days.filter { it.tier == MermaidGanttTickTier.MONTH }.map { it.label })

        val weeks = ticks("2024-01-03", "90d")
        assertEquals("1/8", weeks.first { it.tier == MermaidGanttTickTier.WEEK }.label)
        assertEquals(listOf("Jan 2024", "Feb 2024", "Mar 2024", "Apr 2024"),
            weeks.filter { it.tier == MermaidGanttTickTier.MONTH }.map { it.label })
        assertTrue(weeks.zipWithNext().all { (a, b) -> a.x <= b.x })

        val months = ticks("2024-01-15", "400d")
        assertTrue(months.all { it.tier == MermaidGanttTickTier.MONTH })
        assertEquals("Jan 2024", months.first().label)
        assertEquals("Feb 2024", months[1].label)
        assertTrue(months.last().x <= MermaidLayout.compute(
            MermaidParser.parse("gantt\nTask :a, 2024-01-15, 400d")!!).gantt!!.let {
            it.chartX + it.chartWidth
        })
    }

    @Test fun kanbanMatchesFlutterMetadataWipAndFrontmatterFixtures() {
        val diagram = MermaidParser.parse("""
            ---
            config:
              kanban:
                ticketBaseUrl: 'https://example.com/#TICKET#'
            ---
            kanban
              title Product Development
              todo[To Do] wip:1
                task1[Fix bug] @{ assigned: "Alice", ticket: "123", priority: "Very High" }
                task2[Write tests] @{ priority: "Low", effort: "2d" }
              done[Done]
                task3[Release] @{ priority: "Very Low" }
        """.trimIndent())!!
        assertEquals(MermaidKind.Kanban, diagram.kind)
        val data = diagram.kanban!!
        assertEquals("Product Development", data.title)
        assertEquals("https://example.com/#TICKET#", data.ticketBaseUrl)
        assertEquals(listOf("To Do", "Done"), data.columns.map { it.title })
        assertTrue(data.columns[0].isOverLimit)
        assertEquals(3, data.allTasks.size)
        assertEquals("Alice", data.task("task1")!!.assigned)
        assertEquals("123", data.task("task1")!!.ticket)
        assertEquals(MermaidKanbanPriority.VeryHigh, data.task("task1")!!.priority)
        assertEquals("2d", data.task("task2")!!.metadata["effort"])
        assertEquals(MermaidKanbanPriority.VeryLow, data.task("task3")!!.priority)
        val placement = MermaidLayout.compute(diagram).kanban!!
        assertEquals(2, placement.columns.size)
        assertEquals(2, placement.columns[0].cards.size)
        assertTrue(placement.columns[1].box.x > placement.columns[0].box.x)
    }

    @Test fun kanbanRequiresColumnsAndPreservesUnsupportedSyntaxAsFallback() {
        assertNull(MermaidParser.parse("kanban\n title Empty"))
        assertNull(MermaidParser.parse("kanban\n  todo[To Do]\n    invalid task syntax"))
        assertNull(MermaidParser.parse("kanban\n    task1[Orphan]"))
        assertNull(MermaidParser.parse("---\nconfig:\nkanban\n  todo[To Do]"))
        assertNull(MermaidParser.parse("radar-beta\n  axis A"))
    }
}
