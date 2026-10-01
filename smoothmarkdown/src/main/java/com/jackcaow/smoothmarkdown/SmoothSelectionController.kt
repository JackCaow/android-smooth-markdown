package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.ast.HtmlBlock
import com.jackcaow.smoothmarkdown.ast.HtmlInline
import com.jackcaow.smoothmarkdown.ast.Image
import com.jackcaow.smoothmarkdown.ast.FencedCodeBlock
import com.jackcaow.smoothmarkdown.ast.IndentedCodeBlock

/** Programmatic control of the reader's selectable text region. */
class SmoothSelectionController {
    private var region by mutableStateOf<ReaderSelectionState?>(null)
    private var anchors: NonTextAnchorRegistry? = null
    private val targets = mutableMapOf<Any, MarkdownSelectionTarget>()
    private var document: Node? = null
    private var documentHtml = false
    private var documentPlugins: ParserPluginRegistry? = null
    private var documentBuilders: MarkdownBuilderRegistry? = null
    private val detailsExpanded = mutableMapOf<DetailsNode, Boolean>()
    private var copyDocumentText: ((String) -> Unit)? = null
    private var fullDocumentProjection: ReaderDocumentText? = null
    private var fullDocumentNativeSnapshot: List<AnnotatedString>? = null
    private var hasCustomCodeBuilder = false
    private var hasCustomImageBuilder = false
    internal var fullDocumentSelectionMode by mutableStateOf(false)
        private set
    internal var fullDocumentSelectRequest by mutableIntStateOf(0)
        private set
    internal var fullDocumentLayoutReady by mutableIntStateOf(0)
        private set
    internal var fullDocumentSelectionEstablished by mutableStateOf(false)
        private set

    internal val selectionStateForTesting: ReaderSelectionState? get() = region

    /** The currently selected native text, omitting non-text selection anchors. */
    val selectedText: String
        get() = region?.let { visibleSelectedText(it.selectedTexts, anchors?.snapshot().orEmpty()).text }.orEmpty()

    /**
     * Full document semantic text for explicit copying, including offscreen blocks and built-in
     * image alt/math source but excluding collapsed details and rules. Null when a custom renderer
     * has no known text projection.
     * This does not represent a native selection or selection handles.
     */
    val documentText: String?
        get() = document?.let { readerDocumentText(
            it, documentHtml, documentPlugins, documentBuilders, detailsExpanded,
        ).takeIf { projection -> projection.complete }?.text }

    /** Copy the whole projected document. Returns false if unattached or projection is incomplete. */
    fun copyAllDocumentText(): Boolean {
        val text = documentText?.takeIf { it.isNotEmpty() } ?: return false
        val copy = copyDocumentText ?: return false
        copy(text)
        return true
    }

    /** Select all text currently registered with the Compose selection region (viewport scope). */
    fun selectAll() { region?.selectAll() }

    /**
     * Request native Select All handles over every parsed block, including blocks outside the
     * current viewport. Normal reading remains lazy; while this selection is active the Reader
     * mounts its bounded document in one scroll container. Returns false without changing the UI
     * when the Reader is detached, a custom visual renderer is present, the text projection is
     * incomplete, or the document exceeds the bounded full-selection budget. A true return means
     * the request was accepted; selection is applied after the full layout is positioned.
     */
    fun selectAllDocument(): Boolean {
        if (region == null) return false
        val projection = fullDocumentSelectionProjection() ?: return false
        region?.clear()
        fullDocumentProjection = projection
        fullDocumentNativeSnapshot = null
        fullDocumentSelectionEstablished = false
        fullDocumentSelectionMode = true
        fullDocumentSelectRequest++
        return true
    }

    internal fun fullDocumentSelectionProjection(): ReaderDocumentText? {
        val node = document ?: return null
        if (readerDocumentUsesCustomVisualBuilder(
                node, documentHtml, hasCustomCodeBuilder, hasCustomImageBuilder,
            )) return null
        if (readerDocumentHasOpaqueRenderer(node, documentHtml, documentPlugins, documentBuilders)) return null
        if (readerDocumentNodeCount(node) > MAX_FULL_SELECTION_RENDER_NODES) return null
        val projection = readerDocumentText(
            node, documentHtml, documentPlugins, documentBuilders, detailsExpanded,
        )
        if (!projection.complete || projection.text.isEmpty() ||
            projection.blocks.size > MAX_FULL_SELECTION_BLOCKS ||
            projection.text.length > MAX_FULL_SELECTION_UTF16) return null
        return projection
    }

    /** Select a range in the region's currently registered text. */
    fun select(range: TextRange) { region?.select(range) }

    /** Select the rendered word at a position in this Android window. */
    fun selectWordAt(windowPosition: Offset) { selectAt(windowPosition, ReaderSelectionGranularity.WORD) }

    /** Select the rendered paragraph at a position in this Android window. */
    fun selectParagraphAt(windowPosition: Offset) { selectAt(windowPosition, ReaderSelectionGranularity.PARAGRAPH) }

    /** Clear the current text selection. */
    fun clear() {
        region?.clear()
        exitFullDocumentSelection()
    }

    private fun selectAt(position: Offset, granularity: ReaderSelectionGranularity) {
        val state = region ?: return
        if (targets.values.none { it.boundsInWindow.contains(position) &&
                it.containsTextAtWindowPosition?.invoke(position) != false }) {
            state.clear()
            return
        }
        // Compose exposes registered text through selectedTexts after selectAll.
        // Narrow the selection synchronously, as SmoothSelectionRegion does in Flutter.
        state.selectAll()
        val range = readerSelectionRangeAt(position, targets.values, state.selectedTexts, granularity)
        if (range == null || range.collapsed) state.clear() else state.select(range)
    }

    internal fun track(target: MarkdownSelectionTarget) {
        targets[target.key] = target
        region?.track(target)
    }

    internal fun removeTarget(key: Any) {
        targets.remove(key)
        region?.remove(key)
    }

    internal fun bindDocument(
        node: Node,
        enableHtml: Boolean,
        plugins: ParserPluginRegistry?,
        builders: MarkdownBuilderRegistry?,
        customCodeBuilder: Boolean = false,
        customImageBuilder: Boolean = false,
    ) {
        if (document !== node) {
            detailsExpanded.clear()
            exitFullDocumentSelection()
            region?.clear()
        }
        document = node
        documentHtml = enableHtml
        documentPlugins = plugins
        documentBuilders = builders
        hasCustomCodeBuilder = customCodeBuilder
        hasCustomImageBuilder = customImageBuilder
    }

    internal fun unbindDocument(node: Node) {
        if (document === node) {
            document = null
            detailsExpanded.clear()
            exitFullDocumentSelection()
        }
    }

    internal fun setDetailsExpanded(node: DetailsNode, expanded: Boolean) {
        if (document != null) {
            val before = detailsExpanded[node] ?: node.isOpen
            detailsExpanded[node] = expanded
            if (before != expanded && fullDocumentSelectionMode) clear()
        }
    }

    internal fun detailsExpanded(node: DetailsNode): Boolean? = detailsExpanded[node]

    internal fun attach(state: ReaderSelectionState, anchorRegistry: NonTextAnchorRegistry, copyAll: (String) -> Unit) {
        region = state
        targets.values.forEach(state::track)
        anchors = anchorRegistry
        copyDocumentText = copyAll
    }

    internal fun detach(state: ReaderSelectionState) {
        if (region === state) {
            region = null
            anchors = null
            targets.clear()
            copyDocumentText = null
            exitFullDocumentSelection()
        }
    }

    internal fun onFullDocumentLaidOut() {
        if (fullDocumentSelectionMode) fullDocumentLayoutReady = fullDocumentSelectRequest
    }

    internal fun markFullDocumentSelected(request: Int, selectedTexts: List<AnnotatedString>) {
        if (fullDocumentSelectionMode && request == fullDocumentSelectRequest) {
            fullDocumentNativeSnapshot = selectedTexts.toList()
            fullDocumentSelectionEstablished = true
        }
    }

    /** Semantic copy is valid only while native handles still span the original full selection. */
    internal fun fullDocumentSemanticText(selectedTexts: List<AnnotatedString>): String? {
        if (!fullDocumentSelectionMode || !fullDocumentSelectionEstablished) return null
        val state = region ?: return null
        if (!state.selectsAllRegisteredText) return null
        val projection = fullDocumentProjection ?: return null
        val snapshot = fullDocumentNativeSnapshot ?: return null
        val registeredAnchors = anchors?.snapshot().orEmpty()
        // Image measurements can change the invisible anchor's row count after full layout.
        // Require the complete owned range and unchanged visible text, rather than treating
        // an anchor geometry update as if the user had moved a selection handle.
        return projection.text.takeIf {
            projection.complete && snapshot.isNotEmpty() &&
                visibleSelectedText(snapshot, registeredAnchors).text ==
                visibleSelectedText(selectedTexts, registeredAnchors).text
        }
    }

    internal fun exitFullDocumentSelection() {
        fullDocumentSelectionMode = false
        fullDocumentSelectionEstablished = false
        fullDocumentSelectRequest = 0
        fullDocumentLayoutReady = 0
        fullDocumentProjection = null
        fullDocumentNativeSnapshot = null
    }
}

internal fun exactWholeDocumentCopyText(
    snapshot: List<AnnotatedString>?,
    selectedTexts: List<AnnotatedString>,
    projection: ReaderDocumentText?,
): String? = projection?.text?.takeIf {
    projection.complete && snapshot != null && snapshot.isNotEmpty() && snapshot == selectedTexts
}

internal const val MAX_FULL_SELECTION_BLOCKS = 512
internal const val MAX_FULL_SELECTION_UTF16 = 100_000
internal const val MAX_FULL_SELECTION_RENDER_NODES = 2_048

/** A configured host builder only prevents native selection if this document actually uses it. */
internal fun readerDocumentUsesCustomVisualBuilder(
    document: Node,
    enableHtml: Boolean,
    customCodeBuilder: Boolean,
    customImageBuilder: Boolean,
): Boolean {
    if (!customCodeBuilder && !customImageBuilder) return false
    fun visit(node: Node, depth: Int): Boolean {
        if (depth > 64) return true
        if (customCodeBuilder && (node is FencedCodeBlock || node is IndentedCodeBlock)) return true
        if (customImageBuilder && (node is Image ||
                    (enableHtml && node is HtmlInline && SafeHtml.imageTag(node.literal) != null) ||
                    (enableHtml && node is HtmlBlock && SafeHtml.imageTag(node.literal) != null))) return true
        if (node is DetailsNode && (node.summary + node.body).any { visit(it, depth + 1) }) return true
        if (enableHtml && customImageBuilder && node is HtmlBlock) {
            val html = SafeHtml.parseBlock(node.literal)
            if (html is SafeHtml.Block.Container &&
                parseMarkdown(html.content + "\n" + html.trailing, enableHtml = true)
                    .children().any { visit(it, depth + 1) }) return true
        }
        return node.children().any { visit(it, depth + 1) }
    }
    return document.children().any { visit(it, 0) }
}

/**
 * Copy text supplied by a custom renderer does not prove its Compose output participates in the
 * outer SelectionContainer. Native full selection therefore rejects those renderer boundaries.
 */
internal fun readerDocumentHasOpaqueRenderer(
    document: Node,
    enableHtml: Boolean,
    plugins: ParserPluginRegistry?,
    builders: MarkdownBuilderRegistry?,
): Boolean {
    fun visit(node: Node, depth: Int): Boolean {
        if (depth > 64 || builders?.findBuilder(node) != null || node is PluginBlockNode) return true
        if (node is DetailsNode &&
            (node.summary + node.body).any { visit(it, depth + 1) }) return true
        if (enableHtml && node is HtmlBlock) {
            val html = SafeHtml.parseBlock(node.literal)
            if (html is SafeHtml.Block.Container) {
                val nested = parseMarkdown(html.content + "\n" + html.trailing, plugins, enableHtml = true)
                if (nested.children().any { visit(it, depth + 1) }) return true
            }
        }
        return node.children().any { visit(it, depth + 1) }
    }
    return document.children().any { visit(it, 0) }
}

/** Count visual AST work even when it projects to no text (for example, hundreds of images). */
internal fun readerDocumentNodeCount(document: Node): Int {
    var count = 0
    fun visit(node: Node) {
        if (++count > MAX_FULL_SELECTION_RENDER_NODES) return
        node.children().forEach { if (count <= MAX_FULL_SELECTION_RENDER_NODES) visit(it) }
        if (node is DetailsNode) {
            (node.summary + node.body).forEach {
                if (count <= MAX_FULL_SELECTION_RENDER_NODES) visit(it)
            }
        }
    }
    document.children().forEach { if (count <= MAX_FULL_SELECTION_RENDER_NODES) visit(it) }
    return count
}

internal enum class ReaderSelectionGranularity { WORD, PARAGRAPH }

/** Match a visual target to Compose's selectable-text order, including repeated paragraphs. */
internal fun readerSelectionRangeAt(
    position: Offset,
    targets: Collection<MarkdownSelectionTarget>,
    selectableTexts: List<AnnotatedString>,
    granularity: ReaderSelectionGranularity,
): TextRange? {
    val target = targets.asSequence()
        .filter { it.text.isNotEmpty() && it.boundsInWindow.contains(position) &&
            it.containsTextAtWindowPosition?.invoke(position) != false }
        .minByOrNull { it.boundsInWindow.width * it.boundsInWindow.height }
        ?: return null
    val matches = orderedReaderSelectionTargets(targets).filter { it.text.text == target.text.text }
    val ordinal = matches.indexOfFirst { it.key === target.key }
    if (ordinal < 0) return null
    val index = selectableTexts.withIndex().filter { it.value.text == target.text.text }
        .getOrNull(ordinal)?.index ?: return null
    val text = target.text.text
    val offset = target.offsetAtWindowPosition?.invoke(position)?.coerceIn(0, text.length) ?: return null
    val local = when (granularity) {
        ReaderSelectionGranularity.WORD -> target.wordBoundaryAtWindowPosition?.invoke(position)
            ?: fallbackWordBoundary(text, offset)
        ReaderSelectionGranularity.PARAGRAPH -> {
            val start = if (offset == 0) 0 else text.lastIndexOf('\n', offset - 1) + 1
            val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            TextRange(start, end)
        }
    }
    val base = selectableTexts.take(index).sumOf { it.length }
    return TextRange(base + local.start, base + local.end)
}

private fun fallbackWordBoundary(text: String, offset: Int): TextRange {
    if (text.isEmpty()) return TextRange(0)
    val index = offset.coerceAtMost(text.lastIndex)
    if (!text[index].isLetterOrDigit() && text[index] != '_') return TextRange(index)
    var start = index
    var end = index + 1
    while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) start--
    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
    return TextRange(start, end)
}
