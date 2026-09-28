package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange

/** A slash trigger at the beginning of a source-backed paragraph line. */
internal data class MarkdownSlashTrigger(val range: TextRange, val query: String)

internal object MarkdownSlashCommands {
    val commands = listOf(
        "Heading 1" to MarkdownEditorCommand.HEADING1,
        "Heading 2" to MarkdownEditorCommand.HEADING2,
        "Heading 3" to MarkdownEditorCommand.HEADING3,
        "Bullet list" to MarkdownEditorCommand.UNORDERED_LIST,
        "Numbered list" to MarkdownEditorCommand.ORDERED_LIST,
        "Task list" to MarkdownEditorCommand.TASK_LIST,
        "Quote" to MarkdownEditorCommand.BLOCKQUOTE,
        "Code block" to MarkdownEditorCommand.CODE_BLOCK,
        "Table" to MarkdownEditorCommand.TABLE,
        "Math block" to MarkdownEditorCommand.BLOCK_MATH,
        "Mermaid diagram" to MarkdownEditorCommand.MERMAID_DIAGRAM,
        "Divider" to MarkdownEditorCommand.HORIZONTAL_RULE,
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

    fun suggestions(trigger: MarkdownSlashTrigger): List<Pair<String, MarkdownEditorCommand>> =
        commands.filter { (title, command) ->
            val query = trigger.query
            title.contains(query, ignoreCase = true) || command.name.replace('_', ' ').contains(query, ignoreCase = true)
        }

    fun apply(controller: MarkdownEditorController, trigger: MarkdownSlashTrigger, command: MarkdownEditorCommand): Boolean {
        if (commands.none { it.second == command } || match(controller) != trigger) return false
        controller.transaction {
            controller.replaceRange(trigger.range.min, trigger.range.max, "")
            controller.applyCommand(command)
        }
        return true
    }
}
