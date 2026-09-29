package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/** Preserve the platform Copy label and placement while replacing its action only for anchors. */
internal class ReaderCopyMenuProvider(
    private val delegate: TextContextMenuProvider,
    private val selection: () -> VisibleSelection,
    private val copyVisibleSelection: (String) -> Unit,
) : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        delegate.showTextContextMenu(object : TextContextMenuDataProvider {
            override fun position(destinationCoordinates: LayoutCoordinates): Offset =
                dataProvider.position(destinationCoordinates)

            override fun contentBounds(destinationCoordinates: LayoutCoordinates): Rect =
                dataProvider.contentBounds(destinationCoordinates)

            override fun data(): TextContextMenuData = TextContextMenuData(
                dataProvider.data().components.map { component ->
                    if (component is TextContextMenuItem && component.key == TextContextMenuKeys.CopyKey) {
                        TextContextMenuItem(component.key, component.label, component.leadingIcon) {
                            val selected = selection()
                            if (selected.hadAnchor) {
                                copyVisibleSelection(selected.text)
                                close()
                            } else {
                                component.onClick(this)
                            }
                        }
                    } else component
                },
            )
        })
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

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        delegate.showMenu(rect, wrappedCopy(onCopyRequested), onPasteRequested,
            onCutRequested, onSelectAllRequested, onAutofillRequested)
    }

    private fun wrappedCopy(original: (() -> Unit)?): (() -> Unit)? {
        if (!showDefaultCopyAction || original == null) return null
        return {
            val selected = selection()
            if (selected.hadAnchor) copyVisibleSelection(selected.text) else original()
        }
    }
}
