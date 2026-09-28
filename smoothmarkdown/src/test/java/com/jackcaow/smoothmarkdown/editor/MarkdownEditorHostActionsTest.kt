package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditorHostActionsTest {
    @Test fun imageUsesSelectedAltAndOneUndoStep() = runBlocking {
        val controller = MarkdownEditorController("Intro")
        controller.setSelection(0, 5)
        val events = mutableListOf<MarkdownEditorImagePickEvent>()

        val result = MarkdownEditorHostActions.pickAndInsertImage(controller,
            picker = { MarkdownEditorImageSelection("assets/local.png", title = "Local asset") },
            onEvent = { events += it })

        assertEquals(MarkdownEditorHostResult.SUCCESS, result)
        assertEquals("![Intro](assets/local.png \"Local asset\")", controller.text)
        assertEquals(listOf(MarkdownEditorImagePickStatus.PICKING, MarkdownEditorImagePickStatus.INSERTED),
            events.map { it.status })
        assertEquals(TextRange(controller.text.length), controller.selection)
        assertTrue(controller.undo())
        assertEquals("Intro", controller.text)
        assertEquals(TextRange(0, 5), controller.selection)
        assertFalse(controller.canUndo)
    }

    @Test fun imageCancellationFailureAndStaleResultPreserveDocument() = runBlocking {
        val controller = MarkdownEditorController("Intro")
        val events = mutableListOf<MarkdownEditorImagePickEvent>()
        assertEquals(MarkdownEditorHostResult.CANCELLED,
            MarkdownEditorHostActions.pickAndInsertImage(controller, { null }, { events += it }))
        assertEquals("Intro", controller.text)
        assertFalse(controller.canUndo)

        val failure = IllegalStateException("upload failed")
        assertEquals(MarkdownEditorHostResult.FAILED,
            MarkdownEditorHostActions.pickAndInsertImage(controller, { throw failure }, { events += it }))
        assertEquals(failure, events.last().error)
        assertEquals(MarkdownEditorHostResult.FAILED,
            MarkdownEditorHostActions.pickAndInsertImage(controller,
                { MarkdownEditorImageSelection("javascript:alert(1)") }, { events += it }))
        assertEquals("Intro", controller.text)
        assertFalse(controller.canUndo)

        assertEquals(MarkdownEditorHostResult.STALE,
            MarkdownEditorHostActions.pickAndInsertImage(controller, {
                controller.setSelection(0)
                MarkdownEditorImageSelection("assets/local.png")
            }, { events += it }))
        assertEquals("Intro", controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun importCancelsOnBlankOrFailureAndInsertsAsOneUndoStep() = runBlocking {
        val controller = MarkdownEditorController("Intro")
        val errors = mutableListOf<Throwable>()
        assertEquals(MarkdownEditorHostResult.CANCELLED,
            MarkdownEditorHostActions.importMarkdown(controller, { "  " }))
        assertEquals(MarkdownEditorHostResult.FAILED,
            MarkdownEditorHostActions.importMarkdown(controller, { error("picker failed") },
                { _, error -> errors += error }))
        assertEquals(1, errors.size)
        assertEquals("Intro", controller.text)
        assertFalse(controller.canUndo)

        assertEquals(MarkdownEditorHostResult.SUCCESS,
            MarkdownEditorHostActions.importMarkdown(controller, { "# Imported\n\nBody" }))
        assertEquals("Intro\n\n# Imported\n\nBody", controller.text)
        assertTrue(controller.undo())
        assertEquals("Intro", controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun exportReceivesExactSnapshotAndNeverMutatesSource() = runBlocking {
        val controller = MarkdownEditorController("# Exact\n\nBody  ")
        var exported = ""
        assertEquals(MarkdownEditorHostResult.SUCCESS,
            MarkdownEditorHostActions.exportMarkdown(controller, { exported = it }))
        assertEquals("# Exact\n\nBody  ", exported)
        assertEquals(MarkdownEditorHostResult.FAILED,
            MarkdownEditorHostActions.exportMarkdown(controller, { error("disk failed") }))
        assertEquals("# Exact\n\nBody  ", controller.text)
        assertFalse(controller.canUndo)
    }

    @Test fun pdfExportReceivesMarkdownAndHtmlWithoutChangingDocument() = runBlocking {
        val controller = MarkdownEditorController("# Exact\n\nA **bold** note")
        var markdown = ""
        var html = ""
        assertEquals(MarkdownEditorHostResult.SUCCESS,
            MarkdownEditorHostActions.exportPdf(controller, { source, rendered ->
                markdown = source
                html = rendered
            }))
        assertEquals(controller.text, markdown)
        assertTrue(html.contains("<h1>Exact</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertFalse(controller.canUndo)
    }

    @Test fun imageMarkdownEscapesAltTitleAndDestination() {
        assertEquals("![a\\[b\\]](assets/a\\(1\\).png \"A \\\"title\\\"\")",
            MarkdownEditorHostActions.imageMarkdown(
                MarkdownEditorImageSelection("assets/a(1).png", "a[b]", "A \"title\"")))
        listOf("file:///tmp/a.png", "../secret.png", "assets/file:name.png", "assets\\file.png").forEach { url ->
            assertEquals(null, MarkdownEditorHostActions.imageMarkdown(MarkdownEditorImageSelection(url)))
        }
        assertEquals(null, MarkdownEditorHostActions.imageMarkdown(
            MarkdownEditorImageSelection("assets/photo.png", title = "bad\nmetadata")))
    }
}
