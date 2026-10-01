package com.jackcaow.smoothmarkdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** DEFAULT reuses the bounded memory cache; RELOAD refreshes it; NO_STORE never reads or writes it. */
enum class MarkdownResourceCachePolicy { DEFAULT, RELOAD, NO_STORE }

/** Headers and cache policy are part of the cache key, preventing authenticated image collisions. */
data class MarkdownResourceRequest(
    val source: String,
    val headers: Map<String, String> = emptyMap(),
    val cachePolicy: MarkdownResourceCachePolicy = MarkdownResourceCachePolicy.DEFAULT,
)

/** Return encoded bitmap/SVG bytes. Implementations must cooperate with coroutine cancellation. */
fun interface MarkdownResourceLoader {
    suspend fun load(request: MarkdownResourceRequest): ByteArray
}

/** Request-scoped configuration; default loading remains system-backed and dependency-free. */
data class MarkdownResourceOptions(
    val headers: Map<String, String> = emptyMap(),
    val cachePolicy: MarkdownResourceCachePolicy = MarkdownResourceCachePolicy.DEFAULT,
    val loader: MarkdownResourceLoader? = null,
    val loading: (@Composable () -> Unit)? = null,
    val error: (@Composable (Throwable?) -> Unit)? = null,
)

val LocalMarkdownResources = staticCompositionLocalOf { MarkdownResourceOptions() }
