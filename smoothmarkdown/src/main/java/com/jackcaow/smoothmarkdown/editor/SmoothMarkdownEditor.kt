package com.jackcaow.smoothmarkdown.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.WikilinkPlugin
import kotlinx.coroutines.launch

/** Source editor, preview, split view, and a focused formatted-block editing surface. */
@Composable
fun SmoothMarkdownEditor(
    controller: MarkdownEditorController,
    modifier: Modifier = Modifier,
    onSave: ((String) -> Unit)? = null,
    onPickImage: (suspend () -> MarkdownEditorImageSelection?)? = null,
    onImagePickEvent: ((MarkdownEditorImagePickEvent) -> Unit)? = null,
    onImportMarkdown: (suspend () -> String?)? = null,
    onExportMarkdown: (suspend (String) -> Unit)? = null,
    onExportPdf: (suspend (String, String) -> Unit)? = null,
    onHostActionError: ((MarkdownEditorHostAction, Throwable) -> Unit)? = null,
    enableWikilinks: Boolean = true,
    wikilinkSuggestions: List<String> = emptyList(),
    onTapWikilink: ((String) -> Unit)? = null,
) {
    SideEffect { controller.enableWikilinks = enableWikilinks }
    val previewPlugins = remember(enableWikilinks) {
        if (enableWikilinks) ParserPluginRegistry().also { it.register(WikilinkPlugin()) } else null
    }
    val scope = rememberCoroutineScope()
    var hostActionBusy by remember { mutableStateOf(false) }
    var hostStatus by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var focusMode by remember { mutableStateOf(false) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen, searchFocusRequest) {
        if (searchOpen) searchFocusRequester.requestFocus()
    }
    val searchMatches = if (searchOpen) controller.findMatches(searchQuery) else emptyList()
    val slashTrigger = if (controller.mode == MarkdownEditorMode.PREVIEW) null else MarkdownSlashCommands.match(controller)
    val slashSuggestions = slashTrigger?.let { MarkdownSlashCommands.suggestions(it, enableWikilinks) }.orEmpty()
    fun runHostAction(label: String, action: suspend () -> MarkdownEditorHostResult) {
        if (hostActionBusy) return
        hostActionBusy = true
        scope.launch {
            try {
                hostStatus = when (action()) {
                    MarkdownEditorHostResult.SUCCESS -> "$label complete"
                    MarkdownEditorHostResult.CANCELLED -> "$label cancelled"
                    MarkdownEditorHostResult.STALE -> "$label cancelled: document changed"
                    MarkdownEditorHostResult.FAILED -> "$label failed"
                }
            } finally { hostActionBusy = false }
        }
    }
    Column(modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || event.isAltPressed) {
            false
        } else when {
            event.key == Key.F && !event.isShiftPressed -> {
                searchOpen = true
                searchFocusRequest++
                true
            }
            event.key == Key.Enter && event.isShiftPressed -> {
                focusMode = !focusMode
                true
            }
            else -> false
        }
    }) {
        if (!focusMode) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    MarkdownEditorMode.entries.forEach { mode ->
                        TextButton(onClick = { controller.mode = mode }) {
                            Text(mode.name.lowercase().replaceFirstChar(Char::uppercaseChar))
                        }
                    }
                }
            if (onSave != null) {
                Button(onClick = {
                    onSave(controller.text)
                    controller.markSaved()
                }, enabled = controller.isDirty) { Text("Save") }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { focusMode = !focusMode }, modifier = Modifier.testTag(if (focusMode) "editor-exit-focus" else "editor-focus-mode")) {
                Text(if (focusMode) "Exit focus" else "Focus mode")
            }
            if (!focusMode) {
                TextButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("editor-find")) {
                    Text(if (searchOpen) "Close find" else "Find")
                }
            }
        }
        if (searchOpen) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Find in note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester).testTag("editor-search-query"),
                )
                Row {
                    Text("${searchMatches.size} matches", modifier = Modifier.padding(8.dp).testTag("editor-search-count"))
                    TextButton(onClick = {
                        if (controller.selectPreviousMatch(searchQuery) != null) controller.mode = MarkdownEditorMode.SOURCE
                    }, enabled = searchMatches.isNotEmpty(), modifier = Modifier.testTag("editor-search-previous")) { Text("Previous") }
                    TextButton(onClick = {
                        if (controller.selectNextMatch(searchQuery) != null) controller.mode = MarkdownEditorMode.SOURCE
                    }, enabled = searchMatches.isNotEmpty(), modifier = Modifier.testTag("editor-search-next")) { Text("Next") }
                }
            }
        }
        if (slashTrigger != null && slashSuggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).testTag("editor-slash-suggestions")) {
                slashSuggestions.forEachIndexed { index, item ->
                    TextButton(
                        onClick = { MarkdownSlashCommands.apply(controller, slashTrigger, item.command) },
                        modifier = Modifier.testTag("editor-slash-suggestion-$index"),
                    ) { Text(item.title) }
                }
            }
        }
        if (!focusMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { controller.undo() }, enabled = controller.canUndo) { Text("Undo") }
            TextButton(onClick = { controller.redo() }, enabled = controller.canRedo) { Text("Redo") }
            listOf(
                "B" to MarkdownEditorCommand.BOLD,
                "I" to MarkdownEditorCommand.ITALIC,
                "H1" to MarkdownEditorCommand.HEADING1,
                "List" to MarkdownEditorCommand.UNORDERED_LIST,
                "Task" to MarkdownEditorCommand.TASK_LIST,
                (if (controller.mode == MarkdownEditorMode.FORMATTED) "Inline code" else "Code") to
                    (if (controller.mode == MarkdownEditorMode.FORMATTED) MarkdownEditorCommand.INLINE_CODE else MarkdownEditorCommand.CODE_BLOCK),
                "Link" to MarkdownEditorCommand.LINK,
                "Table" to MarkdownEditorCommand.TABLE,
                "Wikilink" to MarkdownEditorCommand.WIKILINK,
            ).forEach { (label, command) ->
                TextButton(onClick = { controller.applyCommand(command) },
                    enabled = command != MarkdownEditorCommand.WIKILINK || enableWikilinks) { Text(label) }
            }
            if (onPickImage != null) {
                TextButton(onClick = {
                    runHostAction("Image") {
                        MarkdownEditorHostActions.pickAndInsertImage(controller, onPickImage, onImagePickEvent, onHostActionError)
                    }
                }, enabled = !hostActionBusy, modifier = Modifier.testTag("editor-pick-image")) { Text("Image") }
            }
            if (onImportMarkdown != null) {
                TextButton(onClick = {
                    runHostAction("Import") {
                        MarkdownEditorHostActions.importMarkdown(controller, onImportMarkdown, onHostActionError)
                    }
                }, enabled = !hostActionBusy, modifier = Modifier.testTag("editor-import-markdown")) { Text("Import") }
            }
            if (onExportMarkdown != null) {
                TextButton(onClick = {
                    runHostAction("Export") {
                        MarkdownEditorHostActions.exportMarkdown(controller, onExportMarkdown, onHostActionError)
                    }
                }, enabled = !hostActionBusy, modifier = Modifier.testTag("editor-export-markdown")) { Text("Export") }
            }
            if (onExportPdf != null) {
                TextButton(onClick = {
                    runHostAction("PDF export") {
                        MarkdownEditorHostActions.exportPdf(controller, onExportPdf, onHostActionError)
                    }
                }, enabled = !hostActionBusy, modifier = Modifier.testTag("editor-export-pdf")) { Text("Export PDF") }
            }
        }
        if (hostStatus.isNotEmpty()) Text(hostStatus, modifier = Modifier.testTag("editor-host-status"))
        Spacer(Modifier.height(8.dp))
        when (controller.mode) {
            MarkdownEditorMode.SOURCE -> SourcePane(controller, Modifier.weight(1f))
            MarkdownEditorMode.PREVIEW -> SmoothMarkdown(controller.text, Modifier.weight(1f),
                plugins = previewPlugins, onWikilinkClick = onTapWikilink)
            MarkdownEditorMode.SPLIT -> Row(Modifier.weight(1f)) {
                SourcePane(controller, Modifier.weight(1f))
                SmoothMarkdown(controller.text, Modifier.weight(1f),
                    plugins = previewPlugins, onWikilinkClick = onTapWikilink)
            }
            MarkdownEditorMode.FORMATTED -> FormattedBlockPane(controller, Modifier.weight(1f), wikilinkSuggestions)
        }
    }
}

@Composable
private fun SourcePane(controller: MarkdownEditorController, modifier: Modifier) {
    BasicTextField(
        value = controller.value,
        onValueChange = controller::updateFromInput,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp).testTag("editor-source-input"),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

/** Paragraphs, ATX headings, fenced code, and GFM tables expose source-backed content. */
@Composable
private fun FormattedBlockPane(controller: MarkdownEditorController, modifier: Modifier, wikilinkSuggestions: List<String>) {
    val blocks = controller.semanticDocument().blocks
    val pendingExit = controller.pendingListExit
    val clipboard = LocalClipboardManager.current
    val blockSelection = controller.formattedBlockSelection?.takeIf { it.source == controller.text }
    var blockReplacement by remember(controller) { mutableStateOf("") }
    var blockSelectionError by remember(controller) { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (blockSelection != null) {
            Text("${blockSelection.lastIndex - blockSelection.firstIndex + 1} block(s) selected", modifier = Modifier.testTag("formatted-block-selection-count"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    controller.copyFormattedBlockSelectionAsMarkdown()?.let { clipboard.setText(AnnotatedString(it)) }
                }, modifier = Modifier.testTag("formatted-block-copy")) { Text("Copy Markdown") }
                TextButton(onClick = {
                    blockSelectionError = !controller.deleteFormattedBlockSelection()
                }, modifier = Modifier.testTag("formatted-block-delete")) { Text("Delete blocks") }
                TextButton(onClick = {
                    blockSelectionError = !controller.replaceFormattedBlockSelectionWithMarkdown(blockReplacement)
                    if (!blockSelectionError) blockReplacement = ""
                }, modifier = Modifier.testTag("formatted-block-replace")) { Text("Replace blocks") }
                TextButton(onClick = {
                    controller.clearFormattedBlockSelection()
                    blockSelectionError = false
                }, modifier = Modifier.testTag("formatted-block-clear")) { Text("Clear") }
            }
            OutlinedTextField(
                value = blockReplacement,
                onValueChange = { blockReplacement = it; blockSelectionError = false },
                label = { Text("Replacement Markdown") },
                modifier = Modifier.fillMaxWidth().testTag("formatted-block-replacement"),
            )
            if (blockSelectionError) Text("This edit would change neighboring blocks", modifier = Modifier.testTag("formatted-block-edit-error"))
        }
        var pendingRendered = false
        blocks.forEach { block ->
            if (!pendingRendered && pendingExit != null && pendingExit.offset < block.range.min) {
                PendingEmptyParagraphField(controller, Modifier.fillMaxWidth().padding(bottom = 10.dp))
                pendingRendered = true
            }
            if (pendingExit != null && pendingExit.offset > block.range.min && pendingExit.offset < block.range.max &&
                block.kind in setOf(MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.ORDERED_LIST)) pendingRendered = true
            val editableText = MarkdownFormattedBlock.text(block)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                color = if (block.kind == MarkdownBlockKind.CODE) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = when (block.kind) {
                                MarkdownBlockKind.HEADING -> "Heading ${block.headingLevel}"
                                MarkdownBlockKind.CODE -> "Code${block.language?.let { " · $it" }.orEmpty()}"
                                else -> block.kind.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            controller.selectFormattedBlock(block.id)
                            blockSelectionError = false
                        }, modifier = Modifier.testTag("formatted-block-select-${block.id}").semantics {
                            selected = blockSelection?.let { blocks.indexOf(block) in it.firstIndex..it.lastIndex } == true
                        }) { Text(if (blockSelection == null) "Select" else "Extend") }
                        if (block.kind == MarkdownBlockKind.HEADING && editableText != null) {
                            Row {
                                (1..3).forEach { level ->
                                    TextButton(onClick = { controller.setSemanticHeadingLevel(block.id, level) }) {
                                        Text("H$level")
                                    }
                                }
                            }
                        }
                    }
                    if (block.kind == MarkdownBlockKind.TABLE) {
                        val table = controller.semanticTable(block.id)
                        if (table != null) FormattedTable(controller, block.id, table)
                        else Text(block.source, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    } else if (block.kind == MarkdownBlockKind.BULLET_LIST || block.kind == MarkdownBlockKind.ORDERED_LIST) {
                        val list = MarkdownSourceList.parse(block)
                        if (list != null) FormattedList(controller, block.id, list)
                        else Text(block.source, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    } else if (editableText != null) {
                        val inline = MarkdownFormattedBlock.inline(block, controller.enableWikilinks)
                        val rawSelection = if (controller.activeFormattedBlockId == block.id) controller.formattedSelection else TextRange.Zero
                        val visibleLength = inline?.visible?.length ?: editableText.length
                        val fieldSelection = TextRange(rawSelection.start.coerceIn(0, visibleLength), rawSelection.end.coerceIn(0, visibleLength))
                        val rawComposition = if (controller.activeFormattedBlockId == block.id) controller.formattedComposition else null
                        val fieldComposition = rawComposition?.let {
                            TextRange(it.start.coerceIn(0, visibleLength), it.end.coerceIn(0, visibleLength))
                        }
                        val trigger = if (controller.enableWikilinks && controller.activeFormattedBlockId == block.id && inline != null)
                            WikilinkAutocomplete.match(inline.visible, fieldSelection, inline.marks) else null
                        val suggestions = trigger?.let { WikilinkAutocomplete.suggestions(it, wikilinkSuggestions) }.orEmpty()
                        var selectedSuggestion by remember(block.id) { mutableIntStateOf(0) }
                        var dismissedQuery by remember(block.id) { mutableStateOf<String?>(null) }
                        val showSuggestions = trigger != null && dismissedQuery != trigger.query
                        val blockFocusRequester = remember(block.id) { FocusRequester() }
                        val blockFocusTarget = controller.formattedBlockFocusTarget
                        LaunchedEffect(blockFocusTarget) {
                            if (blockFocusTarget == block.id) {
                                blockFocusRequester.requestFocus()
                                controller.clearFormattedBlockFocusTarget(block.id)
                            }
                        }
                        BasicTextField(
                            value = TextFieldValue(inline?.annotated(MaterialTheme.colorScheme.primary) ?: androidx.compose.ui.text.AnnotatedString(editableText), fieldSelection, fieldComposition),
                            onValueChange = { next ->
                                selectedSuggestion = 0
                                dismissedQuery = null
                                if (inline != null) {
                                    controller.setFormattedSelection(block.id, next.selection, next.composition)
                                    if (next.text != inline.visible) controller.replaceFormattedInlineText(block.id, next.text, next.selection, next.composition)
                                } else {
                                    controller.replaceFormattedBlockText(block.id, next.text)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).focusRequester(blockFocusRequester).onPreviewKeyEvent { event ->
                                if (!showSuggestions || event.type != KeyEventType.KeyDown) false else when (event.key) {
                                    Key.DirectionDown -> {
                                        if (suggestions.isNotEmpty()) selectedSuggestion = (selectedSuggestion + 1) % suggestions.size
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (suggestions.isNotEmpty()) selectedSuggestion = (selectedSuggestion - 1 + suggestions.size) % suggestions.size
                                        true
                                    }
                                    Key.Enter -> {
                                        suggestions.getOrNull(selectedSuggestion.coerceAtMost(suggestions.lastIndex))?.let(controller::insertWikilinkSuggestion) == true
                                    }
                                    Key.Escape -> { dismissedQuery = trigger?.query; true }
                                    else -> false
                                }
                            }.onFocusChanged {
                                if (it.isFocused) controller.setFormattedSelection(block.id, fieldSelection)
                            },
                            textStyle = when (block.kind) {
                                MarkdownBlockKind.HEADING -> MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                MarkdownBlockKind.CODE -> MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                )
                                else -> MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                            },
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        )
                        if (showSuggestions) {
                            Column(Modifier.fillMaxWidth().testTag("wikilink-suggestions")) {
                                if (suggestions.isEmpty()) Text("No matching notes", modifier = Modifier.testTag("wikilink-empty"))
                                suggestions.forEachIndexed { index, title ->
                                    TextButton(onClick = { controller.insertWikilinkSuggestion(title) },
                                        modifier = Modifier.testTag("wikilink-suggestion-$index").semantics {
                                            selected = index == selectedSuggestion
                                        }) {
                                        Text(title)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            block.source,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (pendingExit != null && !pendingRendered) {
            PendingEmptyParagraphField(controller, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
    }
}

@Composable
private fun PendingEmptyParagraphField(controller: MarkdownEditorController, modifier: Modifier = Modifier) {
    val offset = controller.pendingListExit?.offset ?: return
    val focusRequester = remember(offset) { FocusRequester() }
    LaunchedEffect(offset) { focusRequester.requestFocus() }
    BasicTextField(
        value = TextFieldValue("", TextRange.Zero),
        onValueChange = { controller.completePendingListExit(it.text, it.selection) },
        modifier = modifier.heightIn(min = 32.dp).focusRequester(focusRequester).testTag("formatted-empty-paragraph"),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { inner() }
        },
    )
}

@Composable
private fun FormattedList(controller: MarkdownEditorController, blockId: String, list: MarkdownSourceList) {
    val block = controller.semanticDocument().blockById(blockId)
    val pending = controller.pendingListExit
    val showPending = block != null && pending != null && pending.offset > block.range.min && pending.offset < block.range.max
    FormattedListItems(controller, blockId, list, list.items, emptyList(), 0, 0, showPending)
}

@Composable
private fun FormattedListItems(
    controller: MarkdownEditorController,
    blockId: String,
    list: MarkdownSourceList,
    items: List<MarkdownSourceList.Item>,
    pathPrefix: List<Int>,
    depth: Int,
    indexBase: Int,
    showPending: Boolean = false,
) {
    Column {
        items.forEachIndexed { index, item ->
            if (showPending && controller.pendingListExit?.beforeItemCount == index) {
                PendingEmptyParagraphField(controller, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
            val path = pathPrefix + (indexBase + index)
            val pathTag = path.joinToString("-")
            val firstLine = item.lines.firstOrNull { it.start == item.contentStart } ?: item.lines.firstOrNull()
            Row(
                Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(8) * 20).dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (item.taskStateOffset != null) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { controller.setFormattedTaskChecked(blockId, path, it) },
                        modifier = Modifier.testTag("formatted-task-$blockId-$pathTag"),
                    )
                } else {
                    Text(
                        text = if (item.marker.first().isDigit()) item.marker else "•",
                        modifier = Modifier.width(40.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (firstLine != null) {
                    val lineIndex = item.lines.indexOf(firstLine)
                    FormattedListTextField(controller, blockId, list, path, lineIndex,
                        Modifier.weight(1f).padding(vertical = 4.dp).testTag("formatted-list-item-$blockId-$pathTag"))
                }
            }
            var nestedBase = 0
            item.parts.forEach { part ->
                when (part) {
                    is MarkdownSourceList.Line -> if (part != firstLine) {
                        val lineIndex = item.lines.indexOf(part)
                        FormattedListTextField(controller, blockId, list, path, lineIndex,
                            Modifier.fillMaxWidth().padding(start = ((depth + 1).coerceAtMost(9) * 20).dp, top = 2.dp, bottom = 2.dp)
                                .testTag("formatted-list-continuation-$blockId-$pathTag-$lineIndex"))
                    }
                    is MarkdownSourceList.NestedList -> {
                        FormattedListItems(controller, blockId, list, part.items, path, depth + 1, nestedBase)
                        nestedBase += part.items.size
                    }
                    is MarkdownSourceList.Raw -> Text(
                        text = list.rawContent(part),
                        modifier = Modifier.fillMaxWidth().padding(start = ((depth + 1).coerceAtMost(9) * 20).dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
        if (showPending && controller.pendingListExit?.beforeItemCount == items.size) {
            PendingEmptyParagraphField(controller, Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun FormattedListTextField(
    controller: MarkdownEditorController,
    blockId: String,
    list: MarkdownSourceList,
    path: List<Int>,
    lineIndex: Int,
    modifier: Modifier,
) {
    val inline = MarkdownInlineEditing.parse(list.lineContent(path, lineIndex).orEmpty(), controller.enableWikilinks)
    val active = controller.activeFormattedBlockId == blockId && controller.activeFormattedListPath == path &&
        controller.activeFormattedListLine == lineIndex
    val selection = if (active) controller.formattedListSelection else TextRange(inline.visible.length)
    val safeSelection = TextRange(selection.start.coerceIn(0, inline.visible.length), selection.end.coerceIn(0, inline.visible.length))
    val rawComposition = if (active) controller.formattedListComposition else null
    val safeComposition = rawComposition?.let {
        TextRange(it.start.coerceIn(0, inline.visible.length), it.end.coerceIn(0, inline.visible.length))
    }
    val focusRequester = remember(blockId, path, lineIndex) { FocusRequester() }
    val focusTarget = controller.formattedListFocusTarget
    LaunchedEffect(focusTarget) {
        if (focusTarget == (path to lineIndex)) {
            focusRequester.requestFocus()
            controller.clearFormattedListFocusTarget(path, lineIndex)
        }
    }
    BasicTextField(
        value = TextFieldValue(inline.annotated(MaterialTheme.colorScheme.primary), safeSelection, safeComposition),
        onValueChange = { next ->
            val newline = next.text.indexOf('\n')
            if (newline >= 0 && next.text.removeRange(newline, newline + 1) == inline.visible) {
                controller.splitFormattedListLine(blockId, path, lineIndex, newline)
            } else if (next.text == inline.visible || controller.replaceFormattedListLineText(blockId, path, lineIndex, next.text)) {
                controller.setFormattedListSelection(blockId, path, lineIndex, next.selection, next.composition)
            }
        },
        modifier = modifier.focusRequester(focusRequester).onFocusChanged {
            if (it.isFocused) controller.setFormattedListSelection(blockId, path, lineIndex, safeSelection)
        }.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed) false
            else when (event.key) {
                Key.Tab -> {
                    if (event.isShiftPressed) controller.outdentFormattedListItem(blockId, path)
                    else controller.indentFormattedListItem(blockId, path)
                    true
                }
                Key.Enter -> {
                    if (event.isShiftPressed || !safeSelection.collapsed) false
                    else controller.splitFormattedListLine(blockId, path, lineIndex, safeSelection.start)
                }
                else -> false
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun FormattedTable(controller: MarkdownEditorController, blockId: String, table: MarkdownSourceTable) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        fun displayCell(raw: String) = raw.replace("\\|", "|")
        @Composable fun cell(raw: String, header: Boolean, rowIndex: Int, columnIndex: Int) {
            BasicTextField(
                value = displayCell(raw),
                onValueChange = { next ->
                    controller.editSemanticTable(blockId) { it.replaceCell(rowIndex, columnIndex, next, header) }
                },
                modifier = Modifier.width(140.dp).padding(4.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        Row {
            table.headers.forEachIndexed { column, raw -> cell(raw, true, 0, column) }
        }
        table.rows.forEachIndexed { row, cells ->
            Row { cells.forEachIndexed { column, raw -> cell(raw, false, row, column) } }
        }
        Row {
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.insertRowAfter(it.rows.lastIndex) } }) { Text("+ Row") }
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.insertColumnAfter(it.columnCount - 1) } }) { Text("+ Column") }
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.deleteRow(it.rows.lastIndex) } }, enabled = table.rows.isNotEmpty()) { Text("− Row") }
            TextButton(onClick = { controller.editSemanticTable(blockId) { it.deleteColumn(it.columnCount - 1) } }, enabled = table.columnCount > 1) { Text("− Column") }
        }
    }
}
