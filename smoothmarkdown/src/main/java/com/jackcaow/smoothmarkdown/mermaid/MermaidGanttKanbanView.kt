package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun formatDay(day: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(day * 86_400_000L))

@Composable
internal fun MermaidGanttView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier) {
    val data = requireNotNull(diagram.gantt)
    val placement = requireNotNull(layout.gantt)
    val foreground = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val chartLeft = placement.chartX.dp.toPx()
                val chartRight = (placement.chartX + placement.chartWidth).dp.toPx()
                val top = (placement.headerY + 48f).dp.toPx()
                val bottom = (layout.height - 20f).dp.toPx()
                for (step in 0..5) {
                    val x = chartLeft + (chartRight - chartLeft) * step / 5f
                    drawLine(foreground.copy(alpha = 0.18f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                }
                placement.tasks.forEachIndexed { index, task ->
                    if (index % 2 == 0) {
                        drawRect(surface.copy(alpha = 0.35f),
                            Offset(12.dp.toPx(), task.rowY.dp.toPx()),
                            Size((layout.width - 24f).dp.toPx(), 44.dp.toPx()))
                    }
                    val color = when (task.task.status) {
                        MermaidGanttStatus.Done -> Color(0xFF4CAF50)
                        MermaidGanttStatus.Active -> Color(0xFF2196F3)
                        MermaidGanttStatus.Critical -> Color(0xFFF44336)
                        MermaidGanttStatus.Milestone -> Color(0xFFFF9800)
                        MermaidGanttStatus.Normal -> accent
                    }
                    val bar = task.bar
                    if (task.task.status == MermaidGanttStatus.Milestone) {
                        val x = bar.x.dp.toPx() + 8.dp.toPx()
                        val y = bar.y.dp.toPx() + 10.dp.toPx()
                        val radius = 9.dp.toPx()
                        val diamond = Path().apply {
                            moveTo(x, y - radius); lineTo(x + radius, y)
                            lineTo(x, y + radius); lineTo(x - radius, y); close()
                        }
                        drawPath(diamond, color)
                    } else {
                        drawRoundRect(color, Offset(bar.x.dp.toPx(), bar.y.dp.toPx()),
                            Size(bar.width.dp.toPx(), bar.height.dp.toPx()), CornerRadius(4.dp.toPx()))
                    }
                }
            }
            data.title?.let {
                Text(it, Modifier.offset(16.dp, 10.dp).width((layout.width - 32f).dp),
                    color = foreground, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(formatDay(placement.minDay), Modifier.offset(placement.chartX.dp, placement.headerY.dp),
                color = foreground, style = MaterialTheme.typography.labelSmall)
            Text(formatDay(placement.maxDay),
                Modifier.offset((placement.chartX + placement.chartWidth - 90f).dp, placement.headerY.dp),
                color = foreground, style = MaterialTheme.typography.labelSmall)
            placement.tasks.forEach { item ->
                val label = item.task.section?.let { "$it · ${item.task.name}" } ?: item.task.name
                Text(label, Modifier.offset(16.dp, (item.rowY + 12f).dp).width(164.dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun MermaidKanbanView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier) {
    val data = requireNotNull(diagram.kanban)
    val placement = requireNotNull(layout.kanban)
    val foreground = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val cardSurface = MaterialTheme.colorScheme.surface
    Box(modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                placement.columns.forEach { item ->
                    val box = item.box
                    val origin = Offset(box.x.dp.toPx(), box.y.dp.toPx())
                    val size = Size(box.width.dp.toPx(), box.height.dp.toPx())
                    drawRoundRect(surface, origin, size, CornerRadius(8.dp.toPx()))
                    drawRoundRect(foreground.copy(alpha = 0.3f), origin, size,
                        CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
                    drawLine(foreground.copy(alpha = 0.2f),
                        Offset(origin.x, (box.y + 52f).dp.toPx()),
                        Offset(origin.x + size.width, (box.y + 52f).dp.toPx()), 1.dp.toPx())
                    item.cards.forEachIndexed { index, card ->
                        val priority = item.column.tasks[index].priority
                        val priorityColor = when (priority) {
                            MermaidKanbanPriority.VeryHigh -> Color(0xFFD32F2F)
                            MermaidKanbanPriority.High -> Color(0xFFFF9800)
                            MermaidKanbanPriority.Normal -> Color(0xFF9E9E9E)
                            MermaidKanbanPriority.Low -> Color(0xFF2196F3)
                            MermaidKanbanPriority.VeryLow -> Color(0xFF4CAF50)
                        }
                        val cardOrigin = Offset(card.x.dp.toPx(), card.y.dp.toPx())
                        val cardSize = Size(card.width.dp.toPx(), card.height.dp.toPx())
                        drawRoundRect(cardSurface, cardOrigin, cardSize, CornerRadius(6.dp.toPx()))
                        drawRoundRect(foreground.copy(alpha = 0.18f), cardOrigin, cardSize,
                            CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                        drawRoundRect(priorityColor, cardOrigin, Size(4.dp.toPx(), cardSize.height),
                            CornerRadius(2.dp.toPx()))
                    }
                }
            }
            data.title?.let {
                Text(it, Modifier.offset(16.dp, 10.dp).width((layout.width - 32f).dp),
                    color = foreground, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            placement.columns.forEach { item ->
                Text(item.column.title, Modifier.offset((item.box.x + 12f).dp, (item.box.y + 14f).dp)
                    .width(if (item.column.wipLimit == null) 194.dp else 125.dp),
                    color = foreground, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                item.column.wipLimit?.let { limit ->
                    val badgeColor = when {
                        item.column.isOverLimit -> Color(0xFFF44336)
                        item.column.tasks.size == limit -> Color(0xFFB77900)
                        else -> Color(0xFF388E3C)
                    }
                    Text("${item.column.tasks.size}/$limit",
                        Modifier.offset((item.box.x + 164f).dp, (item.box.y + 17f).dp).width(46.dp),
                        color = badgeColor, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall)
                }
                item.cards.forEachIndexed { index, card ->
                    val task = item.column.tasks[index]
                    Text(task.description,
                        Modifier.offset((card.x + 12f).dp, (card.y + 9f).dp).width(180.dp),
                        color = foreground, fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    val detail = listOfNotNull(task.assigned, task.ticket).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(detail,
                            Modifier.offset((card.x + 12f).dp, (card.y + 57f).dp).width(180.dp),
                            color = foreground.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
