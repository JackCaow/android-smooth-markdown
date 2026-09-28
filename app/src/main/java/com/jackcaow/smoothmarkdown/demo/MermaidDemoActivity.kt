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
