package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Markup
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownASTParser
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge

/** Pure JVM CommonMark/GFM parser. No Android or Compose runtime is required. */
class MarkdownCoreParser(private val enableGFM: Boolean = true) {
    /** Source ranges and math literals for library-backed selection projections. */
    fun parseAST(source: String, enableExtensions: Boolean = false): com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode =
        NativeMarkdownASTParser(enableGFM = enableGFM, enableNativeExtensions = enableExtensions).parse(source)
    fun parse(source: String): Markup = NativeMarkdownMarkupConverter(source).convert(parseAST(source))
    fun renderHtml(source: String): String = RustMarkdownBridge.exportHtml(source, enableGFM)
        ?: NativeMarkdownHTMLSerializer().render(parse(source))
}
