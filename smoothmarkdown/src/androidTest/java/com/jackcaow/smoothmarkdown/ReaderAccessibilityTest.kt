package com.jackcaow.smoothmarkdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Rule
import org.junit.Test

class ReaderAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headingAndPlainTextHaveCorrectSemantics() {
        compose.setContent { MaterialTheme { SmoothMarkdown("# Chapter\n\nOrdinary paragraph") } }
        compose.onNodeWithText("Chapter").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Ordinary paragraph")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test fun linksExposeNamedActionWithoutMakingPlainTextClickable() {
        var opened: String? = null
        compose.setContent {
            MaterialTheme { SmoothMarkdown("Read [the guide](https://example.com) here.", onLinkClick = { opened = it }) }
        }
        compose.onNodeWithText("Read the guide here.").assert(
            SemanticsMatcher("has named link action") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions)
                    ?.any { it.label == "Open link the guide" && it.action() } == true
            },
        )
        compose.runOnIdle { check(opened == "https://example.com") }
    }

    @Test fun listAndTableExposeCollectionCoordinates() {
        compose.setContent {
            MaterialTheme { SmoothMarkdown("- First\n- Second\n\n| Name | Value |\n| --- | --- |\n| One | 1 |") }
        }
        compose.onNode(SemanticsMatcher("first list item") {
            it.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.rowIndex == 0 &&
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.contains("First") } == true
        }).assertExists()
        compose.onNodeWithText("One").assert(
            SemanticsMatcher("table body coordinates") {
                it.config.getOrNull(SemanticsProperties.CollectionItemInfo)?.let { info ->
                    info.rowIndex == 1 && info.columnIndex == 0
                } == true
            },
        )
    }

    @Test fun detailsButtonNamesActionAndUpdatesState() {
        compose.setContent {
            MaterialTheme { SmoothMarkdown("<details>\n<summary>More</summary>\nHidden body\n</details>") }
        }
        compose.onNode(SemanticsMatcher("collapsed details") {
            it.config.getOrNull(SemanticsProperties.StateDescription) == "Collapsed"
        }).assertHasClickAction().assert(
            SemanticsMatcher("expand label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Expand details" },
        ).performClick()
        compose.onNodeWithText("Hidden body").assertIsDisplayed()
    }

    @Test fun mermaidCanvasHasSpokenSummary() {
        val plugins = ParserPluginRegistry().also { it.register(MermaidPlugin()) }
        compose.setContent {
            MaterialTheme { SmoothMarkdown("```mermaid\ngraph LR\nA[Start] --> B[Finish]\n```", plugins = plugins) }
        }
        compose.onNodeWithContentDescription("Flowchart. 2 nodes, 1 connections. Nodes: Start; Finish. Connections: A to B.")
            .assertIsDisplayed()
        compose.onNodeWithText("Start").assertDoesNotExist()
    }

    @Test fun smallImageStillHasMinimumTouchTargetAtLargeFontScale() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                MaterialTheme {
                    SmoothMarkdown("<img src='https://example.com/a.png' alt='Diagram' width='12' height='12'>", enableHtml = true)
                }
            }
        }
        val image = compose.onNode(SemanticsMatcher("open image button") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open image"
        })
        image.assertHasClickAction()
        val bounds = image.getUnclippedBoundsInRoot()
        check((bounds.right - bounds.left).value >= 48f && (bounds.bottom - bounds.top).value >= 48f) { "Image target is $bounds" }
    }

    @Test fun footnoteAndCodeRemainReadableAndCopyIsClickable() {
        compose.setContent {
            MaterialTheme { SmoothMarkdown("A note[^x].\n\n[^x]: Footnote body\n\n```kotlin\nval x = 1\n```",
                useEnhancedComponents = true) }
        }
        compose.onNodeWithText("A note[x].").assertExists()
        compose.onNodeWithText("Footnote body").assertExists()
        compose.onNodeWithText("Copy").assertHasClickAction()
        compose.onNodeWithText("val x = 1", substring = true).assertExists()
    }
}
