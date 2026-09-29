package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

@Composable
internal fun MermaidERView(
    diagram: MermaidDiagram,
    layout: MermaidLayoutResult,
    modifier: Modifier,
    onNodeTap: ((String) -> Unit)?,
) {
    val data = requireNotNull(diagram.er)
    val place = requireNotNull(layout.er)
    val foreground = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val header = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Box(Modifier.size(layout.width.dp, layout.height.dp).mermaidNodeTaps(diagram, layout, onNodeTap)) {
            Canvas(Modifier.fillMaxSize()) {
                place.relationships.forEach { placed ->
                    val points = placed.points
                    val effect = if (placed.relationship.dotted)
                        PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null
                    points.zipWithNext { a, b ->
                        drawLine(foreground.copy(alpha = 0.7f), Offset(a.x.dp.toPx(), a.y.dp.toPx()),
                            Offset(b.x.dp.toPx(), b.y.dp.toPx()), 1.5.dp.toPx(), pathEffect = effect)
                    }
                    drawERMarker(points.first(), points[1], placed.relationship.sourceCardinality, foreground)
                    drawERMarker(points.last(), points[points.lastIndex - 1],
                        placed.relationship.targetCardinality, foreground)
                }
                data.entities.forEach { entity ->
                    val rect = place.entities.getValue(entity.id)
                    val origin = Offset(rect.x.dp.toPx(), rect.y.dp.toPx())
                    val size = Size(rect.width.dp.toPx(), rect.height.dp.toPx())
                    drawRoundRect(surface, origin, size, CornerRadius(6.dp.toPx()))
                    drawRoundRect(foreground.copy(alpha = 0.7f), origin, size, CornerRadius(6.dp.toPx()),
                        style = Stroke(1.3.dp.toPx()))
                    drawRect(header, origin, Size(size.width, 40.dp.toPx()))
                    if (entity.attributes.isNotEmpty()) {
                        drawLine(foreground.copy(alpha = 0.5f),
                            Offset(origin.x, origin.y + 40.dp.toPx()),
                            Offset(origin.x + size.width, origin.y + 40.dp.toPx()), 1.dp.toPx())
                    }
                }
            }
            data.entities.forEach { entity ->
                val rect = place.entities.getValue(entity.id)
                Text(entity.label, Modifier.offset((rect.x + 8f).dp, (rect.y + 10f).dp)
                    .width((rect.width - 16f).dp), color = foreground,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                entity.attributes.forEachIndexed { index, attribute ->
                    Text(attribute, Modifier.offset((rect.x + 10f).dp,
                        (rect.y + 47f + index * 25f).dp).width((rect.width - 20f).dp),
                        color = foreground, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            place.relationships.forEach { placed ->
                val p = placed.labelAt
                Text(placed.relationship.label,
                    Modifier.offset((p.x - 48f).dp, (p.y - 19f).dp).width(96.dp)
                        .background(surface).padding(horizontal = 3.dp),
                    color = foreground, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Draw the crow's foot, optional circle, and exact-one bars at a relationship endpoint. */
private fun DrawScope.drawERMarker(
    endpoint: MermaidPoint, toward: MermaidPoint, cardinality: MermaidERCardinality, color: Color,
) {
    val start = Offset(endpoint.x.dp.toPx(), endpoint.y.dp.toPx())
    val target = Offset(toward.x.dp.toPx(), toward.y.dp.toPx())
    val length = hypot(target.x - start.x, target.y - start.y).coerceAtLeast(1f)
    val ux = (target.x - start.x) / length
    val uy = (target.y - start.y) / length
    val px = -uy
    val py = ux
    fun at(distance: Float, side: Float = 0f) = Offset(start.x + ux * distance + px * side,
        start.y + uy * distance + py * side)
    fun bar(distance: Float) = drawLine(color, at(distance, -6.dp.toPx()),
        at(distance, 6.dp.toPx()), 1.8.dp.toPx())
    fun circle(distance: Float) = drawCircle(color, 4.dp.toPx(), at(distance), style = Stroke(1.8.dp.toPx()))
    fun crow() {
        val center = at(9.dp.toPx())
        drawLine(color, center, at(19.dp.toPx(), -7.dp.toPx()), 1.6.dp.toPx())
        drawLine(color, center, at(19.dp.toPx(), 7.dp.toPx()), 1.6.dp.toPx())
    }
    when (cardinality) {
        MermaidERCardinality.ExactlyOne -> { bar(8.dp.toPx()); bar(15.dp.toPx()) }
        MermaidERCardinality.ZeroOrOne -> { bar(8.dp.toPx()); circle(18.dp.toPx()) }
        MermaidERCardinality.OneOrMore -> { crow(); bar(23.dp.toPx()) }
        MermaidERCardinality.ZeroOrMore -> { crow(); circle(26.dp.toPx()) }
    }
}
