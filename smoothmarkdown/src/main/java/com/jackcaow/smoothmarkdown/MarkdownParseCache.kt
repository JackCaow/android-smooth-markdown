package com.jackcaow.smoothmarkdown

import org.commonmark.node.Node

/** Snapshot of the shared Markdown parse cache used by native readers. */
data class MarkdownCacheStatistics(val size: Int, val maxSize: Int) {
    val utilization: Double get() = size.toDouble() / maxSize
}

/** Shared LRU cache for the default parser. Custom plugin registries parse separately. */
object SmoothMarkdownCache {
    private val cache = LruMarkdownParseCache(200)

    val statistics: MarkdownCacheStatistics get() = cache.statistics

    fun clear() = cache.clear()

    internal fun get(markdown: String): Node? = cache.get(markdown)

    internal fun put(markdown: String, document: Node) = cache.put(markdown, document)
}

internal class LruMarkdownParseCache(private val maxSize: Int) {
    init { require(maxSize > 0) }

    private val entries = object : LinkedHashMap<String, Node>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Node>): Boolean = size > maxSize
    }

    @Synchronized fun get(markdown: String): Node? = entries[markdown]

    @Synchronized fun put(markdown: String, document: Node) { entries[markdown] = document }

    @Synchronized fun clear() { entries.clear() }

    val statistics: MarkdownCacheStatistics
        @Synchronized get() = MarkdownCacheStatistics(entries.size, maxSize)
}
