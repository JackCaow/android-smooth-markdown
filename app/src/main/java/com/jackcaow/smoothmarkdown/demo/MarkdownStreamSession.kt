package com.jackcaow.smoothmarkdown.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps the entire source available to bubbles that are composed after streaming starts. */
internal class MarkdownStreamSession {
    private val source = StringBuilder()
    private val mutablePrefixes = MutableStateFlow("")
    val prefixes: StateFlow<String> = mutablePrefixes
    var closed: Boolean = false
        private set

    fun append(chunk: String): String {
        if (!closed && chunk.isNotEmpty()) {
            source.append(chunk)
            mutablePrefixes.value = source.toString()
        }
        return source.toString()
    }

    fun finish(): String {
        closed = true
        return source.toString()
    }

    fun cancel() {
        closed = true
    }
}
