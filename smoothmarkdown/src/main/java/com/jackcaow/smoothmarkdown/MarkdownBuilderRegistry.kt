package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import org.commonmark.node.Node
import kotlin.reflect.KClass

/** Presentation of a node embedded in a paragraph, heading, list, or table cell. */
sealed interface MarkdownInlinePresentation {
    /** Replaces the node and its descendants with styled text. */
    data class Text(val text: String, val style: SpanStyle = SpanStyle()) : MarkdownInlinePresentation

    /**
     * Uses [MarkdownNodeBuilder.Render] as inline content. Compose needs a known placeholder
     * size before laying out the surrounding text. [fallbackText] is used for selection/copy.
     */
    data class Widget(val width: Dp, val height: Dp, val fallbackText: String) : MarkdownInlinePresentation
}

/** Context passed to a custom Compose node builder. Child rendering uses the same registry. */
class MarkdownBuilderContext internal constructor(
    val styleSheet: MarkdownStyleSheet,
    val enableHtml: Boolean,
    val onLinkClick: (String) -> Unit,
    val onImageClick: (String) -> Unit,
    private val childRenderer: @Composable (Node) -> Unit,
    private val inlineChildRenderer: @Composable (Node, TextStyle?) -> Unit,
) {
    @Composable fun renderChild(node: Node) = childRenderer(node)

    @Composable fun renderChildren(node: Node) {
        var child = node.firstChild
        while (child != null) {
            childRenderer(child)
            child = child.next
        }
    }

    /** Renders the node's inline descendants together, retaining registered inline builders. */
    @Composable fun renderInlineChildren(node: Node, style: TextStyle? = null) =
        inlineChildRenderer(node, style)
}

/**
 * Builds a parsed CommonMark node. A registered builder takes precedence over built-in rendering
 * when [canBuild] accepts it. [Render] handles block nodes and inline [MarkdownInlinePresentation.Widget]
 * nodes; [renderInline] handles nodes inside Compose's single text layout.
 */
interface MarkdownNodeBuilder {
    fun canBuild(node: Node): Boolean
    @Composable fun Render(node: Node, context: MarkdownBuilderContext)
    fun renderInline(node: Node): MarkdownInlinePresentation? = null
}

/**
 * Opt-in native equivalent of Flutter's BuilderRegistry. Keys are CommonMark node classes, since
 * CommonMark does not expose Flutter's string node types. Exact class matches are tried first;
 * if rejected, builders are checked in registration order. Re-registering a key replaces its
 * builder without moving its position in that fallback order.
 */
class MarkdownBuilderRegistry {
    private val builders = linkedMapOf<KClass<out Node>, MarkdownNodeBuilder>()

    fun register(nodeType: KClass<out Node>, builder: MarkdownNodeBuilder): MarkdownBuilderRegistry = apply {
        builders[nodeType] = builder
    }

    inline fun <reified T : Node> register(builder: MarkdownNodeBuilder): MarkdownBuilderRegistry =
        register(T::class, builder)

    fun getBuilder(nodeType: KClass<out Node>): MarkdownNodeBuilder? = builders[nodeType]
    fun hasBuilder(nodeType: KClass<out Node>): Boolean = builders.containsKey(nodeType)
    fun unregister(nodeType: KClass<out Node>): MarkdownNodeBuilder? = builders.remove(nodeType)
    fun clear() = builders.clear()
    fun copy(): MarkdownBuilderRegistry = MarkdownBuilderRegistry().also { it.builders.putAll(builders) }

    fun findBuilder(node: Node): MarkdownNodeBuilder? {
        builders[node::class]?.let { if (it.canBuild(node)) return it }
        return builders.values.firstOrNull { it.canBuild(node) }
    }
}
