package com.jackcaow.smoothmarkdown.nativeparser

/** Original, source-preserving Markdown AST. Offsets are UTF-16 String indices. */
data class SourceRange(val offset: Int, val length: Int) { val end: Int get() = offset + length }
data class NativeMarkdownReference(val destination: String, val title: String? = null)
data class NativeMarkdownNode(
    val kind: Kind,
    val source: String,
    val sourceRange: SourceRange,
    val children: List<NativeMarkdownNode> = emptyList(),
    val level: Int = 0,
    val info: String = "",
    val destination: String = "",
    val title: String? = null,
    val ordered: Boolean = false,
    val checked: Boolean? = null,
    val isTight: Boolean? = null,
    val listStart: Int? = null,
    val literalText: String? = null,
    val tableAlignments: List<String?> = emptyList(),
    val label: String = "",
    val payload: Any? = null
) {
    enum class Kind {
        DOCUMENT, PARAGRAPH, HEADING, FENCED_CODE, INDENTED_CODE, TABLE, TABLE_ROW, TABLE_CELL,
        LIST, LIST_ITEM, BLOCK_QUOTE, THEMATIC_BREAK, TEXT, STRONG, EMPHASIS, STRIKETHROUGH,
        INLINE_CODE, INLINE_MATH, SOFT_BREAK, HARD_BREAK, INLINE_HTML, BLOCK_MATH,
        FOOTNOTE_REFERENCE, FOOTNOTE_DEFINITION, REFERENCE_DEFINITION, LINK, IMAGE, HTML_BLOCK, RAW
    }
    val semanticText: String? get() = when (kind) {
        Kind.TEXT -> literalText ?: NativeMarkdownTextDecoder.decode(source)
        Kind.INLINE_CODE -> literalText ?: NativeMarkdownTextDecoder.codeSpan(source)
        Kind.FENCED_CODE -> literalText ?: NativeMarkdownCodeSemantics.text(source, true)
        Kind.INDENTED_CODE -> literalText ?: NativeMarkdownCodeSemantics.text(source, false)
        Kind.INLINE_HTML, Kind.HTML_BLOCK -> literalText
        else -> null
    }
}
