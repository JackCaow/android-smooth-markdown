package com.jackcaow.smoothmarkdown.demo

import com.jackcaow.smoothmarkdown.mermaid.MermaidKind
import com.jackcaow.smoothmarkdown.mermaid.MermaidLayout
import com.jackcaow.smoothmarkdown.mermaid.MermaidParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Exercises the actual source files shown in the Demo against the native parser and layout. */
class MermaidGalleryNativeLayoutTest {
    @Test fun allFlutterGallerySourcesHaveDrawableNativeLayouts() {
        val fixtureDir = sequenceOf(
            File("src/main/assets/examples/mermaid"),
            File("app/src/main/assets/examples/mermaid"),
        ).firstOrNull(File::isDirectory) ?: error("Mermaid gallery assets are missing")
        val files = (1..40).map { number ->
            File(fixtureDir, "mermaid-%02d.mmd".format(number)).also {
                assertTrue("Missing Mermaid example $number", it.isFile)
            }
        }
        val failures = mutableListOf<String>()
        files.forEachIndexed { position, file ->
            val number = position + 1
            val source = file.readText()
            try {
                val diagram = MermaidParser.parse(source)
                    ?: error("native parser returned null (source fallback)")
                val expectedKind = when (number) {
                    in 1..7, in 31..33 -> MermaidKind.Flowchart
                    in 8..11 -> MermaidKind.Sequence
                    in 12..15 -> MermaidKind.Pie
                    in 16..19 -> MermaidKind.Gantt
                    in 20..24 -> MermaidKind.Timeline
                    in 25..30 -> MermaidKind.Kanban
                    in 34..36 -> MermaidKind.Radar
                    else -> MermaidKind.XYChart
                }
                check(diagram.kind == expectedKind) { "expected $expectedKind, got ${diagram.kind}" }
                val layout = MermaidLayout.compute(diagram)
                println("Mermaid gallery %02d: %s, %d nodes, %d edges, %.0f x %.0f".format(
                    number, diagram.kind, diagram.nodes.size, diagram.edges.size, layout.width, layout.height))
                check(layout.width.isFinite() && layout.height.isFinite() &&
                    layout.width > 0f && layout.height > 0f) {
                    "invalid layout size ${layout.width} x ${layout.height}"
                }
                // These gallery diagrams contain loops. The closing arrow stays visible,
                // while the main path should still follow the declared direction.
                when (number) {
                    2 -> check(layout.nodes.getValue("A").x < layout.nodes.getValue("E").x) {
                        "LR loop collapsed instead of progressing from A to E"
                    }
                    5 -> check(layout.nodes.getValue("Start").y < layout.nodes.getValue("End").y) {
                        "TD loop collapsed instead of progressing from Start to End"
                    }
                    31 -> check(layout.nodes.getValue("A").x < layout.nodes.getValue("I").x) {
                        "LR Git workflow loop collapsed instead of progressing from A to I"
                    }
                }
                when (diagram.kind) {
                    MermaidKind.Flowchart, MermaidKind.Sequence -> {
                        check(diagram.nodes.isNotEmpty() && layout.nodes.keys.containsAll(diagram.nodes.map { it.id })) {
                            "missing node layout: ${diagram.nodes.map { it.id } - layout.nodes.keys}"
                        }
                        check(layout.edges.size == diagram.edges.size) {
                            "placed ${layout.edges.size}/${diagram.edges.size} edges"
                        }
                        check(layout.subgraphs.keys.containsAll(diagram.subgraphs.map { it.id })) {
                            "missing subgraph layout"
                        }
                    }
                    MermaidKind.Pie -> check(layout.pie?.slices?.size == diagram.pie?.slices?.size &&
                        diagram.pie!!.slices.isNotEmpty()) { "missing pie slices" }
                    MermaidKind.Gantt -> check(layout.gantt?.tasks?.size == diagram.gantt?.tasks?.size &&
                        diagram.gantt!!.tasks.isNotEmpty()) { "missing Gantt tasks" }
                    MermaidKind.Timeline -> check(layout.timeline?.sections?.size == diagram.timeline?.sections?.size &&
                        diagram.timeline!!.sections.isNotEmpty()) { "missing timeline sections" }
                    MermaidKind.Kanban -> check(layout.kanban?.columns?.size == diagram.kanban?.columns?.size &&
                        diagram.kanban!!.columns.isNotEmpty()) { "missing Kanban columns" }
                    MermaidKind.Radar -> check(layout.radar?.curves?.size == diagram.radar?.curves?.size &&
                        diagram.radar!!.curves.isNotEmpty()) { "missing radar curves" }
                    MermaidKind.XYChart -> check(layout.xyChart?.let { it.bars.isNotEmpty() || it.lines.isNotEmpty() } == true) {
                        "empty XY layout"
                    }
                    else -> error("Unexpected gallery kind ${diagram.kind}")
                }
            } catch (error: Throwable) {
                failures += "%02d: %s: %s".format(number, error::class.simpleName, error.message)
            }
        }
        assertTrue("Native Mermaid gallery failures (${failures.size}/40):\n${failures.joinToString("\n")}",
            failures.isEmpty())
    }
}
