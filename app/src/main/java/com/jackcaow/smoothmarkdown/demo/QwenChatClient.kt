package com.jackcaow.smoothmarkdown.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

/** Frames SSE by lines, so TCP and UTF-8 chunk boundaries cannot split a JSON event. */
internal class QwenSseFramer {
    private val dataLines = mutableListOf<String>()

    fun acceptLine(line: String): String? {
        if (line.isEmpty()) return flush()
        if (line.startsWith("data:")) dataLines += line.substring(5).removePrefix(" ")
        return null
    }

    fun flush(): String? = if (dataLines.isEmpty()) null else dataLines.joinToString("\n").also {
        dataLines.clear()
    }
}

/** Matches Flutter's reasoning_content to <thinking> stream conversion. */
internal class QwenDeltaFormatter {
    private var inThinking = false

    fun accept(reasoning: String?, content: String?): String = buildString {
        if (!reasoning.isNullOrEmpty()) {
            if (!inThinking) {
                inThinking = true
                append("<thinking>\n")
            }
            append(reasoning)
        }
        if (!content.isNullOrEmpty()) {
            if (inThinking) append(closeThinking())
            append(content)
        }
    }

    fun finish(): String = if (inThinking) closeThinking() else ""

    private fun closeThinking(): String {
        inThinking = false
        return "\n</thinking>\n\n"
    }
}

internal class QwenHttpException(val statusCode: Int) : IOException("API Error: $statusCode")

/** The Flutter example's single-prompt DashScope compatible-mode streaming request. */
internal class QwenChatClient {
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    suspend fun stream(
        prompt: String,
        apiKey: String,
        model: String,
        enableThinking: Boolean,
        onChunk: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
        }
        activeConnection.set(connection)
        try {
            currentCoroutineContext().ensureActive()
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
                put("stream", true)
                if (enableThinking && model.startsWith("qwen3")) {
                    put("enable_thinking", true)
                    put("thinking_budget", 10_000)
                }
            }.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw QwenHttpException(connection.responseCode)
            }

            val framer = QwenSseFramer()
            val formatter = QwenDeltaFormatter()
            var done = false
            suspend fun consume(data: String?) {
                if (data == null) return
                if (data == "[DONE]") {
                    done = true
                    return
                }
                val delta = runCatching {
                    JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                }.getOrNull() ?: return
                val chunk = formatter.accept(
                    delta.opt("reasoning_content") as? String,
                    delta.opt("content") as? String,
                )
                if (chunk.isNotEmpty()) onChunk(chunk)
            }
            connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                    while (!done) {
                        currentCoroutineContext().ensureActive()
                        val line = reader.readLine() ?: break
                        consume(framer.acceptLine(line))
                    }
                }
            }
            if (!done) consume(framer.flush())
            formatter.finish().takeIf(String::isNotEmpty)?.let { onChunk(it) }
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        const val SYSTEM_PROMPT = """你是一个 AI 助手。在回答时，请适当使用以下格式：

1. 使用 <artifact identifier="id" type="code" language="lang" title="title">...</artifact> 包裹代码制品
2. 使用标准 Markdown 格式

请确保回答内容丰富、格式清晰。"""
    }
}
