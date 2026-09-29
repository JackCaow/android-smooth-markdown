package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CancellationException

/** A slash trigger at the beginning of a source-backed paragraph line. */
internal data class MarkdownSlashTrigger(val range: TextRange, val query: String)

internal data class MarkdownSlashCommand(
    val title: String,
    val searchText: String,
    val command: MarkdownEditorCommand,
)

internal data class MarkdownSlashSuggestion(
    val title: String,
    val command: MarkdownEditorCommand? = null,
    val customCommand: MarkdownEditorSlashCommand? = null,
)

internal object MarkdownSlashCommands {
    val commands = listOf(
        MarkdownSlashCommand("Text", "paragraph body plain normal", MarkdownEditorCommand.PARAGRAPH),
        MarkdownSlashCommand("Heading 1", "heading h1 title", MarkdownEditorCommand.HEADING1),
        MarkdownSlashCommand("Heading 2", "h2 heading subtitle", MarkdownEditorCommand.HEADING2),
        MarkdownSlashCommand("Heading 3", "h3 heading", MarkdownEditorCommand.HEADING3),
        MarkdownSlashCommand("Heading 4", "heading h4", MarkdownEditorCommand.HEADING4),
        MarkdownSlashCommand("Heading 5", "heading h5", MarkdownEditorCommand.HEADING5),
        MarkdownSlashCommand("Heading 6", "heading h6", MarkdownEditorCommand.HEADING6),
        MarkdownSlashCommand("Bullet List", "bullet unordered ul list", MarkdownEditorCommand.UNORDERED_LIST),
        MarkdownSlashCommand("Numbered List", "number ordered ol list numbered", MarkdownEditorCommand.ORDERED_LIST),
        MarkdownSlashCommand("Task List", "todo checklist checkbox task", MarkdownEditorCommand.TASK_LIST),
        MarkdownSlashCommand("Blockquote", "blockquote quote", MarkdownEditorCommand.BLOCKQUOTE),
        MarkdownSlashCommand("Code Block", "code fenced block pre", MarkdownEditorCommand.CODE_BLOCK),
        MarkdownSlashCommand("Mermaid Diagram", "mermaid diagram flowchart chart", MarkdownEditorCommand.MERMAID_DIAGRAM),
        MarkdownSlashCommand("Block Math", "math equation", MarkdownEditorCommand.BLOCK_MATH),
        MarkdownSlashCommand("Horizontal Rule", "divider separator hr line horizontal rule", MarkdownEditorCommand.HORIZONTAL_RULE),
        MarkdownSlashCommand("Image", "picture photo img", MarkdownEditorCommand.IMAGE),
        MarkdownSlashCommand("Table", "table grid", MarkdownEditorCommand.TABLE),
        MarkdownSlashCommand("Wikilink", "wiki note link wikilink [[", MarkdownEditorCommand.WIKILINK),
    )

    fun match(controller: MarkdownEditorController): MarkdownSlashTrigger? {
        val text = controller.text
        val cursor = if (controller.mode == MarkdownEditorMode.FORMATTED) {
            val block = controller.semanticDocument().blockById(controller.activeFormattedBlockId ?: return null)
                ?: return null
            if (block.kind != MarkdownBlockKind.PARAGRAPH || !controller.formattedSelection.collapsed) return null
            // Only plain paragraph text has a direct visible-to-source offset mapping.
            if (MarkdownFormattedBlock.inline(block, controller.enableWikilinks)?.visible != block.source) return null
            val offset = controller.formattedSelection.min
            if (offset !in 0..block.source.length) return null
            block.range.min + offset
        } else {
            if (!controller.selection.collapsed) return null
            controller.selection.min
        }
        if (cursor !in 0..text.length) return null
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let {
            if (cursor == 0) 0 else it + 1
        }
        val prefix = text.substring(lineStart, cursor)
        if (!prefix.startsWith('/') || prefix.drop(1).any(Char::isWhitespace)) return null
        val block = controller.semanticDocument().blocks.firstOrNull {
            lineStart >= it.range.min && cursor <= it.range.max
        } ?: return null
        if (block.kind != MarkdownBlockKind.PARAGRAPH) return null
        return MarkdownSlashTrigger(TextRange(lineStart, cursor), prefix.drop(1))
    }

    fun suggestions(
        trigger: MarkdownSlashTrigger,
        enableWikilinks: Boolean = true,
        capabilities: MarkdownEditorCapabilities = MarkdownEditorCapabilities.All,
    ): List<MarkdownSlashCommand> =
        commands.filter { item ->
            if (item.command == MarkdownEditorCommand.WIKILINK && !enableWikilinks) return@filter false
            if (!capabilities.supports(item.command)) return@filter false
            val query = trigger.query
            item.title.contains(query, ignoreCase = true) || item.searchText.contains(query, ignoreCase = true)
        }

    fun allSuggestions(
        trigger: MarkdownSlashTrigger,
        enableWikilinks: Boolean,
        capabilities: MarkdownEditorCapabilities,
        customCommands: List<MarkdownEditorSlashCommand>,
    ): List<MarkdownSlashSuggestion> =
        suggestions(trigger, enableWikilinks, capabilities).map { MarkdownSlashSuggestion(it.title, command = it.command) } +
            customCommands.filter {
                it.title.contains(trigger.query, ignoreCase = true) ||
                    it.searchText.contains(trigger.query, ignoreCase = true)
            }.map { MarkdownSlashSuggestion(it.title, customCommand = it) }

    fun apply(
        controller: MarkdownEditorController,
        trigger: MarkdownSlashTrigger,
        command: MarkdownEditorCommand,
        capabilities: MarkdownEditorCapabilities = MarkdownEditorCapabilities.All,
    ): Boolean {
        if (commands.none { it.command == command } ||
            !capabilities.supports(command) ||
            (command == MarkdownEditorCommand.WIKILINK && !controller.enableWikilinks) ||
            match(controller) != trigger) return false
        val formattedBlock = if (controller.mode == MarkdownEditorMode.FORMATTED) {
            controller.activeFormattedBlockId?.let { controller.semanticDocument().blockById(it) }
        } else null
        controller.transaction {
            if (command == MarkdownEditorCommand.WIKILINK) {
                // Flutter's slash Wikilink opens autocomplete rather than inserting placeholder text.
                controller.replaceRange(trigger.range.min, trigger.range.max, "[[")
                if (formattedBlock != null) controller.setFormattedSelection(
                    formattedBlock.id, TextRange(trigger.range.min - formattedBlock.range.min + 2))
            } else {
                controller.replaceRange(trigger.range.min, trigger.range.max, "")
                controller.applyCommand(command)
            }
        }
        return true
    }

    /** Resolve the host callback before editing, then reject a stale slash trigger. */
    suspend fun applyCustom(
        controller: MarkdownEditorController,
        trigger: MarkdownSlashTrigger,
        command: MarkdownEditorSlashCommand,
    ): Boolean {
        if (match(controller) != trigger) return false
        val markdown = try {
            command.markdown ?: command.onSelected?.invoke(trigger.query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
        if (markdown.isNullOrBlank() || match(controller) != trigger) return false
        controller.transaction {
            controller.replaceRange(trigger.range.min, trigger.range.max, "")
            controller.insertMarkdownBlock(markdown)
        }
        return true
    }
}
