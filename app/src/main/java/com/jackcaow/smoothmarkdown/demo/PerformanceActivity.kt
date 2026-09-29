package com.jackcaow.smoothmarkdown.demo

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.StreamMarkdownBuffer
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Repeatable 68,282-byte device fixture. Launch with `--es mode static` or `--es mode stream`. */
class PerformanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startNanos = SystemClock.elapsedRealtimeNanos()
        val fixture = assets.open("performance/flutter-readme.md").bufferedReader().use { it.readText() }
        val document = List(4) { fixture }.joinToString("\n\n")
        require(document.toByteArray(Charsets.UTF_8).size == 68_282)
        val hash = document.sha256()
        val initialMode = if (intent.getStringExtra("mode") == "stream") "stream" else "static"
        Log.i(TAG, "start mode=$initialMode fixtureBytes=${fixture.toByteArray().size} documentBytes=68282 sha256=$hash")
        setContent {
            var mode by remember { mutableStateOf(initialMode) }
            var visible by remember(mode) { mutableStateOf(if (mode == "static") document else "") }
            var publishes by remember(mode) { mutableIntStateOf(0) }
            val firstContentDraw = remember(mode) { AtomicBoolean(false) }
            LaunchedEffect(mode) {
                if (mode == "stream") {
                    val chunks = document.chunked(32)
                    val buffer = StreamMarkdownBuffer(intervalMillis = 50, startMillis = 0)
                    chunks.forEachIndexed { index, chunk ->
                        if (buffer.append(chunk, (index + 1).toLong()) == null) {
                            visible = buffer.visibleText
                            publishes++
                        }
                        delay(1)
                    }
                    buffer.finish(chunks.size.toLong() + 1)
                    visible = buffer.visibleText
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
                    Log.i(TAG, "complete mode=stream elapsedMs=%.2f chunks=${chunks.size} publishes=$publishes ".format(elapsedMs) +
                        "bytes=${visible.toByteArray().size} matches=${visible == document} sha256=${visible.sha256()}")
                } else {
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
                    Log.i(TAG, "complete mode=static elapsedMs=%.2f bytes=${visible.toByteArray().size} matches=${visible == document} sha256=${visible.sha256()}".format(elapsedMs))
                }
            }
            MaterialTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TextButton(onClick = { mode = if (mode == "static") "stream" else "static" }) {
                        Text("Long document: $mode · switch")
                    }
                    Text("${visible.toByteArray().size}/68282 bytes · $publishes publishes · " +
                        if (visible == document) "complete" else "streaming")
                    SmoothMarkdown(
                        markdown = visible,
                        modifier = Modifier.weight(1f).drawWithContent {
                            drawContent()
                            if (visible.isNotEmpty() && firstContentDraw.compareAndSet(false, true)) {
                                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
                                Log.i(TAG, "firstContentDraw mode=$mode elapsedMs=%.2f visibleBytes=${visible.toByteArray().size}".format(elapsedMs))
                            }
                        },
                    )
                }
            }
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object { const val TAG = "SmoothMarkdownPerf" }
}
