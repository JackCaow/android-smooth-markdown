package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import com.jackcaow.smoothmarkdown.ast.Node
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

/** How a custom block participates in the Reader's native cross-block selection. */
enum class MarkdownBlockSelectionMode {
    /** Unknown Compose content stays outside neighboring selection groups. */
    NONE,
    /** Render emits selectable text in the same order as its document text projection. */
    NATIVE_TEXT,
    /** Render is visual only; a hidden anchor bridges it and contributes no partial-copy text. */
    NON_TEXT,
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
    /** Text for whole-document copy when [Render] replaces a block; null means it is unknown. */
    fun documentText(node: Node): String? = null
    /** Opt in only when [Render] satisfies the chosen native selection behavior. */
    fun selectionMode(node: Node): MarkdownBlockSelectionMode = MarkdownBlockSelectionMode.NONE
}

/**
 * Opt-in native equivalent of Flutter's BuilderRegistry. Keys are CommonMark node classes, since
 * CommonMark does not expose Flutter's string node types. Exact class matches are tried first;
 * if rejected, builders are checked in registration order. Re-registering a key replaces its
 * builder without moving its position in that fallback order.
 */
class MarkdownBuilderRegistry {
    private val revision = mutableStateOf(0L)
    /** Observable configuration version. Mutate on the UI thread or inside a mutable snapshot. */
    val version: Long get() = revision.value
    private fun changed() { revision.value = revision.value + 1 }
    private val builders = linkedMapOf<KClass<out Node>, MarkdownNodeBuilder>()

    fun register(nodeType: KClass<out Node>, builder: MarkdownNodeBuilder): MarkdownBuilderRegistry = apply {
        if (builders[nodeType] !== builder) {
            builders[nodeType] = builder
            changed()
        }
    }

    inline fun <reified T : Node> register(builder: MarkdownNodeBuilder): MarkdownBuilderRegistry =
        register(T::class, builder)

    fun getBuilder(nodeType: KClass<out Node>): MarkdownNodeBuilder? = builders.also { version }[nodeType]
    fun hasBuilder(nodeType: KClass<out Node>): Boolean = builders.also { version }.containsKey(nodeType)
    fun unregister(nodeType: KClass<out Node>): MarkdownNodeBuilder? = builders.remove(nodeType).also { if (it != null) changed() }
    fun clear() { if (builders.isNotEmpty()) { builders.clear(); changed() } }
    fun copy(): MarkdownBuilderRegistry = MarkdownBuilderRegistry().also { it.builders.putAll(builders) }

    fun findBuilder(node: Node): MarkdownNodeBuilder? {
        version
        builders[node::class]?.let { if (it.canBuild(node)) return it }
        return builders.values.firstOrNull { it.canBuild(node) }
    }
}
