package com.jackcaow.smoothmarkdown.nativeparser

object NativeMarkdownInlineHTML {
    private const val whitespace = "[ \\t\\r\\n]"
    private const val name = "[A-Za-z_:][A-Za-z0-9_.:-]*"
    private const val value = "(?:\"[^\"]*\"|'[^']*'|[^ \\t\\r\\n\"'=<>`]+)"
    private val attribute = "$whitespace+$name(?:$whitespace*=$whitespace*$value)?"
    private val expression = Regex("^(?:<[A-Za-z][A-Za-z0-9-]*(?:$attribute)*$whitespace*/?>|</[A-Za-z][A-Za-z0-9-]*$whitespace*>|<!--(?:>|->|[\\s\\S]*?-->)|<\\?[\\s\\S]*?\\?>|<![A-Z]+$whitespace+[^>]*>|<!\\[CDATA\\[[\\s\\S]*?\\]\\]>)")
    fun length(source: String, at: Int): Int? = if (at in source.indices && source[at] == '<') expression.find(source.substring(at))?.value?.length else null
}
