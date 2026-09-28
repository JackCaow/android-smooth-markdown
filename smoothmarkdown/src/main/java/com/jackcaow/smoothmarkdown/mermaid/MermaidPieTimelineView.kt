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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

internal val piePalette = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548),
    Color(0xFF607D8B), Color(0xFFF44336), Color(0xFF3F51B5), Color(0xFF009688),
)

@Composable
internal fun MermaidPieView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier) {
    val pie = requireNotNull(diagram.pie)
    val placement = requireNotNull(layout.pie)
    val surface = MaterialTheme.colorScheme.surface
    val foreground = MaterialTheme.colorScheme.onSurface
    Box(modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val left = (placement.center.x - placement.radius) * density
                val top = (placement.center.y - placement.radius) * density
                val diameter = placement.radius * 2 * density
                placement.slices.forEach { slice ->
                    val color = piePalette[slice.index % piePalette.size]
                    val origin = Offset(left, top)
                    val size = Size(diameter, diameter)
                    drawArc(color, slice.startAngle, slice.sweepAngle, useCenter = true, topLeft = origin, size = size)
                    drawArc(surface, slice.startAngle, slice.sweepAngle, useCenter = true,
                        topLeft = origin, size = size, style = Stroke(2.dp.toPx()))
                    drawRoundRect(color, Offset(22.dp.toPx(), (placement.legendY + slice.index * 30f).dp.toPx()),
                        Size(16.dp.toPx(), 16.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
            }
            pie.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(title, modifier = Modifier.offset(24.dp, 8.dp).width(312.dp),
                    color = foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            placement.slices.forEach { slice ->
                val value = if (pie.showValuesInLegend) ": ${String.format(Locale.US, "%.0f", slice.slice.value)}" else ""
                val percentage = String.format(Locale.US, "%.1f", pie.percentage(slice.slice))
                Text("${slice.slice.label}$value ($percentage%)",
                    modifier = Modifier.offset(48.dp, (placement.legendY + slice.index * 30f - 3f).dp).width(292.dp),
                    color = foreground, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun MermaidTimelineView(diagram: MermaidDiagram, layout: MermaidLayoutResult, modifier: Modifier) {
    val data = requireNotNull(diagram.timeline)
    val placement = requireNotNull(layout.timeline)
    val foreground = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(accent, Offset(24.dp.toPx(), placement.axisY.dp.toPx()),
                    Offset((layout.width - 24f).dp.toPx(), placement.axisY.dp.toPx()), 3.dp.toPx())
                placement.sections.forEach { section ->
                    val color = piePalette[section.index % piePalette.size]
                    val marker = Offset(section.marker.x.dp.toPx(), section.marker.y.dp.toPx())
                    drawLine(color, marker, Offset(marker.x, section.card.y.dp.toPx()), 2.dp.toPx())
                    drawCircle(color, 7.dp.toPx(), marker)
                    drawCircle(Color.White, 3.dp.toPx(), marker)
                    val topLeft = Offset(section.card.x.dp.toPx(), section.card.y.dp.toPx())
                    val size = Size(section.card.width.dp.toPx(), section.card.height.dp.toPx())
                    drawRoundRect(surface, topLeft, size, CornerRadius(7.dp.toPx()))
                    drawRoundRect(color, topLeft, size, CornerRadius(7.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                }
            }
            data.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(title, modifier = Modifier.offset(24.dp, 8.dp).width((layout.width - 48f).dp),
                    color = foreground, fontWeight = FontWeight.Bold, maxLines = 2)
            }
            placement.sections.forEach { section ->
                val x = section.card.x + 9f
                Text(section.section.title,
                    modifier = Modifier.offset(x.dp, (section.card.y + 8f).dp).width((section.card.width - 18f).dp),
                    color = piePalette[section.index % piePalette.size], fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                var eventOffset = 35f
                section.section.events.forEach { event ->
                    Text(event.title,
                        modifier = Modifier.offset(x.dp, (section.card.y + eventOffset).dp)
                            .width((section.card.width - 18f).dp),
                        color = foreground, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    event.description?.takeIf(String::isNotBlank)?.let { description ->
                        Text(description,
                            modifier = Modifier.offset(x.dp, (section.card.y + eventOffset + 20f).dp)
                                .width((section.card.width - 18f).dp),
                            color = foreground.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        eventOffset += 50f
                    } ?: run { eventOffset += 30f }
                }
            }
        }
    }
}
