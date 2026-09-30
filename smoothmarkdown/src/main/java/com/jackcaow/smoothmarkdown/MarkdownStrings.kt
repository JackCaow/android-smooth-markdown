package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.staticCompositionLocalOf

/** All library-owned reader and plugin labels; content supplied by authors is left unchanged. */
data class MarkdownStrings(
    val copy: String = "Copy",
    val copied: String = "Copied!",
    val copyArtifact: String = "Copy artifact",
    val copyFormula: String = "Copy formula",
    val copyMarkdown: String = "Copy Markdown",
    val copyTextRange: String = "Copy text range",
    val copyCells: String = "Copy cells",
    val copyItems: String = "Copy items",
    val copyTSV: String = "Copy TSV",
    val expanded: String = "Expanded",
    val collapsed: String = "Collapsed",
    val expandDetails: String = "Expand details",
    val collapseDetails: String = "Collapse details",
    val expandThinking: String = "Expand thinking",
    val collapseThinking: String = "Collapse thinking",
    val thinking: String = "Thinking",
    val thinkingInProgress: String = "Thinking...",
    val selectSurroundingContent: String = "Select surrounding content",
    val tool: String = "Tool",
    val identifier: String = "ID",
    val running: String = "Running",
    val completed: String = "Completed",
    val failed: String = "Failed",
    val cancelled: String = "Cancelled",
    val pending: String = "Pending",
    val parameters: String = "Parameters",
    val result: String = "Result",
    val error: String = "Error",
    val code: String = "CODE",
    val document: String = "DOCUMENT",
    val component: String = "COMPONENT",
    val diagram: String = "DIAGRAM",
    val artifact: String = "ARTIFACT",
    val overrides: Map<String, String> = emptyMap(),
) {
    operator fun get(defaultLabel: String): String = overrides[defaultLabel] ?: when (defaultLabel) {
        "Copy" -> copy
        "Copied!" -> copied
        "Copy artifact" -> copyArtifact
        "Copy formula" -> copyFormula
        "Copy Markdown" -> copyMarkdown
        "Copy text range" -> copyTextRange
        "Copy cells" -> copyCells
        "Copy items" -> copyItems
        "Copy TSV" -> copyTSV
        "Expanded" -> expanded
        "Collapsed" -> collapsed
        "Expand details" -> expandDetails
        "Collapse details" -> collapseDetails
        "Expand thinking" -> expandThinking
        "Collapse thinking" -> collapseThinking
        "Thinking" -> thinking
        "Thinking..." -> thinkingInProgress
        "Select surrounding content" -> selectSurroundingContent
        "Tool" -> tool
        "ID" -> identifier
        "Running" -> running
        "Completed" -> completed
        "Failed" -> failed
        "Cancelled" -> cancelled
        "Pending" -> pending
        "Parameters" -> parameters
        "Result" -> result
        "Error" -> error
        "CODE" -> code
        "DOCUMENT" -> document
        "COMPONENT" -> component
        "DIAGRAM" -> diagram
        "ARTIFACT" -> artifact
        else -> defaultLabel
    }
    /** Replacements are literal author data and are not recursively interpreted as placeholders. */
    fun format(template: String, values: Map<String, String>): String =
        Regex("\\{([A-Za-z_][A-Za-z0-9_]*)\\}").replace(this[template]) { match -> values[match.groupValues[1]] ?: match.value }
}

val LocalMarkdownStrings = staticCompositionLocalOf { MarkdownStrings() }
