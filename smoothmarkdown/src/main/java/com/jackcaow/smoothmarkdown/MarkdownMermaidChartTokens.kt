package com.jackcaow.smoothmarkdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.mermaid.MermaidGanttStatus
import com.jackcaow.smoothmarkdown.mermaid.MermaidKanbanPriority

/** Shared chart appearance. Empty palettes/maps and null overrides preserve existing defaults.
 * Dimensions use unscaled diagram dp. These tokens change painting, not layout algorithms. */
data class MarkdownMermaidChartTokens(
    val seriesColors: List<Color> = emptyList(),
    val ganttStatusColors: Map<MermaidGanttStatus, Color> = emptyMap(),
    val kanbanPriorityColors: Map<MermaidKanbanPriority, Color> = emptyMap(),
    val todayColor: Color? = null,
    val overLimitColor: Color? = null,
    val atLimitColor: Color? = null,
    val belowLimitColor: Color? = null,
    val gridColor: Color? = null,
    val columnBackground: Color? = null,
    val cardBackground: Color? = null,
    val sectorBorderColor: Color? = null,
    val sectorBorderWidth: Dp? = null,
    val seriesLineWidth: Dp? = null,
    val pointRadius: Dp? = null,
    val fillOpacity: Float? = null,
    val barCornerRadius: Dp = 3.dp,
    val taskCornerRadius: Dp = 4.dp,
    val columnCornerRadius: Dp = 8.dp,
    val cardCornerRadius: Dp = 6.dp,
    val borderWidth: Dp = 1.dp,
    val gridWidth: Dp = 1.dp,
    val priorityStripeWidth: Dp = 4.dp,
    val todayLineWidth: Dp = 2.dp,
) {
    /** Safe copy without mutating host collections or configuration. */
    fun normalized(): MarkdownMermaidChartTokens = copy(
        seriesColors = seriesColors.filter { it != Color.Unspecified },
        ganttStatusColors = ganttStatusColors.filterValues { it != Color.Unspecified },
        kanbanPriorityColors = kanbanPriorityColors.filterValues { it != Color.Unspecified },
        todayColor = todayColor?.specified(), overLimitColor = overLimitColor?.specified(),
        atLimitColor = atLimitColor?.specified(), belowLimitColor = belowLimitColor?.specified(),
        gridColor = gridColor?.specified(), columnBackground = columnBackground?.specified(),
        cardBackground = cardBackground?.specified(), sectorBorderColor = sectorBorderColor?.specified(),
        sectorBorderWidth = sectorBorderWidth?.dimension(), seriesLineWidth = seriesLineWidth?.dimension(),
        pointRadius = pointRadius?.dimension(), fillOpacity = fillOpacity?.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f },
        barCornerRadius = barCornerRadius.dimension(), taskCornerRadius = taskCornerRadius.dimension(),
        columnCornerRadius = columnCornerRadius.dimension(), cardCornerRadius = cardCornerRadius.dimension(),
        borderWidth = borderWidth.dimension(), gridWidth = gridWidth.dimension(),
        priorityStripeWidth = priorityStripeWidth.dimension(), todayLineWidth = todayLineWidth.dimension(),
    )

    internal fun seriesColor(index: Int, fallback: Color): Color =
        if (seriesColors.isEmpty()) fallback else seriesColors[index.coerceAtLeast(0) % seriesColors.size]
}

private fun Dp.dimension(): Dp = if (value.isFinite()) value.coerceAtLeast(0f).dp else 0.dp
private fun Color.specified(): Color? = takeIf { it != Color.Unspecified }
