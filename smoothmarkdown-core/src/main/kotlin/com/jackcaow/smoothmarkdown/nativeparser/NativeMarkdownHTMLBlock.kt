package com.jackcaow.smoothmarkdown.nativeparser

object NativeMarkdownHTMLBlock {
    data class End(val pattern: Regex? = null) { fun matches(line: String) = pattern?.containsMatchIn(line) ?: line.all { it == ' ' || it == '\t' } }
    fun end(line: String, interruptingParagraph: Boolean = false): End? {
        val body = line.dropWhile { it == ' ' }
        if (line.length - body.length > 3 || !body.startsWith('<')) return null
        fun marker(pattern: String, insensitive: Boolean = false) = End(Regex(pattern, if (insensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()))
        if (Regex("^<(?:script|pre|style|textarea)(?:[ \\t>]|$)", RegexOption.IGNORE_CASE).containsMatchIn(body)) return marker("</(?:script|pre|style|textarea)>", true)
        if (body.startsWith("<!--")) return marker("-->")
        if (body.startsWith("<?")) return marker("\\?>")
        if (Regex("^<![A-Z]").containsMatchIn(body)) return marker(">")
        if (body.startsWith("<![CDATA[")) return marker("\\]\\]>")
        val tags = "address|article|aside|base|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog|dir|div|dl|dt|fieldset|figcaption|figure|footer|form|frame|frameset|h[1-6]|head|header|hr|html|iframe|legend|li|link|main|menu|menuitem|nav|noframes|ol|optgroup|option|p|param|search|section|summary|table|tbody|td|tfoot|th|thead|title|tr|track|ul"
        if (Regex("^</?(?:$tags)(?:[ \\t]|/?>|$)", RegexOption.IGNORE_CASE).containsMatchIn(body)) return End()
        if (interruptingParagraph || !Regex("^</?[A-Za-z]").containsMatchIn(body)) return null
        val length = NativeMarkdownInlineHTML.length(body, 0) ?: return null
        return if (body.drop(length).all { it == ' ' || it == '\t' }) End() else null
    }
}
