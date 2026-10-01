package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection
import com.jackcaow.smoothmarkdown.ast.Node

/** AST order remains stable when table cells wrap, content is RTL, or items are recycled. */
internal class ReaderSelectionOrder(val path: List<Int>) {
    private var nextSlot = 0
    fun allocate(): List<Int> = path + nextSlot++
}
internal val LocalReaderSelectionOrder = compositionLocalOf<ReaderSelectionOrder?> { null }
internal val LocalReaderSelectionSourceOrder = compositionLocalOf<Map<Node, Int>> { emptyMap() }

@Composable
internal fun rememberReaderSelectionOrder(): List<Int>? {
    val order = LocalReaderSelectionOrder.current
    return remember(order) { order?.allocate() }
}

internal fun readerSelectionSourceOrder(document: Node): Map<Node, Int> {
    val result = linkedMapOf<Node, Int>()
    fun visit(node: Node) {
        if (node in result) return
        result[node] = result.size
        if (node is DetailsNode) (node.summary + node.body).forEach(::visit)
        node.children().forEach(::visit)
    }
    visit(document)
    return result
}

internal fun compareReaderSelectionOrder(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val compared = a[i].compareTo(b[i])
        if (compared != 0) return compared
    }
    return a.size.compareTo(b.size)
}

/**
 * AST scopes determine document/table order; positioned children determine order within a scope.
 * A builder may recompose by itself, retaining its allocation scope while inserting a new first
 * Text. Allocation slots therefore cannot determine that builder's current child order.
 */
internal fun orderedReaderSelectionTargets(targets: Collection<MarkdownSelectionTarget>): List<MarkdownSelectionTarget> {
    fun scope(target: MarkdownSelectionTarget): List<Int>? = target.sourceOrder?.let {
        if (it.size > 1) it.dropLast(1) else it
    }
    // Use one direction per scope so comparison stays transitive even if a custom builder
    // overrides the layout direction of an individual Text. The earliest live slot represents
    // the scope's established text direction, independently of registration order.
    val directions = targets.groupBy(::scope).mapValues { (_, children) ->
        children.sortedWith { a, b -> compareReaderSelectionOrder(a.sourceOrder.orEmpty(), b.sourceOrder.orEmpty()) }
            .firstNotNullOfOrNull { it.layoutResult?.layoutInput?.layoutDirection } ?: LayoutDirection.Ltr
    }
    return targets.sortedWith { a, b ->
        val aScope = scope(a)
        val bScope = scope(b)
        val sourceComparison = when {
            aScope == null && bScope != null -> 1
            aScope != null && bScope == null -> -1
            aScope != null && bScope != null -> compareReaderSelectionOrder(aScope, bScope)
            else -> 0
        }
        if (sourceComparison != 0) sourceComparison
        else {
            val vertical = a.boundsInWindow.top.compareTo(b.boundsInWindow.top)
            if (vertical != 0) vertical
            else {
                val horizontal = if (directions[aScope] == LayoutDirection.Rtl && aScope == bScope) {
                    b.boundsInWindow.right.compareTo(a.boundsInWindow.right)
                } else a.boundsInWindow.left.compareTo(b.boundsInWindow.left)
                if (horizontal != 0) horizontal
                else compareReaderSelectionOrder(a.sourceOrder.orEmpty(), b.sourceOrder.orEmpty())
            }
        }
    }
}
