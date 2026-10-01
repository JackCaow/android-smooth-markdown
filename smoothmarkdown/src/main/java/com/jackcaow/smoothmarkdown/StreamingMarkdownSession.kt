package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.compositionLocalOf
import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge

/** A private render handoff keeps the public reader signature and avoids parsing a prefix twice. */
internal data class StreamingMarkdownDocument(val source: String, val document: Node,
    val plugins: ParserPluginRegistry?, val enableHtml: Boolean, val blockKeys: Map<Node, Long>,
    val parserThreadId: Long? = null)
internal val LocalStreamingMarkdownDocument = compositionLocalOf<StreamingMarkdownDocument?> { null }

/** One synchronous session per stream/configuration. Published blocks retain their identity.
 * Arbitrary host plugins and HTML postprocessors keep the established whole-document path.
 */
internal class StreamingMarkdownSession(
    private val plugins: ParserPluginRegistry? = null,
    private val enableHtml: Boolean = false,
) : AutoCloseable {
    private var native: RustMarkdownBridge.StreamSession? = null
    private var previous: StreamingMarkdownDocument? = null
    private val converter = NativeMarkdownParser(enableGFM = true, enableExtensions = true)
    private var nativeAttempted = false
    private var nextBlockKey = 1L
    private var pluginVersion = plugins?.version
    private val owner = Thread.currentThread()
    var retainedBlockCount: Int = 0
        private set

    fun parse(source: String): StreamingMarkdownDocument {
        check(Thread.currentThread() === owner) { "Markdown stream sessions cannot cross threads" }
        if (pluginVersion != plugins?.version) {
            closeNative()
            previous = null
            pluginVersion = plugins?.version
        }
        // Opaque callbacks may depend on host state outside the observable registry.
        if (plugins == null) previous?.takeIf { it.source == source }?.let { return it }
        // Details recursively invoke the host reader and construct its custom UI node.
        // Reader extension hooks accept some math spellings beyond the built-in scanner
        // and include incomplete footnote continuation whitespace in public spans.
        // Preserve those published contracts with the existing hook-aware batch scanner.
        val eligible = streamingNativeEligible(source, plugins, enableHtml)
        if (!eligible) {
            closeNative()
            return publish(source, parseMarkdown(source, plugins, enableCache = false, enableHtml = enableHtml), 0)
        }
        if (!nativeAttempted) {
            nativeAttempted = true
            native = RustMarkdownBridge.StreamSession.create()
        }
        // A failed native decode/adaptation must not keep a session whose retained prefix
        // refers to a document the host never published.
        try {
            return parseNative(source)
        } catch (error: Throwable) {
            closeNative()
            throw error
        }
    }

    private fun parseNative(source: String): StreamingMarkdownDocument {
        val update = native?.update(source)
        if (update == null) {
            closeNative()
            return publish(source, parseMarkdown(source, enableCache = false), 0)
        }
        val oldBlocks = previous?.document?.children()?.toList().orEmpty()
        check(update.retainedBlocks <= oldBlocks.size) { "Rust stream retained unavailable host blocks" }
        val document = converter.convertTree(source, update.replacement)
        // Nodes move only on the UI/creator thread, before the new document is published.
        // No background worker mutates an AST which Compose may still be reading.
        oldBlocks.take(update.retainedBlocks).asReversed().forEach(document::prependChild)
        FootnoteReferencePostProcessor(source).process(document)
        return publish(source, document, update.retainedBlocks)
    }

    private fun publish(source: String, document: Node, retained: Int): StreamingMarkdownDocument {
        retainedBlockCount = retained
        val keys = java.util.IdentityHashMap<Node, Long>()
        document.children().forEach { block -> keys[block] = previous?.blockKeys?.get(block) ?: nextBlockKey++ }
        return StreamingMarkdownDocument(source, document, plugins, enableHtml, keys).also { previous = it }
    }
    private fun closeNative() { native?.close(); native = null; nativeAttempted = false }
    override fun close() {
        check(Thread.currentThread() === owner) { "Markdown stream sessions cannot cross threads" }
        closeNative(); previous = null
    }
}
