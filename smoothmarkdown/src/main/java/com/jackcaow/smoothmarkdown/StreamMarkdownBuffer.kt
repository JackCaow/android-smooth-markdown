package com.jackcaow.smoothmarkdown

/** Accumulates streaming source and limits visible document updates to one per interval. */
class StreamMarkdownBuffer(
    private val intervalMillis: Long = 50,
    startMillis: Long = 0,
    private val enableHtml: Boolean = false,
) {
    private val buffer = StringBuilder()
    val fullText: String get() = buffer.toString()
    var visibleText: String = ""
        private set
    private var lastUpdateMillis = startMillis

    /** Returns milliseconds until a pending update, or null when this chunk was published. */
    fun append(chunk: String, nowMillis: Long): Long? {
        buffer.append(chunk)
        val remaining = intervalMillis - (nowMillis - lastUpdateMillis)
        if (remaining <= 0) {
            flush(nowMillis)
            return null
        }
        return remaining
    }

    /** Accepts a cumulative source snapshot, including a late collector's first value.
     * StateFlow may skip intermediate snapshots; only the suffix is appended. A shorter or
     * changed prefix starts a fresh stream rather than mixing two conversations.
     */
    fun appendPrefix(prefix: String, nowMillis: Long): Long? {
        val previous = buffer.toString()
        if (!prefix.startsWith(previous)) {
            reset(nowMillis)
            append(prefix, nowMillis)
            // Do not leave the previous conversation visible for a throttle interval.
            flush(nowMillis)
            return null
        }
        return append(prefix.substring(buffer.length), nowMillis)
    }

    fun flush(nowMillis: Long) {
        val full = buffer.toString()
        visibleText = if (enableHtml) SafeHtml.safeRenderPrefix(full) else full
        lastUpdateMillis = nowMillis
    }

    fun finish(nowMillis: Long) {
        visibleText = buffer.toString()
        lastUpdateMillis = nowMillis
    }

    fun reset(nowMillis: Long) {
        buffer.clear()
        visibleText = ""
        lastUpdateMillis = nowMillis
    }
}
