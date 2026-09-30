package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Markup
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownASTParser

/** Pure JVM CommonMark/GFM parser. No Android or Compose runtime is required. */
class MarkdownCoreParser(private val enableGFM: Boolean = true) {
    fun parse(source: String): Markup = NativeMarkdownMarkupConverter(source)
        .convert(NativeMarkdownASTParser(enableGFM = enableGFM, enableNativeExtensions = false).parse(source))
    fun renderHtml(source: String): String = NativeMarkdownHTMLSerializer().render(parse(source))
}
