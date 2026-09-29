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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
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

/** Compose content placed before or after the native editor toolbar controls. */
typealias MarkdownEditorToolbarSlot = @Composable () -> Unit

/** Wrap or replace the complete default toolbar. Call [defaultToolbar] to retain its controls. */
typealias MarkdownEditorToolbarBuilder = @Composable (defaultToolbar: MarkdownEditorToolbarSlot) -> Unit

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
    enableSlashCommands: Boolean = true,
    customSlashCommands: List<MarkdownEditorSlashCommand> = emptyList(),
    capabilities: MarkdownEditorCapabilities = MarkdownEditorCapabilities.All,
    toolbarCommands: List<MarkdownEditorCommand>? = null,
    /** Whether the toolbar is shown. Find results and suggestions remain available. */
    showToolbar: Boolean = true,
    /** Host controls before the mode buttons, in the supplied order. */
    toolbarLeading: List<MarkdownEditorToolbarSlot> = emptyList(),
    /** Host controls after Find, in the supplied order. */
    toolbarTrailing: List<MarkdownEditorToolbarSlot> = emptyList(),
    /** Wrap or replace the complete default toolbar. */
    toolbarBuilder: MarkdownEditorToolbarBuilder? = null,
    customBlockMatcher: ((MarkdownDocumentBlock) -> Boolean)? = null,
    customBlockBuilder: MarkdownEditorCustomBlockBuilder? = null,
    customBlockEditorBuilder: MarkdownEditorCustomBlockEditorBuilder? = null,
    /** Whether the built-in keyboard shortcuts are handled. */
    enableKeyboardShortcuts: Boolean = true,
    /** Host key handler runs before the built-in shortcuts, even when they are disabled. */
    onShortcut: ((KeyEvent, MarkdownEditorController) -> Boolean)? = null,
    /** Called for accepted built-in commands from the toolbar, slash menu, or keyboard. */
    onCommand: ((MarkdownEditorCommand) -> Unit)? = null,
    /** Called once for each committed source change, including undo and host edits. */
    onChanged: ((String) -> Unit)? = null,
    /** When supplied, the host owns the display mode; user requests arrive in [onModeChanged]. */
    mode: MarkdownEditorMode? = null,
    onModeChanged: ((MarkdownEditorMode) -> Unit)? = null,
    /** Source offsets use UTF-16 and are reported when the selection changes. */
    onSelectionChanged: ((TextRange) -> Unit)? = null,
    /** Reports focus transitions of the source field, not formatted or search fields. */
    onFocusChanged: ((Boolean) -> Unit)? = null,
    /** Reports an after-frame snapshot of native editor state. */
    onPerformanceSnapshot: ((MarkdownEditorPerformanceSnapshot) -> Unit)? = null,
    initialFocusMode: Boolean = false,
    onFocusModeChanged: ((Boolean) -> Unit)? = null,
) {
    SideEffect {
        controller.enableWikilinks = enableWikilinks
        if (mode != null && controller.mode != mode) controller.mode = mode
    }
    val latestOnChanged = rememberUpdatedState(onChanged)
    val latestOnSelectionChanged = rememberUpdatedState(onSelectionChanged)
    val latestOnFocusChanged = rememberUpdatedState(onFocusChanged)
    val latestOnPerformanceSnapshot = rememberUpdatedState(onPerformanceSnapshot)
    val hostEvents = remember(controller) { MarkdownEditorHostEvents(controller.value) }
    val sourceFocus = remember(controller) { MarkdownEditorSourceFocusTracker() }
    val performanceReporter = remember(controller) { MarkdownEditorPerformanceReporter() }
    DisposableEffect(controller, hostEvents) {
        val observer: (TextFieldValue) -> Unit = { value ->
            hostEvents.accept(value, latestOnChanged.value, latestOnSelectionChanged.value)
        }
        controller.addValueObserver(observer)
        onDispose { controller.removeValueObserver(observer) }
    }
    DisposableEffect(controller, sourceFocus) {
        onDispose { sourceFocus.setFocused(false, latestOnFocusChanged.value) }
    }
    val previewPlugins = remember(controller.parserPlugins, enableWikilinks) {
        controller.parserPlugins?.copy()?.also { registry ->
            if (enableWikilinks && registry.getInlinePlugin("wikilink") == null) registry.register(WikilinkPlugin())
            if (!enableWikilinks) registry.unregisterInline("wikilink")
        } ?: if (enableWikilinks) ParserPluginRegistry().also { it.register(WikilinkPlugin()) } else null
    }
    val scope = rememberCoroutineScope()
    var hostActionBusy by remember { mutableStateOf(false) }
    var hostStatus by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var focusMode by remember { mutableStateOf(initialFocusMode) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen, searchFocusRequest) {
        if (searchOpen) searchFocusRequester.requestFocus()
    }
    val searchMatches = if (searchOpen) controller.findMatches(searchQuery) else emptyList()
    val slashTrigger = if (!enableSlashCommands || controller.mode == MarkdownEditorMode.PREVIEW) null else MarkdownSlashCommands.match(controller)
    val slashSuggestions = slashTrigger?.let {
        MarkdownSlashCommands.allSuggestions(it, enableWikilinks, capabilities, customSlashCommands)
    }.orEmpty()
    val slashSuggestionsVisible = slashTrigger != null && slashSuggestions.isNotEmpty()
    LaunchedEffect(controller, controller.value, controller.mode, searchOpen, searchQuery,
        slashSuggestionsVisible, onPerformanceSnapshot != null) {
        if (onPerformanceSnapshot != null) {
            // Consecutive synchronous edits settle into one snapshot of the final frame.
            withFrameNanos { }
            latestOnPerformanceSnapshot.value?.invoke(performanceReporter.capture(
                controller, searchQuery, searchOpen, slashSuggestionsVisible,
            ))
        }
    }
    fun requestMode(next: MarkdownEditorMode) {
        if (next == controller.mode) return
        if (mode == null) controller.mode = next
        onModeChanged?.invoke(next)
    }
    fun toggleFocusMode() {
        focusMode = !focusMode
        onFocusModeChanged?.invoke(focusMode)
    }
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
    fun applyEditorCommand(command: MarkdownEditorCommand): Boolean {
        if (!capabilities.supports(command) ||
            (command == MarkdownEditorCommand.WIKILINK && !enableWikilinks)) return false
        onCommand?.invoke(command)
        controller.applyCommand(command)
        return true
    }
    Column(modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else if (onShortcut?.invoke(event, controller) == true) {
            true
        } else if (!enableKeyboardShortcuts || !event.isCtrlPressed || event.isAltPressed) {
            false
        } else {
            when {
                event.key == Key.Z -> {
                    if (event.isShiftPressed) controller.redo() else controller.undo()
                    true
                }
                event.key == Key.Y && !event.isShiftPressed -> {
                    controller.redo()
                    true
                }
                event.key == Key.B && !event.isShiftPressed -> applyEditorCommand(MarkdownEditorCommand.BOLD)
                event.key == Key.I && !event.isShiftPressed -> applyEditorCommand(MarkdownEditorCommand.ITALIC)
                event.key == Key.K && !event.isShiftPressed -> applyEditorCommand(MarkdownEditorCommand.LINK)
                event.key == Key.F && !event.isShiftPressed -> {
                    searchOpen = true
                    searchFocusRequest++
                    true
                }
                event.key == Key.Enter && event.isShiftPressed -> {
                    toggleFocusMode()
                    true
                }
                else -> false
            }
        }
    }) {
        if (showToolbar && !focusMode) {
            val defaultToolbar: MarkdownEditorToolbarSlot = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())) {
                            toolbarLeading.forEach { it() }
                            MarkdownEditorMode.entries.forEach { mode ->
                                TextButton(onClick = { requestMode(mode) }) {
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
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { toggleFocusMode() }, modifier = Modifier.testTag("editor-focus-mode")) {
                            Text("Focus mode")
                        }
                        TextButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("editor-find")) {
                            Text(if (searchOpen) "Close find" else "Find")
                        }
                        toolbarTrailing.forEach { it() }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { controller.undo() }, enabled = controller.canUndo) { Text("Undo") }
                        TextButton(onClick = { controller.redo() }, enabled = controller.canRedo) { Text("Redo") }
                        val defaultCommands = listOf(
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
                        )
                        val buttons = toolbarCommands?.map { editorToolbarLabel(it) to it } ?: defaultCommands
                        buttons.filter { capabilities.supports(it.second) }.forEach { (label, command) ->
                            TextButton(onClick = {
                                if (command == MarkdownEditorCommand.IMAGE && onPickImage != null) {
                                    onCommand?.invoke(command)
                                    runHostAction("Image") {
                                        MarkdownEditorHostActions.pickAndInsertImage(controller, onPickImage, onImagePickEvent, onHostActionError)
                                    }
                                } else applyEditorCommand(command)
                            }, enabled = (command != MarkdownEditorCommand.WIKILINK || enableWikilinks) &&
                                (command != MarkdownEditorCommand.IMAGE || !hostActionBusy)) { Text(label) }
                        }
                        if (onPickImage != null && (toolbarCommands == null || MarkdownEditorCommand.IMAGE !in toolbarCommands) &&
                            capabilities.supports(MarkdownEditorCommand.IMAGE)) {
                            TextButton(onClick = {
                                onCommand?.invoke(MarkdownEditorCommand.IMAGE)
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
                }
            }
            if (toolbarBuilder == null) defaultToolbar() else toolbarBuilder(defaultToolbar)
        } else if (showToolbar && focusMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { toggleFocusMode() }, modifier = Modifier.testTag("editor-exit-focus")) {
                    Text("Exit focus")
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
                        if (controller.selectPreviousMatch(searchQuery) != null) requestMode(MarkdownEditorMode.SOURCE)
                    }, enabled = searchMatches.isNotEmpty(), modifier = Modifier.testTag("editor-search-previous")) { Text("Previous") }
                    TextButton(onClick = {
                        if (controller.selectNextMatch(searchQuery) != null) requestMode(MarkdownEditorMode.SOURCE)
                    }, enabled = searchMatches.isNotEmpty(), modifier = Modifier.testTag("editor-search-next")) { Text("Next") }
                }
            }
        }
        if (slashTrigger != null && slashSuggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).testTag("editor-slash-suggestions")) {
                slashSuggestions.forEachIndexed { index, item ->
                    TextButton(
                        onClick = {
                            item.command?.let { command ->
                                if (MarkdownSlashCommands.apply(controller, slashTrigger, command, capabilities)) {
                                    onCommand?.invoke(command)
                                }
                            }
                            item.customCommand?.let { command ->
                                scope.launch { MarkdownSlashCommands.applyCustom(controller, slashTrigger, command) }
                            }
                        },
                        modifier = Modifier.testTag("editor-slash-suggestion-$index"),
                    ) { Text(item.title) }
                }
            }
        }
        if (hostStatus.isNotEmpty()) Text(hostStatus, modifier = Modifier.testTag("editor-host-status"))
        Spacer(Modifier.height(8.dp))
        when (controller.mode) {
            MarkdownEditorMode.SOURCE -> SourcePane(controller, Modifier.weight(1f)) { focused ->
                sourceFocus.setFocused(focused, latestOnFocusChanged.value)
            }
            MarkdownEditorMode.PREVIEW -> SmoothMarkdown(controller.text, Modifier.weight(1f),
                plugins = previewPlugins, onWikilinkClick = onTapWikilink)
            MarkdownEditorMode.SPLIT -> Row(Modifier.weight(1f)) {
                SourcePane(controller, Modifier.weight(1f)) { focused ->
                    sourceFocus.setFocused(focused, latestOnFocusChanged.value)
                }
                SmoothMarkdown(controller.text, Modifier.weight(1f),
                    plugins = previewPlugins, onWikilinkClick = onTapWikilink)
            }
            MarkdownEditorMode.FORMATTED -> FormattedBlockPane(
                controller, Modifier.weight(1f), wikilinkSuggestions,
                customBlockMatcher, customBlockBuilder, customBlockEditorBuilder,
            )
        }
    }
}

private fun editorToolbarLabel(command: MarkdownEditorCommand): String = when (command) {
    MarkdownEditorCommand.BOLD -> "B"
    MarkdownEditorCommand.ITALIC -> "I"
    MarkdownEditorCommand.INLINE_CODE -> "Inline code"
    MarkdownEditorCommand.CODE_BLOCK -> "Code"
    MarkdownEditorCommand.UNORDERED_LIST -> "List"
    MarkdownEditorCommand.TASK_LIST -> "Task"
    else -> command.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
}

@Composable
private fun SourcePane(controller: MarkdownEditorController, modifier: Modifier, onFocusChanged: (Boolean) -> Unit) {
    DisposableEffect(controller) { onDispose { onFocusChanged(false) } }
    BasicTextField(
        value = controller.value,
        onValueChange = controller::updateFromInput,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) }.testTag("editor-source-input"),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

/** Paragraphs, ATX headings, fenced code, and GFM tables expose source-backed content. */
@Composable
private fun FormattedBlockPane(
    controller: MarkdownEditorController,
    modifier: Modifier,
    wikilinkSuggestions: List<String>,
    customBlockMatcher: ((MarkdownDocumentBlock) -> Boolean)?,
    customBlockBuilder: MarkdownEditorCustomBlockBuilder?,
    customBlockEditorBuilder: MarkdownEditorCustomBlockEditorBuilder?,
) {
    val blocks = controller.semanticDocument().blocks
    val pendingExit = controller.pendingListExit
    val clipboard = LocalClipboardManager.current
    val blockSelection = controller.formattedBlockSelection?.takeIf { it.source == controller.text }
    val listSelection = controller.formattedListItemSelection?.takeIf { it.source == controller.text }
    val tableSelection = controller.formattedTableCellSelection?.takeIf { it.source == controller.text }
    val dragSelection = remember(controller, controller.text) { FormattedDragSelection(controller) }
    var blockReplacement by remember(controller) { mutableStateOf("") }
    var tableReplacement by remember(controller) { mutableStateOf("") }
    var blockSelectionError by remember(controller) { mutableStateOf(false) }
    var activeCustomBlock by remember(controller) { mutableStateOf<Pair<String, String>?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        var pendingRendered = false
        blocks.forEach { block ->
            if (!pendingRendered && pendingExit != null && pendingExit.offset < block.range.min) {
                PendingEmptyParagraphField(controller, Modifier.fillMaxWidth().padding(bottom = 10.dp))
                pendingRendered = true
            }
            if (pendingExit != null && pendingExit.offset > block.range.min && pendingExit.offset < block.range.max &&
                block.kind in setOf(MarkdownBlockKind.BULLET_LIST, MarkdownBlockKind.ORDERED_LIST)) pendingRendered = true
            val custom = customBlockMatcher?.invoke(block) == true &&
                (customBlockBuilder != null || customBlockEditorBuilder != null)
            if (custom) {
                val sourceSnapshot = controller.text
                val replace: (String) -> Boolean = { markdown ->
                    controller.replaceCustomBlockMarkdown(sourceSnapshot, block, markdown).also { changed ->
                        if (changed) activeCustomBlock = null
                    }
                }
                val delete: () -> Boolean = { replace("") }
                val edit: () -> Unit = { activeCustomBlock = block.id to sourceSnapshot }
                val active = activeCustomBlock == (block.id to sourceSnapshot)
                val plainText = MarkdownFormattedBlock.text(block) ?: block.source
                if (active && customBlockEditorBuilder != null) {
                    customBlockEditorBuilder(MarkdownEditorCustomBlockEditorContext(
                        block.id, block.kind, block.source, plainText,
                        replace, { activeCustomBlock = null }, delete,
                    ))
                } else if (customBlockBuilder != null) {
                    customBlockBuilder(MarkdownEditorCustomBlockContext(
                        block.id, block.kind, block.source, plainText, edit, replace, delete,
                    ))
                } else {
                    Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), tonalElevation = 1.dp) {
                        Row(Modifier.padding(12.dp)) {
                            Text(block.source, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                            TextButton(onClick = edit) { Text("Edit custom") }
                        }
                    }
                }
            } else {
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
                        if (table != null) FormattedTable(controller, block.id, table, dragSelection)
                        else Text(block.source, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    } else if (block.kind == MarkdownBlockKind.BULLET_LIST || block.kind == MarkdownBlockKind.ORDERED_LIST) {
                        val list = MarkdownSourceList.parse(block)
                        if (list != null) FormattedList(controller, block.id, list, dragSelection)
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
                        val dragModifier = if (block.kind == MarkdownBlockKind.PARAGRAPH || block.kind == MarkdownBlockKind.HEADING)
                            Modifier.formattedDragSelectionTarget(dragSelection, FormattedDragTarget.Block(block.id))
                                .testTag("formatted-block-drag-${block.id}") else Modifier
                        BasicTextField(
                            value = TextFieldValue(inline?.annotated(MaterialTheme.colorScheme.primary) ?: androidx.compose.ui.text.AnnotatedString(editableText), fieldSelection, fieldComposition),
                            onValueChange = { next ->
                                selectedSuggestion = 0
                                dismissedQuery = null
                                if (inline != null) {
                                    if (next.text == inline.visible || next.composition != null ||
                                        !controller.replaceFormattedTextWithBlocks(block.id, next.text)) {
                                        controller.setFormattedSelection(block.id, next.selection, next.composition)
                                        if (next.text != inline.visible) {
                                            controller.replaceFormattedInlineText(block.id, next.text, next.selection, next.composition)
                                        }
                                    }
                                } else {
                                    controller.replaceFormattedBlockText(block.id, next.text)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).then(dragModifier)
                                .focusRequester(blockFocusRequester).onPreviewKeyEvent { event ->
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
        }
        if (pendingExit != null && !pendingRendered) {
            PendingEmptyParagraphField(controller, Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
        if (blockSelection != null) {
            Text("${blockSelection.lastIndex - blockSelection.firstIndex + 1} block(s) selected", modifier = Modifier.testTag("formatted-block-selection-count"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    controller.copyFormattedBlockSelectionAsMarkdown()?.let { clipboard.setText(AnnotatedString(it)) }
                }, modifier = Modifier.testTag("formatted-block-copy")) { Text("Copy Markdown") }
                TextButton(onClick = {
                    blockSelectionError = !controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.BOLD)
                }, modifier = Modifier.testTag("formatted-block-bold")) { Text("Bold") }
                TextButton(onClick = {
                    blockSelectionError = !controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.ITALIC)
                }, modifier = Modifier.testTag("formatted-block-italic")) { Text("Italic") }
                TextButton(onClick = {
                    blockSelectionError = !controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.STRIKETHROUGH)
                }, modifier = Modifier.testTag("formatted-block-strikethrough")) { Text("Strike") }
                TextButton(onClick = {
                    blockSelectionError = !controller.applyInlineCommandToFormattedBlockSelection(MarkdownEditorCommand.INLINE_CODE)
                }, modifier = Modifier.testTag("formatted-block-inline-code")) { Text("Code") }
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
            if (blockSelectionError) Text("Cannot preserve this selection's Markdown structure", modifier = Modifier.testTag("formatted-block-edit-error"))
        }
        if (listSelection != null) {
            Text("${listSelection.lastIndex - listSelection.firstIndex + 1} list item(s) selected",
                modifier = Modifier.testTag("formatted-list-selection-count"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    controller.copyFormattedListItemSelectionAsMarkdown()?.let { clipboard.setText(AnnotatedString(it)) }
                }, modifier = Modifier.testTag("formatted-list-selection-copy")) { Text("Copy Markdown") }
                TextButton(onClick = { controller.deleteFormattedListItemSelection() },
                    modifier = Modifier.testTag("formatted-list-selection-delete")) { Text("Delete items") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.BOLD) },
                    modifier = Modifier.testTag("formatted-list-selection-bold")) { Text("Bold") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.ITALIC) },
                    modifier = Modifier.testTag("formatted-list-selection-italic")) { Text("Italic") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.STRIKETHROUGH) },
                    modifier = Modifier.testTag("formatted-list-selection-strikethrough")) { Text("Strike") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedListItemSelection(MarkdownEditorCommand.INLINE_CODE) },
                    modifier = Modifier.testTag("formatted-list-selection-inline-code")) { Text("Code") }
                TextButton(onClick = controller::clearFormattedListItemSelection,
                    modifier = Modifier.testTag("formatted-list-selection-clear")) { Text("Clear") }
            }
        }
        if (tableSelection != null) {
            Text("${tableSelection.lastRow - tableSelection.firstRow + 1} × ${tableSelection.lastColumn - tableSelection.firstColumn + 1} cells selected",
                modifier = Modifier.testTag("formatted-table-selection-count"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    controller.copyFormattedTableCellSelectionAsTsv()?.let { clipboard.setText(AnnotatedString(it)) }
                }, modifier = Modifier.testTag("formatted-table-selection-copy")) { Text("Copy TSV") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.BOLD) },
                    modifier = Modifier.testTag("formatted-table-selection-bold")) { Text("Bold") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.ITALIC) },
                    modifier = Modifier.testTag("formatted-table-selection-italic")) { Text("Italic") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.STRIKETHROUGH) },
                    modifier = Modifier.testTag("formatted-table-selection-strikethrough")) { Text("Strike") }
                TextButton(onClick = { controller.applyInlineCommandToFormattedTableCellSelection(MarkdownEditorCommand.INLINE_CODE) },
                    modifier = Modifier.testTag("formatted-table-selection-inline-code")) { Text("Code") }
                TextButton(onClick = { controller.clearFormattedTableCellSelection() },
                    modifier = Modifier.testTag("formatted-table-selection-delete")) { Text("Clear cells") }
                TextButton(onClick = {
                    if (controller.replaceFormattedTableCellSelectionFromTsv(tableReplacement)) tableReplacement = ""
                }, modifier = Modifier.testTag("formatted-table-selection-replace")) { Text("Replace TSV") }
                TextButton(onClick = controller::resetFormattedTableCellSelection,
                    modifier = Modifier.testTag("formatted-table-selection-clear")) { Text("Clear selection") }
            }
            OutlinedTextField(value = tableReplacement, onValueChange = { tableReplacement = it },
                label = { Text("Replacement TSV") },
                modifier = Modifier.fillMaxWidth().testTag("formatted-table-replacement"))
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
private fun FormattedList(controller: MarkdownEditorController, blockId: String, list: MarkdownSourceList,
                          dragSelection: FormattedDragSelection) {
    val block = controller.semanticDocument().blockById(blockId)
    val pending = controller.pendingListExit
    val showPending = block != null && pending != null && pending.offset > block.range.min && pending.offset < block.range.max
    FormattedListItems(controller, blockId, list, list.items, emptyList(), 0, 0, showPending, dragSelection)
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
    dragSelection: FormattedDragSelection,
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
                Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(8) * 20).dp)
                    .background(if (controller.formattedListItemSelection?.let {
                        it.source == controller.text && it.blockId == blockId &&
                            path.size > it.parentPath.size && path.take(it.parentPath.size) == it.parentPath &&
                            path[it.parentPath.size] in it.firstIndex..it.lastIndex
                    } == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .formattedDragSelectionTarget(dragSelection, FormattedDragTarget.ListItem(blockId, path))
                    .testTag("formatted-list-drag-$blockId-$pathTag"),
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
                TextButton(
                    onClick = { controller.selectFormattedListItem(blockId, path) },
                    modifier = Modifier.testTag("formatted-list-select-$blockId-$pathTag"),
                ) { Text("Select") }
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
                        FormattedListItems(controller, blockId, list, part.items, path, depth + 1, nestedBase,
                            dragSelection = dragSelection)
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
            } else if (controller.replaceFormattedListLineWithBlocks(blockId, path, lineIndex, next.text)) {
                // The pasted child list replaces this field; its last item receives focus.
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
private fun FormattedTable(controller: MarkdownEditorController, blockId: String, table: MarkdownSourceTable,
                           dragSelection: FormattedDragSelection) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        fun displayCell(raw: String) = raw.replace("\\|", "|")
        @Composable fun cell(raw: String, header: Boolean, rowIndex: Int, columnIndex: Int) {
            val selectedRow = if (header) 0 else rowIndex + 1
            val selected = controller.formattedTableCellSelection?.let {
                it.source == controller.text && it.blockId == blockId &&
                    selectedRow in it.firstRow..it.lastRow && columnIndex in it.firstColumn..it.lastColumn
            } == true
            Column(Modifier.width(140.dp).padding(4.dp)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .formattedDragSelectionTarget(dragSelection, FormattedDragTarget.TableCell(blockId, selectedRow, columnIndex))
                .testTag("formatted-table-drag-$blockId-$selectedRow-$columnIndex")) {
                TextButton(
                    onClick = { controller.selectFormattedTableCell(blockId, if (header) 0 else rowIndex + 1, columnIndex) },
                    modifier = Modifier.testTag("formatted-table-select-$blockId-${if (header) 0 else rowIndex + 1}-$columnIndex"),
                ) { Text("Select") }
                BasicTextField(
                    value = displayCell(raw),
                    onValueChange = { next ->
                        controller.editSemanticTable(blockId) { it.replaceCell(rowIndex, columnIndex, next, header) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
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
