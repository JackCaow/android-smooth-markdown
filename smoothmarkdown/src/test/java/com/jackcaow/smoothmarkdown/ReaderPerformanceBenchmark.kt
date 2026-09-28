package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.system.measureNanoTime

/** Opt-in observational benchmark: SMOOTH_MARKDOWN_BENCH=1 ./gradlew :smoothmarkdown:testDebugUnitTest --tests '*ReaderPerformanceBenchmark' --rerun-tasks --info */
class ReaderPerformanceBenchmark {
    @Test fun readmeParseAndRapidStream() {
        assumeTrue(System.getenv("SMOOTH_MARKDOWN_BENCH") == "1")
        val readme = requireNotNull(javaClass.getResource("/performance/flutter-readme.md"))
            .readText(Charsets.UTF_8)
        val document = List(4) { readme }.joinToString("\n\n")
        assertTrue(document.length > 60_000)
        val runtime = Runtime.getRuntime()
        fun usedHeap() = runtime.totalMemory() - runtime.freeMemory()
        repeat(3) { parseMarkdown(document) }
        System.gc(); Thread.sleep(50)
        val before = usedHeap()
        var peak = before
        val parseTimes = LongArray(10)
        repeat(10) { index ->
            parseTimes[index] = measureNanoTime {
                val parsed = parseMarkdown(document)
                assertTrue(parsed.firstChild != null)
            }
            peak = maxOf(peak, usedHeap())
        }
        System.gc(); Thread.sleep(50)
        val after = usedHeap()
        val chunks = document.chunked(32)
        fun stream(parseOnPublish: Boolean): Pair<Long, Int> {
            val buffer = StreamMarkdownBuffer(intervalMillis = 50, startMillis = 0)
            var published = 0
            val elapsed = measureNanoTime {
                chunks.forEachIndexed { index, chunk ->
                    if (buffer.append(chunk, (index + 1).toLong()) == null) {
                        if (parseOnPublish) assertTrue(parseMarkdown(buffer.visibleText).firstChild != null)
                        published++
                    }
                }
                buffer.finish(chunks.size.toLong() + 1)
                if (parseOnPublish) assertTrue(parseMarkdown(buffer.visibleText).firstChild != null)
            }
            assertEquals(document, buffer.visibleText)
            assertTrue(published > 20)
            return elapsed to published
        }
        repeat(2) { stream(true) }
        val streamTimes = List(5) { stream(true) }
        val bufferOnlyTimes = List(5) { stream(false).first }
        val published = streamTimes.first().second
        val sorted = parseTimes.sorted()
        System.out.println("BENCH android fixtureBytes=${document.toByteArray().size} chunks=${chunks.size} publishes=$published " +
            "parseMedianMs=${"%.2f".format(sorted[5] / 1e6)} parseMaxMs=${"%.2f".format(sorted[9] / 1e6)} " +
            "streamMedianMs=${"%.2f".format(streamTimes.map { it.first }.sorted()[2] / 1e6)} " +
            "bufferOnlyMedianMs=${"%.2f".format(bufferOnlyTimes.sorted()[2] / 1e6)} " +
            "heapBeforeMiB=${"%.1f".format(before / 1048576.0)} heapPeakMiB=${"%.1f".format(peak / 1048576.0)} " +
            "heapAfterGCMiB=${"%.1f".format(after / 1048576.0)}")
    }
}
