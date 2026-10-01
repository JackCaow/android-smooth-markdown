package com.jackcaow.smoothmarkdown

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test

class MarkdownResourcesTest {
    @Test fun requestRetainsHeadersAndCachePolicy() = runBlocking {
        val loader = MarkdownResourceLoader { request ->
            assertEquals("test-only", request.headers["Authorization"])
            assertEquals(MarkdownResourceCachePolicy.NO_STORE, request.cachePolicy)
            byteArrayOf(1, 2)
        }
        assertArrayEquals(byteArrayOf(1, 2), loader.load(MarkdownResourceRequest(
            "https://example.test/image.png", mapOf("Authorization" to "test-only"), MarkdownResourceCachePolicy.NO_STORE)))
    }
    @Test fun customLoaderCooperatesWithCancellation() = runBlocking {
        var cancelled = false
        val loader = MarkdownResourceLoader { try { awaitCancellation() } finally { cancelled = true } }
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { loader.load(MarkdownResourceRequest("image.png")) }
        job.cancelAndJoin()
        assertTrue(cancelled)
    }
    @Test fun localizedTemplatesPreserveLiteralAuthorValues() {
        val strings = MarkdownStrings(overrides = mapOf("Code language: {language}" to "代码语言：{language} {missing}"))
        assertEquals("代码语言：custom {missing} %s {missing}", strings.format("Code language: {language}", mapOf("language" to "custom {missing} %s")))
    }
    @Test fun stringsOverrideKnownAndAdditionalLabels() {
        val strings = MarkdownStrings(copy = "复制", overrides = mapOf("Save" to "保存"))
        assertEquals("复制", strings.copy)
        assertEquals("保存", strings["Save"])
        assertEquals("Unknown", strings["Unknown"])
    }
}
