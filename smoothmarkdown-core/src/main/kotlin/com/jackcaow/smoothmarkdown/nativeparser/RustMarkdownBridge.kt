package com.jackcaow.smoothmarkdown.nativeparser

/** Optional JNI transport. Android and desktop JVMs use the same UTF-16 batch ABI. */
internal object RustMarkdownBridge {
    private val successfulParses = java.util.concurrent.atomic.AtomicLong(0)
    val successfulParseCount: Long get() = successfulParses.get()
    val available: Boolean by lazy {
        try {
            val explicit = System.getProperty("smoothmarkdown.rust.library")
            if (explicit.isNullOrBlank()) System.loadLibrary("smooth_markdown_rust_jni") else System.load(explicit)
            abiVersionNative() == 1
        } catch (_: LinkageError) { false } catch (_: SecurityException) { false }
    }
    private external fun abiVersionNative(): Int
    private external fun parseNative(source: String, options: Int): ByteArray?

    /** Kotlin internal SPI for the companion renderer module; never an application backend option. */
    @JvmSynthetic
    fun parseForReader(source: String, enableGFM: Boolean): NativeMarkdownNode? {
        val native = parse(source, enableGFM, true) ?: return null
        if (!enableGFM) return native
        fun filter(node: NativeMarkdownNode): NativeMarkdownNode = node.copy(
            children = node.children.map(::filter),
            literalText = if (node.kind == NativeMarkdownNode.Kind.INLINE_HTML || node.kind == NativeMarkdownNode.Kind.HTML_BLOCK)
                NativeMarkdownHTMLTagFilter.filter(node.literalText ?: node.source) else node.literalText)
        return filter(native)
    }

    fun parse(source: String, enableGFM: Boolean, enableExtensions: Boolean): NativeMarkdownNode? {
        val required = System.getProperty("smoothmarkdown.rust.required") == "true"
        if (!available) {
            check(!required) { "Required Rust Markdown native library is unavailable or its ABI is incompatible" }
            return null
        }
        return try {
            val flags = (if (enableGFM) 1 else 0) or (if (enableExtensions) 2 else 0)
            val bytes = parseNative(source, flags)
            if (bytes == null) {
                check(!required) { "Required Rust Markdown FFI parse failed" }
                return null
            }
            val tree = RustMarkdownWire.decode(bytes, source)
            successfulParses.incrementAndGet()
            tree
        } catch (error: LinkageError) {
            if (required) throw IllegalStateException("Required Rust Markdown FFI is unavailable", error)
            null
        } catch (error: IllegalArgumentException) {
            if (required) throw IllegalStateException("Required Rust Markdown AST wire is invalid", error)
            null
        }
    }
}

/** Bounds checks protect the fallback boundary from incompatible or malformed native output. */
internal object RustMarkdownWire {
    fun decode(bytes: ByteArray, originalSource: String): NativeMarkdownNode {
        val cursor = Cursor(bytes, originalSource)
        require(bytes.size >= 8 && bytes[0] == 83.toByte() && bytes[1] == 77.toByte() && bytes[2] == 82.toByte() && bytes[3] == 49.toByte()) { "Unsupported Rust AST wire version" }
        cursor.offset = 4
        val nodes = cursor.count()
        require(nodes > 0 && nodes <= bytes.size / 32) { "Invalid AST node count" }
        cursor.remainingNodes = nodes
        val root = cursor.node(0)
        require(root.kind == NativeMarkdownNode.Kind.DOCUMENT && root.sourceRange == SourceRange(0, originalSource.length))
        require(cursor.remainingNodes == 0 && cursor.offset == bytes.size) { "Trailing or missing AST records" }
        return root
    }

    private class Cursor(val bytes: ByteArray, val originalSource: String) {
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
            val kind = NativeMarkdownNode.Kind.entries.getOrNull(count()) ?: throw IllegalArgumentException("Unknown AST kind")
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
            return NativeMarkdownNode(kind, source, SourceRange(start, length), decodedChildren,
                level, info, destination, title, flags and 1 != 0,
                if (flags and 4 != 0) flags and 2 != 0 else null,
                if (flags and 16 != 0) flags and 8 != 0 else null,
                if (flags and 32 != 0) listStart else null, literal, alignments, label)
        }
    }
}
