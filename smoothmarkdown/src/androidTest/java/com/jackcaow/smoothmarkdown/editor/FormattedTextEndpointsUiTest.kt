package com.jackcaow.smoothmarkdown.editor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FormattedTextEndpointsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun quoteLineCaretButtonsCopyAndDeleteSourceBackedText() {
        val original = "> BeforeX\n> YAfter\n> Keep"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }

        compose.onNodeWithTag("formatted-quote-line-block-0-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-quote-text-start-block-0-0").performClick()
        compose.onNodeWithTag("formatted-quote-line-block-0-1").performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-quote-text-end-block-0-1").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("> X\n> Y", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()
        compose.runOnIdle {
            assertEquals("> BeforeAfter\n> Keep", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun continuationAndNestedCaretButtonsCopyAndDeleteWithoutTouchingRootSibling() {
        val original = "- root\n  BeforeX\n  - YAfter\n- keep"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("formatted-list-continuation-block-0-0-1").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-list-text-start-block-0-0").performClick()
        compose.onNodeWithTag("formatted-list-item-block-0-0-0").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-list-text-end-block-0-0-0").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("  X\n  - Y", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()
        compose.runOnIdle {
            assertEquals("- root\n  BeforeAfter\n- keep", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun listLineCaretEndpointsCopyAndDeleteAcrossCodeBlock() {
        val original = "- BeforeX\n- second\n\n```js\nconst n = 1\n```\n\nRightTail\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }

        compose.onNodeWithTag("formatted-list-item-block-0-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-list-text-start-block-0-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-2").performScrollTo().performClick()
            .performTextInputSelection(TextRange(5))
        compose.onNodeWithTag("formatted-text-end-block-2").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle {
            assertEquals("- X\n- second\n\n```js\nconst n = 1\n```\n\nRight", copied?.text)
        }
        compose.onNodeWithTag("formatted-text-delete").performClick()
        compose.runOnIdle {
            assertEquals("- BeforeTail\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun caretEndpointsAcrossParagraphAndHeadingDeleteOnlySelectedCharactersWithOneUndo() {
        val original = "BeforeX\n\n# YAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }

        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-1").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-1").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("X\n\n# Y", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()

        compose.runOnIdle {
            assertEquals("BeforeAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }

    @Test fun caretEndpointsReplaceRangeWithoutChangingOutsideBlocks() {
        val original = "BeforeX\n\nYAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        compose.setContent { MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) } }

        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-1").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-1").performClick()
        compose.onNodeWithTag("formatted-text-replacement").performScrollTo().performTextInput("# Inserted")
        compose.onNodeWithTag("formatted-text-replace").performClick()

        compose.runOnIdle {
            assertEquals("Before\n\n# Inserted\n\nAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun caretEndpointsPasteMarkdownBlocksFromClipboard() {
        val original = "BeforeX\n\nYAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString = AnnotatedString("# Inserted\n\n- one\n- two")
            override fun setText(annotatedString: AnnotatedString) = Unit
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-1").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-1").performClick()
        compose.onNodeWithTag("formatted-text-paste-blocks").performClick()
        compose.runOnIdle {
            assertEquals("Before\n\n# Inserted\n\n- one\n- two\n\nAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
        }
    }

    @Test fun caretEndpointsAcrossCodeFenceCopyAndDeleteItsCompleteMarkdown() {
        val original = "BeforeX\n\n```js\nconst n = 1\n```\n\n# YAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }

        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-2").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-2").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("X\n\n```js\nconst n = 1\n```\n\n# Y", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()
        compose.runOnIdle {
            assertEquals("BeforeAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }
    @Test fun caretInsideCodeBodyCanSelectThroughFollowingProse() {
        val original = "```js\nBeforeX\n```\n\nYAfter\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }

        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-block-drag-block-1").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-text-end-block-1").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("```js\nX\n```\n\nY", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()
        compose.runOnIdle {
            assertEquals("```js\nBefore\n```\n\nAfter\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }

    @Test fun proseToFirstTableCellCaretEndpointsCopyDeleteAndUndo() {
        val original = "BeforeX\n\n| YAfter | H |\n| --- | --- |\n| A | B |\n\nOutside"
        val controller = MarkdownEditorController(original).also { it.mode = MarkdownEditorMode.FORMATTED }
        var copied: AnnotatedString? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                MaterialTheme { SmoothMarkdownEditor(controller, Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("formatted-block-drag-block-0").performClick()
            .performTextInputSelection(TextRange(6))
        compose.onNodeWithTag("formatted-text-start-block-0").performClick()
        compose.onNodeWithTag("formatted-table-text-block-1-0-0").performScrollTo().performClick()
            .performTextInputSelection(TextRange(1))
        compose.onNodeWithTag("formatted-table-text-end-block-1-0-0").performClick()
        compose.onNodeWithTag("formatted-text-selection-status").performScrollTo()
        compose.onNodeWithTag("formatted-text-copy").performClick()
        compose.runOnIdle { assertEquals("X\n\n| Y", copied?.text) }
        compose.onNodeWithTag("formatted-text-delete").performClick()
        compose.runOnIdle {
            assertEquals("Before\n\n| After | H |\n| --- | --- |\n| A | B |\n\nOutside", controller.text)
            check(controller.undo())
            assertEquals(original, controller.text)
            check(!controller.canUndo)
        }
    }

}
