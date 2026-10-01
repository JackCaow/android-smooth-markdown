package com.jackcaow.smoothmarkdown.nativeparser

import com.jackcaow.smoothmarkdown.ast.*
import java.io.ByteArrayOutputStream

/** Optional JNI transport. Android and desktop JVMs use the same UTF-16 batch ABI. */
internal object RustMarkdownBridge {
    private val successfulParses = java.util.concurrent.atomic.AtomicLong(0)
    private val successfulHtmlRenders = java.util.concurrent.atomic.AtomicLong(0)
    val successfulParseCount: Long get() = successfulParses.get()
    val successfulHtmlRenderCount: Long get() = successfulHtmlRenders.get()
    val available: Boolean by lazy {
        try {
            val explicit = System.getProperty("smoothmarkdown.rust.library")
            if (explicit.isNullOrBlank()) System.loadLibrary("smooth_markdown_rust_jni") else System.load(explicit)
            abiVersionNative() == 1
        } catch (_: LinkageError) { false } catch (_: SecurityException) { false }
    }
    private external fun abiVersionNative(): Int
    private external fun parseNative(source: String, options: Int): ByteArray?
    private external fun parseWithHooksNative(source: String, options: Int, callbacks: Any?): ByteArray?
    private external fun parseInlineNative(source: String, options: Int, referenceTriples: Array<String?>?, callbacks: Any?): ByteArray?
    private external fun renderWireNative(ast: ByteArray, escapeHtml: Boolean): String?
    private external fun exportSourceNative(source: String, options: Int, escapeHtml: Boolean): String?
    private external fun streamNewNative(options: Int): Long
    private external fun streamUpdateNative(handle: Long, source: String): ByteArray?
    private external fun streamFreeNative(handle: Long)

    /** A stream is confined to its creator thread; it owns the native parser session. */
    internal class StreamSession private constructor(private var handle: Long) : AutoCloseable {
        private val owner = Thread.currentThread()
        private var previousBlockCount = 0
        data class Update(val retainedBlocks: Int, val replacement: NativeMarkdownNode)

        fun update(source: String): Update? {
            check(Thread.currentThread() === owner) { "Markdown stream sessions cannot cross threads" }
            check(handle != 0L) { "Markdown stream session is closed" }
            val bytes = streamUpdateNative(handle, source)
            if (bytes == null) {
                check(System.getProperty("smoothmarkdown.rust.required") != "true") { "Required Rust stream update failed" }
                return null
            }
            require(bytes.size >= 12) { "Invalid Rust stream response" }
            var retained = 0L
            repeat(4) { retained = retained or ((bytes[it].toLong() and 255) shl (it * 8)) }
            require(retained <= previousBlockCount) { "Invalid Rust stream retained block count" }
            val tree = filterHtml(RustMarkdownWire.decode(bytes, source, startOffset = 4), true)
            previousBlockCount = retained.toInt() + tree.children.size
            successfulParses.incrementAndGet()
            return Update(retained.toInt(), tree)
        }

        override fun close() {
            check(Thread.currentThread() === owner) { "Markdown stream sessions cannot cross threads" }
            if (handle != 0L) {
                streamFreeNative(handle)
                handle = 0L
            }
        }

        companion object {
            fun create(): StreamSession? {
                if (!requireAvailable()) return null
                return try {
                    val handle = streamNewNative(flags(true, true))
                    if (handle == 0L) {
                        check(System.getProperty("smoothmarkdown.rust.required") != "true") { "Required Rust stream session creation failed" }
                        null
                    } else StreamSession(handle)
                } catch (error: LinkageError) {
                    if (System.getProperty("smoothmarkdown.rust.required") == "true")
                        throw IllegalStateException("Required Rust stream ABI is unavailable", error)
                    null
                }
            }
        }
    }

    /** Parse-local host nodes never cross the C ABI; Rust records only their opaque IDs. */
    internal class Hooks(
        private val inlineHook: ((String, Int, Int) -> NativeCustomInlineMatch?)?,
        private val blockHook: ((List<String>, Int, Int) -> NativeCustomBlockMatch?)?,
        private val baseOffset: Int = 0,
    ) {
        val nodes = mutableMapOf<Int, NativeMarkdownNode>()
        private var nextId = 1
        // JNI reuses an immutable projected line array for each Rust scan context.
        // Preserve its List view too, rather than copying the whole document per candidate.
        private val lineContexts = java.util.IdentityHashMap<Array<String>, List<String>>()
        var failure: Throwable? = null
            private set
        // Names and descriptors are consumed by the handwritten JNI shim.
        fun inlineMatch(source: String, index: Int, offset: Int): LongArray? = callback {
            if (index !in source.indices) return@callback null
            val match = inlineHook?.invoke(source, index, offset + baseOffset) ?: return@callback null
            if (match.consumed !in 1..source.length - index) return@callback null
            remember(match.consumed, match.node)
        }
        fun blockMatch(lines: Array<String>, index: Int, offset: Int): LongArray? = callback {
            if (index !in lines.indices) return@callback null
            val hook = blockHook ?: return@callback null
            val context = lineContexts.getOrPut(lines) { lines.toList() }
            val match = hook(context, index, offset + baseOffset) ?: return@callback null
            if (match.linesConsumed !in 1..lines.size - index) return@callback null
            remember(match.linesConsumed, match.node)
        }
        private fun remember(consumed: Int, node: NativeMarkdownNode): LongArray {
            check(nextId < Int.MAX_VALUE) { "Too many custom parser matches" }
            val id = nextId++
            fun local(node: NativeMarkdownNode): NativeMarkdownNode = node.copy(
                sourceRange = SourceRange(node.sourceRange.offset - baseOffset, node.sourceRange.length),
                children = node.children.map(::local))
            nodes[id] = if (baseOffset == 0) node else local(node)
            return longArrayOf(consumed.toLong(), id.toLong())
        }
        private inline fun callback(operation: () -> LongArray?): LongArray? = try { operation() }
        catch (error: Throwable) { failure = error; throw error }
        fun throwFailure() { failure?.let { throw it } }
    }

    /** Internal friend-module SPI; host callbacks execute in the Rust scan, not a whole-document fallback. */
    @JvmSynthetic
    fun parseForReader(source: String, enableGFM: Boolean,
                       customInline: ((String, Int, Int) -> NativeCustomInlineMatch?)? = null,
                       customBlock: ((List<String>, Int, Int) -> NativeCustomBlockMatch?)? = null,
                       enableExtensions: Boolean = true): NativeMarkdownNode? =
        parse(source, enableGFM, enableExtensions, customInline, customBlock)?.let { native -> filterHtml(native, enableGFM) }

    fun parse(source: String, enableGFM: Boolean, enableExtensions: Boolean,
              customInline: ((String, Int, Int) -> NativeCustomInlineMatch?)? = null,
              customBlock: ((List<String>, Int, Int) -> NativeCustomBlockMatch?)? = null): NativeMarkdownNode? {
        val hooks = if (customInline != null || customBlock != null) Hooks(customInline, customBlock) else null
        return nativeTree(source, hooks) {
            val flags = flags(enableGFM, enableExtensions)
            if (hooks == null) parseNative(source, flags) else parseWithHooksNative(source, flags, hooks)
        }
    }

    fun parseInline(source: String, offset: Int = 0, references: Map<String, NativeMarkdownReference> = emptyMap(),
                    enableGFM: Boolean = true, enableExtensions: Boolean = false,
                    customInline: ((String, Int, Int) -> NativeCustomInlineMatch?)? = null): NativeMarkdownNode? {
        val hooks = customInline?.let { Hooks(it, null, offset) }
        val document = nativeTree(source, hooks) {
            parseInlineNative(source, flags(enableGFM, enableExtensions), references.flatMap { (label, reference) ->
                listOf(NativeMarkdownInlineParser.normalizeReference(label), reference.destination, reference.title)
            }.toTypedArray(), hooks)
        } ?: return null
        fun translated(node: NativeMarkdownNode): NativeMarkdownNode = node.copy(
            sourceRange = SourceRange(node.sourceRange.offset + offset, node.sourceRange.length),
            children = node.children.map(::translated))
        return if (offset == 0) document else translated(document)
    }

    @JvmSynthetic
    fun parseForEditor(source: String): NativeMarkdownNode? = parseForReader(source, true)
    @JvmSynthetic
    fun parseInlineForEditor(source: String, enableGFM: Boolean = true): NativeMarkdownNode? =
        parseInline(source, enableGFM = enableGFM, enableExtensions = false)

    fun renderHtml(root: Markup, escapeHtml: Boolean): String? = nativeHtml {
        renderWireNative(RustMarkdownWire.encode(root), escapeHtml)
    }
    fun exportHtml(source: String, enableGFM: Boolean, escapeHtml: Boolean = false): String? = nativeHtml {
        exportSourceNative(source, flags(enableGFM, false), escapeHtml)
    }
    private fun flags(gfm: Boolean, extensions: Boolean) = (if (gfm) 1 else 0) or (if (extensions) 2 else 0)
    private fun requireAvailable(): Boolean {
        if (available) return true
        check(System.getProperty("smoothmarkdown.rust.required") != "true") { "Required Rust Markdown native library is unavailable or its ABI is incompatible" }
        return false
    }
    private inline fun nativeTree(source: String, hooks: Hooks?, operation: () -> ByteArray?): NativeMarkdownNode? {
        if (!requireAvailable()) return null
        val required = System.getProperty("smoothmarkdown.rust.required") == "true"
        return try {
            val bytes = operation()
            hooks?.throwFailure()
            if (bytes == null) {
                check(!required) { "Required Rust Markdown FFI parse failed" }
                return null
            }
            val tree = RustMarkdownWire.decode(bytes, source, hooks?.nodes.orEmpty())
            successfulParses.incrementAndGet()
            tree
        } catch (error: LinkageError) {
            hooks?.throwFailure()
            if (required) throw IllegalStateException("Required Rust Markdown FFI is unavailable", error)
            null
        } catch (error: IllegalArgumentException) {
            hooks?.throwFailure()
            if (required) throw IllegalStateException("Required Rust Markdown AST wire is invalid", error)
            null
        }
    }
    private inline fun nativeHtml(operation: () -> String?): String? {
        if (!requireAvailable()) return null
        val required = System.getProperty("smoothmarkdown.rust.required") == "true"
        return try {
            val html = operation()
            if (html == null) {
                check(!required) { "Required Rust Markdown HTML export failed" }
                return null
            }
            successfulHtmlRenders.incrementAndGet()
            html
        } catch (error: LinkageError) {
            if (required) throw IllegalStateException("Required Rust Markdown HTML FFI is unavailable", error)
            null
        }
    }
    private fun filterHtml(node: NativeMarkdownNode, gfm: Boolean): NativeMarkdownNode = if (!gfm) node else node.copy(
        children = node.children.map { filterHtml(it, true) },
        literalText = if (node.kind == NativeMarkdownNode.Kind.INLINE_HTML || node.kind == NativeMarkdownNode.Kind.HTML_BLOCK)
            NativeMarkdownHTMLTagFilter.filter(node.literalText ?: node.source) else node.literalText)
}

/** Bounds checks protect the fallback boundary from incompatible or malformed native output. */
internal object RustMarkdownWire {
    fun decode(bytes: ByteArray, originalSource: String, customNodes: Map<Int, NativeMarkdownNode> = emptyMap(),
               startOffset: Int = 0): NativeMarkdownNode {
        val cursor = Cursor(bytes, originalSource, customNodes)
        require(startOffset in 0..bytes.size && bytes.size - startOffset >= 8 &&
            bytes[startOffset] == 83.toByte() && bytes[startOffset + 1] == 77.toByte() &&
            bytes[startOffset + 2] == 82.toByte() && bytes[startOffset + 3] == 49.toByte()) { "Unsupported Rust AST wire version" }
        cursor.offset = startOffset + 4
        val nodes = cursor.count()
        require(nodes > 0 && nodes <= (bytes.size - startOffset) / 32) { "Invalid AST node count" }
        cursor.remainingNodes = nodes
        val root = cursor.node(0)
        require(root.kind == NativeMarkdownNode.Kind.DOCUMENT && root.sourceRange == SourceRange(0, originalSource.length))
        require(cursor.remainingNodes == 0 && cursor.offset == bytes.size) { "Trailing or missing AST records" }
        return root
    }

    /** Serialize values of the actual mutable host AST. Source positions are irrelevant to HTML. */
    fun encode(root: Markup): ByteArray {
        data class Record(val node: Markup, val depth: Int)
        val records = mutableListOf<Record>()
        val pending = java.util.ArrayDeque<Record>()
        pending.addLast(Record(root, 0))
        while (pending.isNotEmpty()) {
            val item = pending.removeLast()
            require(item.depth <= 256) { "HTML AST nesting exceeds the native wire limit" }
            records += item
            val children = if (item.node is ListItem) item.node.children().filter { it !is TaskListItemMarker }.toList()
                else item.node.children().toList()
            children.asReversed().forEach { pending.addLast(Record(it, item.depth + 1)) }
            require(records.size + pending.size <= 1_000_000) { "Too many HTML AST nodes" }
        }
        val writer = Writer()
        writer.magic("SMR1"); writer.word(records.size)
        records.forEach { (node, _) ->
            val kind = when (node) {
                is Document -> 0; is Paragraph -> 1; is Heading -> 2; is FencedCodeBlock -> 3; is IndentedCodeBlock -> 4
                is TableBlock -> 5; is TableRow -> 6; is TableCell -> 7; is ListBlock -> 8; is ListItem -> 9
                is BlockQuote -> 10; is ThematicBreak -> 11; is Text -> 12; is StrongEmphasis -> 13; is Emphasis -> 14
                is Strikethrough -> 15; is Code -> 16; is SoftLineBreak -> 18; is HardLineBreak -> 19
                is HtmlInline -> 20; is LinkReferenceDefinition -> 24; is Link -> 25; is Image -> 26; is HtmlBlock -> 27
                is TableHead -> 30; is TableBody -> 31; is TaskListItemMarker -> 32
                else -> 0 // Existing public exporter treats unknown custom nodes as transparent containers.
            }
            val tight = when (node) {
                is ListBlock -> node.isTight
                is ListItem -> (node.parent as? ListBlock)?.isTight
                is Paragraph -> ((node.parent as? ListItem)?.parent as? ListBlock)?.isTight
                else -> null
            }
            val checked = when (node) {
                is ListItem -> node.children().filterIsInstance<TaskListItemMarker>().firstOrNull()?.isChecked
                is TaskListItemMarker -> node.isChecked
                else -> null
            }
            var flags = if (node is OrderedList) 1 or 32 else 0
            if (checked != null) flags = flags or 4 or if (checked) 2 else 0
            if (tight != null) flags = flags or 16 or if (tight) 8 else 0
            if (node is TableCell && (node.isHeader || node.parent?.parent is TableHead)) flags = flags or 64
            // Mutable host API fields are signed Int values; grammar metadata remains u32.
            if (node is Heading || node is OrderedList) flags = flags or 128
            val children = if (node is ListItem) node.children().filter { it !is TaskListItemMarker }.toList() else node.children().toList()
            writer.word(kind); writer.word(0); writer.word(0)
            writer.word((node as? Heading)?.level ?: 0); writer.word(flags)
            writer.word((node as? OrderedList)?.startNumber ?: 0); writer.word(children.size)
            writer.string("") // Every source is explicit; the HTML ABI has no source-document sentinel.
            writer.string((node as? FencedCodeBlock)?.info.orEmpty())
            writer.string(when (node) { is Link -> node.destination; is Image -> node.destination; is LinkReferenceDefinition -> node.destination; else -> "" })
            writer.string(when (node) { is Link -> node.title; is Image -> node.title; is LinkReferenceDefinition -> node.title; else -> null })
            writer.string(when (node) { is Text -> node.literal; is Code -> node.literal; is FencedCodeBlock -> node.literal;
                is IndentedCodeBlock -> node.literal; is HtmlInline -> node.literal; is HtmlBlock -> node.literal; else -> null })
            writer.string(when (node) {
                is Heading -> node.level.toString()
                is OrderedList -> node.startNumber.toString()
                is LinkReferenceDefinition -> node.label
                else -> ""
            })
            if (node is TableCell) { writer.word(1); writer.string(node.alignment?.name?.lowercase()) } else writer.word(0)
        }
        return writer.bytes()
    }

    private class Writer {
        private val output = ByteArrayOutputStream()
        private fun reserve(count: Int) {
            require(count >= 0 && count <= 64 * 1024 * 1024 - output.size()) { "Native AST wire exceeds 64MiB" }
        }
        fun magic(value: String) { reserve(4); value.forEach { output.write(it.code) } }
        fun word(value: Int) { reserve(4); repeat(4) { output.write(value ushr (it * 8) and 255) } }
        fun string(value: String?) {
            if (value == null) { word(-1); return }
            require(value.length <= 32 * 1024 * 1024) { "Native AST string is too large" }
            reserve(4 + value.length * 2); word(value.length)
            value.forEach { output.write(it.code and 255); output.write(it.code ushr 8) }
        }
        fun bytes(): ByteArray = output.toByteArray()
    }

    private class Cursor(val bytes: ByteArray, val originalSource: String, val customNodes: Map<Int, NativeMarkdownNode>) {
        val sourceLength = originalSource.length
        var offset = 0
        var remainingNodes = 0
        fun word(): Long {
            require(offset <= bytes.size - 4) { "Truncated AST record" }
            var value = 0L
            repeat(4) { value = value or ((bytes[offset++].toLong() and 255) shl (it * 8)) }
            return value
        }
        fun count(): Int = word().also { require(it <= Int.MAX_VALUE) { "Oversized AST field" } }.toInt()
        fun string(units: Long = word()): String? {
            if (units == 0xffffffffL) return null
            require(units <= (bytes.size - offset) / 2) { "Truncated UTF-16 string" }
            val chars = CharArray(units.toInt()) {
                val low = bytes[offset++].toInt() and 255
                val high = bytes[offset++].toInt() and 255
                (low or (high shl 8)).toChar()
            }
            return String(chars)
        }
        fun node(depth: Int): NativeMarkdownNode {
            require(depth <= 256 && remainingNodes-- > 0) { "Invalid AST depth/count" }
            val kindId = count()
            val kind = if (kindId == 29) NativeMarkdownNode.Kind.RAW else NativeMarkdownNode.Kind.entries.getOrNull(kindId) ?: throw IllegalArgumentException("Unknown AST kind")
            val start = count(); val length = count()
            require(start <= sourceLength && length <= sourceLength - start) { "Invalid UTF-16 source span" }
            val level = count(); val flags = count(); val listStart = count(); val children = count()
            require(children <= remainingNodes) { "Invalid AST child count" }
            val sourceUnits = word()
            val source = if (sourceUnits == 0xfffffffeL) originalSource.substring(start, start + length) else requireNotNull(string(sourceUnits))
            val info = requireNotNull(string()); val destination = requireNotNull(string())
            val title = string(); val literal = string(); val label = requireNotNull(string())
            val alignmentCount = count()
            require(alignmentCount <= (bytes.size - offset) / 4) { "Invalid alignment count" }
            val alignments = List(alignmentCount) { string() }
            var decodedChildren = List(children) { node(depth + 1) }
            if (kind == NativeMarkdownNode.Kind.LIST && flags and 16 != 0 && flags and 8 != 0) {
                decodedChildren = decodedChildren.map { item ->
                    if (item.kind != NativeMarkdownNode.Kind.LIST_ITEM) item else item.copy(
                        children = item.children.flatMap { if (it.kind == NativeMarkdownNode.Kind.PARAGRAPH) it.children else listOf(it) })
                }
            }
            if (kindId == 29) {
                val token = label.toIntOrNull() ?: throw IllegalArgumentException("Invalid custom parser token")
                val custom = customNodes[token] ?: throw IllegalArgumentException("Unknown custom parser token")
                // Match metadata/payload belongs to the host; ranges belong to Rust's source map.
                return custom.copy(source = source, sourceRange = SourceRange(start, length),
                    literalText = custom.literalText ?: custom.semanticText)
            }
            return NativeMarkdownNode(kind, source, SourceRange(start, length), decodedChildren,
                level, info, destination, title, flags and 1 != 0,
                if (flags and 4 != 0) flags and 2 != 0 else null,
                if (flags and 16 != 0) flags and 8 != 0 else null,
                if (flags and 32 != 0) listStart else null, literal, alignments, label)
        }
    }
}
