package com.jackcaow.smoothmarkdown.demo

import android.content.res.AssetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.StreamMarkdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.security.MessageDigest

data class StreamingDemoFixture(val chunks: List<String>, val delayMillis: Long)

/** The 48 chunks and 50 ms interval come from Flutter example/lib/streaming_demo.dart. */
fun loadStreamingDemoFixture(assets: AssetManager): StreamingDemoFixture {
    val fixture = JSONObject(assets.open("examples/streaming/streaming.json").bufferedReader().use { it.readText() })
    val entries = fixture.getJSONArray("chunks")
    val chunks = (0 until entries.length()).map(entries::getString)
    val markdown = chunks.joinToString("")
    val hash = MessageDigest.getInstance("SHA-256").digest(markdown.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val delayMillis = fixture.getLong("delayMillis")
    require(chunks.size == 48 && delayMillis == 50L && hash == fixture.getString("markdownSha256")) {
        "Flutter streaming demo fixture mismatch"
    }
    return StreamingDemoFixture(chunks, delayMillis)
}

private enum class StreamPhase { Idle, Streaming, Complete }

@Composable
fun StreamingDemo(
    fixture: StreamingDemoFixture,
    styleSheet: MarkdownStyleSheet,
    plugins: ParserPluginRegistry,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableStateOf(StreamPhase.Idle) }
    var generation by remember { mutableIntStateOf(0) }
    var chunkCount by remember { mutableIntStateOf(0) }
    val chunks = remember(generation) {
        flow {
            fixture.chunks.forEachIndexed { index, chunk ->
                emit(chunk)
                chunkCount = index + 1
                delay(fixture.delayMillis)
            }
            phase = StreamPhase.Complete
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = { chunkCount = 0; generation++; phase = StreamPhase.Streaming },
                enabled = phase == StreamPhase.Idle,
                modifier = Modifier.weight(1f).testTag("stream-start"),
            ) {
                Text("▶ ")
                Text("Start Stream")
            }
            OutlinedButton(
                onClick = { phase = StreamPhase.Idle; chunkCount = 0; generation++ },
                enabled = phase != StreamPhase.Idle,
                modifier = Modifier.weight(1f).testTag("stream-reset"),
            ) {
                Text("■ ")
                Text("Reset")
            }
        }
        when (phase) {
            StreamPhase.Streaming -> LinearProgressIndicator(Modifier.fillMaxWidth().testTag("stream-progress"))
            StreamPhase.Complete -> Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0xFF4CAF50))
                .testTag("stream-complete-bar"))
            StreamPhase.Idle -> Unit
        }
        if (phase != StreamPhase.Idle) {
            Text(
                text = if (phase == StreamPhase.Complete) "Complete · $chunkCount/${fixture.chunks.size} chunks"
                else "$chunkCount/${fixture.chunks.size} chunks",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("stream-status"),
            )
        }
        if (phase == StreamPhase.Idle) {
            Column(
                Modifier.weight(1f).fillMaxWidth().testTag("stream-empty"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("↝", style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Click \"Start Stream\" to begin", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Watch as Markdown renders in real-time", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            StreamMarkdown(
                chunks = chunks,
                modifier = Modifier.weight(1f).testTag("stream-markdown"),
                onLinkClick = onLinkClick,
                styleSheet = styleSheet,
                plugins = plugins,
                useEnhancedComponents = true,
            )
        }
    }
}
