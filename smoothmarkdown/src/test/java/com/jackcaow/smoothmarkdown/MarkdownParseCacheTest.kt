package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownParseCacheTest {
    @Test fun defaultParserReusesEntriesAndClearForcesReparse() {
        SmoothMarkdownCache.clear()
        val first = parseMarkdown("# Cache test")
        assertSame(first, parseMarkdown("# Cache test"))
        assertEquals(1, SmoothMarkdownCache.statistics.size)
        assertEquals(200, SmoothMarkdownCache.statistics.maxSize)
        SmoothMarkdownCache.clear()
        assertEquals(0, SmoothMarkdownCache.statistics.size)
        assertNotSame(first, parseMarkdown("# Cache test"))
        SmoothMarkdownCache.clear()
    }

    @Test fun leastRecentlyUsedEntryIsEvicted() {
        val cache = LruMarkdownParseCache(2)
        val a = Document()
        cache.put("a", a)
        cache.put("b", Document())
        assertSame(a, cache.get("a"))
        cache.put("c", Document())
        assertNull(cache.get("b"))
        assertEquals(2, cache.statistics.size)
    }

    @Test fun disabledCacheReparsesWithoutReadingOrWritingSharedEntries() {
        SmoothMarkdownCache.clear()
        val source = "# Frequently changing message"
        val uncached = parseMarkdown(source, enableCache = false)
        assertNotSame(uncached, parseMarkdown(source, enableCache = false))
        assertEquals(0, SmoothMarkdownCache.statistics.size)

        val cached = parseMarkdown(source)
        assertNotSame(cached, parseMarkdown(source, enableCache = false))
        assertSame(cached, parseMarkdown(source))
        assertEquals(1, SmoothMarkdownCache.statistics.size)
        SmoothMarkdownCache.clear()
    }
    @Test fun canonicallyEquivalentUnicodeStillHasDistinctAuthoredSource() {
        SmoothMarkdownCache.clear()
        val composed = parseMarkdown("é")
        val decomposed = parseMarkdown("e\u0301")
        assertNotSame(composed, decomposed)
        assertEquals("é", inlineText(composed.firstChild!!, false).text)
        assertEquals("e\u0301", inlineText(decomposed.firstChild!!, false).text)
        SmoothMarkdownCache.clear()
    }
}
