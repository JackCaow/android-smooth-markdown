package com.jackcaow.smoothmarkdown.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jackcaow.smoothmarkdown.mermaid.MermaidDiagramView

/** Directly launchable prototype: adb shell am start -n com.jackcaow.smoothmarkdown.demo/.MermaidDemoActivity */
class MermaidDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var exampleIndex by remember { mutableStateOf(0) }
            val examples = listOf(
                "Flowchart" to flowExample,
                "Sequence" to sequenceExample,
                "Pie" to pieExample,
                "Timeline" to timelineExample,
                "Gantt" to ganttExample,
                "Kanban" to kanbanExample,
            )
            MaterialTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TextButton(onClick = { exampleIndex = (exampleIndex + 1) % examples.size }) {
                        Text("${examples[exampleIndex].first} · Next diagram")
                    }
                    MermaidDiagramView(examples[exampleIndex].second, Modifier.weight(1f))
                }
            }
        }
    }
}

private val flowExample = """
    flowchart TD
    A[开始] --> B{判断}
    B -->|是| C[处理]
    B -->|否| D[跳过]
    C --> E[结束]
    D --> E
    classDef done fill:#d9f7be,stroke:#389e0d
    class E done
""".trimIndent()

private val sequenceExample = """
    sequenceDiagram
    participant A as Alice
    actor B as Bob
    A->>B: Hello
    B-->>A: Hi
    A-)B: Async work
""".trimIndent()

private val pieExample = """
    pie showData
    title Favorite Pets
    "Dogs" : 386
    "Cats" : 85
    "Rats" : 15
""".trimIndent()

private val timelineExample = """
    timeline
    title Product Releases
    Q1 2024 : First preview
            : Feedback
    Q2 2024 : Public beta
    Q3 2024 : Launch
""".trimIndent()

private val ganttExample = """
    gantt
    title Development Timeline
    dateFormat YYYY-MM-DD
    section Planning
    Requirements :done, req, 2024-01-01, 12d
    Design :active, design, after req, 10d
    section Build
    API :crit, api, 2024-01-19, 18d
    Release :milestone, rel, after api, 0d
""".trimIndent()

private val kanbanExample = """
    kanban
      title Product Board
      backlog[Backlog] wip:2
        task1[User authentication] @{ assigned: "Alice", ticket: "APP-101", priority: "High" }
        task2[Database design] @{ assigned: "Bob" }
      doing[In Progress] wip:1
        task3[Dashboard] @{ assigned: "Charlie", priority: "Very High" }
        task4[API integration] @{ ticket: "APP-104" }
      done[Done]
        task5[CI pipeline] @{ assigned: "Alice", priority: "Low" }
""".trimIndent()
