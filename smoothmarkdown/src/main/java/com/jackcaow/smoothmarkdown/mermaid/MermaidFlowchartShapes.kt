package com.jackcaow.smoothmarkdown.mermaid

/** Shape outlines in diagram units, matching Flutter's flowchart painter. */
internal fun flowchartPolygon(shape: MermaidShape, rect: MermaidRect): List<MermaidPoint>? {
    val left = rect.x
    val top = rect.y
    val right = left + rect.width
    val bottom = top + rect.height
    val centerX = rect.centerX
    val centerY = rect.centerY
    fun point(x: Float, y: Float) = MermaidPoint(x, y)
    return when (shape) {
        MermaidShape.Diamond -> listOf(point(centerX, top), point(right, centerY),
            point(centerX, bottom), point(left, centerY))
        MermaidShape.Hexagon -> {
            val inset = rect.width * .15f
            listOf(point(left + inset, top), point(right - inset, top), point(right, centerY),
                point(right - inset, bottom), point(left + inset, bottom), point(left, centerY))
        }
        MermaidShape.Parallelogram -> {
            val skew = rect.width * .15f
            listOf(point(left + skew, top), point(right, top), point(right - skew, bottom), point(left, bottom))
        }
        MermaidShape.ParallelogramAlt -> {
            val skew = rect.width * .15f
            listOf(point(left, top), point(right - skew, top), point(right, bottom), point(left + skew, bottom))
        }
        MermaidShape.Trapezoid -> {
            val inset = rect.width * .1f
            listOf(point(left + inset, top), point(right - inset, top), point(right, bottom), point(left, bottom))
        }
        MermaidShape.TrapezoidAlt -> {
            val inset = rect.width * .1f
            listOf(point(left, top), point(right, top), point(right - inset, bottom), point(left + inset, bottom))
        }
        MermaidShape.Asymmetric -> listOf(point(left, top), point(right - 10f, top),
            point(right, centerY), point(right - 10f, bottom), point(left, bottom))
        else -> null
    }
}
