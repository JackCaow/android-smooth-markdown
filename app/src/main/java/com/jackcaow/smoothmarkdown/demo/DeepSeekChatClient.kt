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

internal class DeepSeekHttpException(val statusCode: Int) : IOException("API Error: $statusCode")

/** Keep this provider's endpoint, model list, and credentials separate from DashScope. */
internal class DeepSeekChatClient(private val endpoint: String = ENDPOINT) {
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
        require(model in MODELS) { "Unsupported DeepSeek model" }
        require(apiKey.isNotBlank()) { "A DeepSeek API key is required" }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                put("thinking", JSONObject().put("type", if (enableThinking) "enabled" else "disabled"))
            }.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw DeepSeekHttpException(status)

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
                        consume(framer.acceptLine(reader.readLine() ?: break))
                    }
                }
            }
            if (!done) consume(framer.flush())
            if (!done) throw IOException("DeepSeek stream ended before [DONE]")
            formatter.finish().takeIf(String::isNotEmpty)?.let { onChunk(it) }
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    internal companion object {
        val MODELS = setOf("deepseek-flash", "deepseek-v4-pro")
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val SYSTEM_PROMPT = """你是一个 AI 助手。在回答时，请适当使用以下格式：

1. 使用 <artifact identifier="id" type="code" language="lang" title="title">...</artifact> 包裹代码制品
2. 使用标准 Markdown 格式

请确保回答内容丰富、格式清晰。"""
    }
}
