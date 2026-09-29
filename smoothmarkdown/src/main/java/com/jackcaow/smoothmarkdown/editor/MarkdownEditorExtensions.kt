package com.jackcaow.smoothmarkdown.editor

/** Controls built-in commands shown by this editor's toolbar and slash menu and accepted by its shortcuts.
 *
 * [MarkdownEditorController.applyCommand] remains a public imperative API and is not filtered here.
 * Find, Focus mode, and history shortcuts are independent of this setting.
 */
data class MarkdownEditorCapabilities(
    val disabledCommands: Set<MarkdownEditorCommand> = emptySet(),
) {
    fun supports(command: MarkdownEditorCommand): Boolean = command !in disabledCommands

    companion object {
        val All = MarkdownEditorCapabilities()
    }
}

/** A host command shown after built-in slash commands and inserted as one undoable edit. */
class MarkdownEditorSlashCommand(
    val title: String,
    val searchText: String,
    val markdown: String? = null,
    val onSelected: (suspend (query: String) -> String?)? = null,
) {
    init {
        require(title.isNotBlank()) { "A slash command needs a title" }
        require(markdown != null || onSelected != null) { "A slash command needs Markdown or a callback" }
    }
}
