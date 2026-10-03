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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

private fun DrawScope.pathFor(points: List<MermaidPoint>, closed: Boolean): Path = Path().apply {
    points.forEachIndexed { index, point ->
        if (index == 0) moveTo(point.x * density, point.y * density)
        else lineTo(point.x * density, point.y * density)
    }
    if (closed) close()
}

private fun number(value: Double): String = if (abs(value - value.toLong()) < 0.0001)
    value.toLong().toString() else String.format(Locale.US, "%.1f", value)

@Composable
internal fun MermaidRadarView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier) {
    val data = requireNotNull(diagram.radar)
    val place = requireNotNull(layout.radar)
    val foreground = MaterialTheme.colorScheme.onSurface
    val grid = foreground.copy(alpha = 0.25f)
    MermaidViewport(layout, modifier) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                place.rings.forEach { ring ->
                    if (data.graticule == MermaidRadarGraticule.Circle) {
                        val radius = place.radius * (place.rings.indexOf(ring) + 1) / data.ticks
                        drawCircle(grid, radius.dp.toPx(), Offset(place.center.x.dp.toPx(), place.center.y.dp.toPx()),
                            style = Stroke(1.dp.toPx()))
                    } else drawPath(pathFor(ring, true), grid, style = Stroke(1.dp.toPx()))
                }
                place.axisEnds.forEach { point ->
                    drawLine(grid, Offset(place.center.x.dp.toPx(), place.center.y.dp.toPx()),
                        Offset(point.x.dp.toPx(), point.y.dp.toPx()), 1.dp.toPx())
                }
                place.curves.forEachIndexed { index, curve ->
                    val color = piePalette[index % piePalette.size]
                    drawPath(pathFor(curve, true), color.copy(alpha = 0.17f))
                    drawPath(pathFor(curve, true), color, style = Stroke(2.5.dp.toPx()))
                    curve.forEach { point ->
                        drawCircle(color, 4.dp.toPx(), Offset(point.x.dp.toPx(), point.y.dp.toPx()))
                    }
                    if (data.showLegend) {
                        drawRoundRect(color, Offset(88.dp.toPx(), (place.legendY + index * 26f).dp.toPx()),
                            Size(14.dp.toPx(), 14.dp.toPx()), CornerRadius(3.dp.toPx()))
                    }
                }
            }
            data.title?.let {
                Text(it, Modifier.offset(28.dp, 12.dp).width(364.dp),
                    color = foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            data.axes.forEachIndexed { index, axis ->
                val point = place.axisEnds[index]
                val x = (point.x - 42f).coerceIn(0f, layout.width - 84f)
                val y = if (point.y < place.center.y - 60f) point.y - 31f
                    else if (point.y > place.center.y + 60f) point.y + 8f else point.y - 9f
                Text(axis.label, Modifier.offset(x.dp, y.dp).width(84.dp), color = foreground,
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("${number(data.effectiveMin)} – ${number(data.effectiveMax)}",
                Modifier.offset(12.dp, (place.legendY - 25f).dp),
                color = foreground.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            if (data.showLegend) data.curves.forEachIndexed { index, curve ->
                Text(curve.label, Modifier.offset(110.dp, (place.legendY + index * 26f - 2f).dp).width(260.dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun MermaidXYChartView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier) {
    val data = requireNotNull(diagram.xyChart)
    val place = requireNotNull(layout.xyChart)
    val foreground = MaterialTheme.colorScheme.onSurface
    val horizontal = data.orientation == MermaidXYOrientation.Horizontal
    val plot = place.plot
    MermaidViewport(layout, modifier) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val grid = foreground.copy(alpha = 0.18f)
                for (step in 0..5) {
                    if (horizontal) {
                        val x = (plot.x + plot.width * step / 5f).dp.toPx()
                        drawLine(grid, Offset(x, plot.y.dp.toPx()),
                            Offset(x, (plot.y + plot.height).dp.toPx()), 1.dp.toPx())
                    } else {
                        val y = (plot.y + plot.height * step / 5f).dp.toPx()
                        drawLine(grid, Offset(plot.x.dp.toPx(), y),
                            Offset((plot.x + plot.width).dp.toPx(), y), 1.dp.toPx())
                    }
                }
                place.bars.forEach { bar ->
                    val color = piePalette[bar.seriesIndex % piePalette.size]
                    drawRoundRect(color, Offset(bar.rect.x.dp.toPx(), bar.rect.y.dp.toPx()),
                        Size(bar.rect.width.dp.toPx(), bar.rect.height.dp.toPx()),
                        CornerRadius(3.dp.toPx()))
                }
                place.lines.forEach { (seriesIndex, points) ->
                    val color = piePalette[seriesIndex % piePalette.size]
                    if (points.size > 1) drawPath(pathFor(points, false), color, style = Stroke(2.5.dp.toPx()))
                    points.forEach { point ->
                        drawCircle(color, 4.dp.toPx(), Offset(point.x.dp.toPx(), point.y.dp.toPx()))
                    }
                }
                if (horizontal) drawLine(foreground, Offset(place.baseline.dp.toPx(), plot.y.dp.toPx()),
                    Offset(place.baseline.dp.toPx(), (plot.y + plot.height).dp.toPx()), 1.dp.toPx())
                else drawLine(foreground, Offset(plot.x.dp.toPx(), place.baseline.dp.toPx()),
                    Offset((plot.x + plot.width).dp.toPx(), place.baseline.dp.toPx()), 1.dp.toPx())
            }
            data.title?.let {
                Text(it, Modifier.offset(20.dp, 10.dp).width((layout.width - 40f).dp),
                    color = foreground, fontWeight = FontWeight.Bold, maxLines = 1,
                    textAlign = TextAlign.Center)
            }
            place.categoryCenters.forEachIndexed { index, center ->
                val label = data.categories.getOrNull(index) ?: (index + 1).toString()
                if (horizontal) Text(label,
                    Modifier.offset(8.dp, (center - 10f).dp).width((plot.x - 20f).dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Text(label,
                    Modifier.offset((center - 33f).dp, (plot.y + plot.height + 10f).dp).width(66.dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            for (step in 0..5) {
                val tick = data.effectiveMin + (data.effectiveMax - data.effectiveMin) * step / 5
                if (horizontal) Text(number(tick),
                    Modifier.offset((plot.x + plot.width * step / 5f - 17f).dp,
                        (plot.y + plot.height + 9f).dp).width(48.dp),
                    color = foreground.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                else Text(number(tick),
                    Modifier.offset(4.dp, (plot.y + plot.height * (1f - step / 5f) - 8f).dp).width(52.dp),
                    color = foreground.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End)
            }
            data.xAxisTitle?.let {
                Text(it, Modifier.offset(plot.x.dp, (plot.y + plot.height + if (horizontal) 31f else 40f).dp)
                    .width(plot.width.dp), color = foreground, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center)
            }
            data.yAxisTitle?.let {
                Text(it, Modifier.offset(4.dp, (plot.y - 20f).dp).width(150.dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
