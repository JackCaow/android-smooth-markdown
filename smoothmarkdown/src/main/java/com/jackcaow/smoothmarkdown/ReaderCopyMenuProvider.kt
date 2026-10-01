package com.jackcaow.smoothmarkdown

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.graphics.Rect as AndroidRect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

internal data class ReaderSelectionMenuSnapshot(
    val defaultCopyVisible: Boolean,
    val actions: List<SmoothSelectionMenuAction>,
    val selectedText: String,
    val invokeAction: (String) -> Unit,
)

/** Internal observer enables UI tests without depending on private Compose menu providers. */
internal val LocalReaderSelectionMenuObserver = compositionLocalOf<((ReaderSelectionMenuSnapshot?) -> Unit)?> { null }

/** Platform floating selection toolbar with host-provided actions. */
internal class ReaderCopyMenuProvider(private val view: View) {
    private var mode: ActionMode? = null
    private var selectionRevision = 0
    private var dismissedRevision: Int? = null
    private var releaseBackHandler: (() -> Unit)? = null
    private var bounds = Rect.Zero
    private var selection: () -> VisibleSelection = { VisibleSelection("", false) }
    private var copy: (String) -> Unit = {}
    private var actions = emptyList<SmoothSelectionMenuAction>()
    private var showCopy = true
    private var selectAll: () -> Unit = {}
    private var clearSelection: () -> Unit = {}
    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            populate(menu)
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            populate(menu)
            return true
        }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val key = when (item.itemId) {
                android.R.id.copy -> "copy"
                android.R.id.selectAll -> "select-all"
                else -> actions.getOrNull(item.itemId - ACTION_ID_BASE)?.key ?: return false
            }
            if (item.itemId in ACTION_ID_BASE until (ACTION_ID_BASE + actions.size)) {
                actions.getOrNull(item.itemId - ACTION_ID_BASE)?.let { it.onClick(selection().text); hide(suppressUntilSelectionChanges = true) }
            } else invoke(key)
            return true
        }
        override fun onDestroyActionMode(mode: ActionMode) {
            if (this@ReaderCopyMenuProvider.mode === mode) {
                this@ReaderCopyMenuProvider.mode = null
                releaseBackHandler?.invoke()
                releaseBackHandler = null
                clearSelection()
            }
        }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            outRect.set((bounds.left - location[0]).toInt(), (bounds.top - location[1]).toInt(),
                (bounds.right - location[0]).toInt(), (bounds.bottom - location[1]).toInt())
        }
    }
    fun show(bounds: Rect, selection: () -> VisibleSelection, copy: (String) -> Unit,
        actions: List<SmoothSelectionMenuAction>, showCopy: Boolean, selectAll: () -> Unit, clearSelection: () -> Unit, selectionRevision: Int) {
        val menuChanged = this.actions != actions || this.showCopy != showCopy
        this.bounds = bounds
        this.selection = selection
        this.copy = copy
        this.actions = actions
        this.showCopy = showCopy
        this.selectAll = selectAll
        this.clearSelection = clearSelection
        this.selectionRevision = selectionRevision
        if (dismissedRevision == selectionRevision) return
        if (mode == null) {
            mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
            if (mode == null) {
                var context = view.context
                while (context is android.content.ContextWrapper && context !is android.app.Activity && context.baseContext !== context) {
                    context = context.baseContext
                }
                mode = (context as? android.app.Activity)?.window?.decorView?.startActionMode(callback, ActionMode.TYPE_FLOATING)
            }
        } else { if (menuChanged) mode?.invalidate(); mode?.invalidateContentRect() }
        if (releaseBackHandler == null && android.os.Build.VERSION.SDK_INT >= 33) {
            releaseBackHandler = ReaderBackHandler33.register(view) { clearSelection(); hide() }
        }
    }
    fun snapshot(): ReaderSelectionMenuSnapshot = ReaderSelectionMenuSnapshot(showCopy, actions,
        selection().text, ::invoke)
    fun hide(suppressUntilSelectionChanges: Boolean = false) {
        if (suppressUntilSelectionChanges) dismissedRevision = selectionRevision
        val old = mode
        mode = null
        releaseBackHandler?.invoke()
        releaseBackHandler = null
        old?.finish()
    }
    private fun populate(menu: Menu) {
        if (showCopy) menu.add(0, android.R.id.copy, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, android.R.id.selectAll, 1, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        actions.forEachIndexed { index, action ->
            menu.add(0, ACTION_ID_BASE + index, index + 2, action.label).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
    }
    private fun invoke(key: String) {
        when (key) {
            "copy" -> if (showCopy) { copy(selection().text); hide() }
            "select-all" -> selectAll()
            else -> actions.firstOrNull { it.key == key }?.let { it.onClick(selection().text); hide(suppressUntilSelectionChanges = true) }
        }
    }
    private companion object {
        const val ACTION_ID_BASE = 10000
    }
}

/** Compose may use the older Android text toolbar when no context menu provider is installed. */
internal class ReaderCopyTextToolbar(
    private val delegate: TextToolbar,
    private val selection: () -> VisibleSelection,
    private val copyVisibleSelection: (String) -> Unit,
    private val showDefaultCopyAction: Boolean,
) : TextToolbar {
    override val status: TextToolbarStatus get() = delegate.status

    override fun hide() = delegate.hide()

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(
            rect,
            wrappedCopy(onCopyRequested),
            onPasteRequested,
            onCutRequested,
            onSelectAllRequested,
        )
    }

    private fun wrappedCopy(original: (() -> Unit)?): (() -> Unit)? {
        if (!showDefaultCopyAction || original == null) return null
        return {
            val selected = selection()
            if (selected.hadAnchor) copyVisibleSelection(selected.text) else original()
        }
    }
}


/** Register the owned selection with Android's public system Back dispatcher, without an Activity dependency. */
@android.annotation.TargetApi(33)
private object ReaderBackHandler33 {
    fun register(view: View, onBack: () -> Unit): (() -> Unit)? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = android.window.OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        return { dispatcher.unregisterOnBackInvokedCallback(callback) }
    }
}
