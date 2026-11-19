package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge

/**
 * Code tokens from the shared Rust core (smooth-rust-core `smr_highlight_utf16`).
 * Offsets are Kotlin UTF-16 indices; [kind] is the core `Kind` value.
 */
internal data class CodeToken(val start: Int, val end: Int, val kind: Int)

/** Token kinds of the shared core that this module styles by name. */
internal object CodeKind {
    const val ATTRIBUTE = 1; const val BOOLEAN = 2; const val COMMENT = 3; const val COMMENT_DOC = 4
    const val CONSTANT = 5; const val CONSTANT_BUILTIN = 6; const val CONSTRUCTOR = 7
    const val DIFF_MINUS = 8; const val DIFF_PLUS = 9; const val EMPHASIS = 11; const val EMPHASIS_STRONG = 12
    const val FUNCTION = 13; const val FUNCTION_DEFINITION = 14; const val FUNCTION_METHOD = 15
    const val KEYWORD = 16; const val KEYWORD_CONTROL = 17; const val KEYWORD_DECLARATION = 18
    const val KEYWORD_IMPORT = 19; const val KEYWORD_OPERATOR = 20; const val LABEL = 21; const val NAMESPACE = 22
    const val NUMBER = 23; const val OPERATOR = 24; const val PREPROC = 25; const val PROPERTY = 26; const val PROPERTY_KEY = 27
    const val PUNCTUATION = 28; const val PUNCTUATION_BRACKET = 29; const val PUNCTUATION_DELIMITER = 30
    const val PUNCTUATION_LIST_MARKER = 31; const val PUNCTUATION_SPECIAL = 32
    const val SELECTOR = 33; const val SELECTOR_CLASS = 34; const val SELECTOR_ID = 35; const val SELECTOR_PSEUDO = 36
    const val STRING = 37; const val STRING_ESCAPE = 38; const val STRING_REGEX = 39; const val STRING_SPECIAL = 40
    const val TAG = 41; const val TAG_DOCTYPE = 42; const val TAG_COMPONENT = 43; const val TEXT_LITERAL = 44
    const val TITLE = 45; const val TYPE = 46; const val TYPE_BUILTIN = 47; const val VARIABLE = 48
    const val VARIABLE_PARAMETER = 49; const val VARIABLE_SPECIAL = 50; const val LINK_TEXT = 51; const val LINK_URI = 52
}

/** Maps a fence tag, file extension or file name (`kt`, `tsx`, `Dockerfile`, …) to the highlighter's language name. */
fun normalizedCodeLanguage(language: String?): String? {
    val tag = language?.trim()?.split(Regex("[\\s,]+"))?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    return RustMarkdownBridge.highlightLanguage(tag)?.takeIf { it.isNotEmpty() }
}

/** Tokens for [code]; empty for unsupported languages or when the native core is unavailable. */
internal fun codeTokens(code: String, language: String?): List<CodeToken> {
    val tag = language?.trim()?.split(Regex("[\\s,]+"))?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val flat = RustMarkdownBridge.highlight(code, tag) ?: return emptyList()
    return (0 until flat.size / 3).mapNotNull { i ->
        val start = flat[i * 3]
        val end = start + flat[i * 3 + 1]
        if (start < 0 || end > code.length || end <= start) null else CodeToken(start, end, flat[i * 3 + 2])
    }
}
