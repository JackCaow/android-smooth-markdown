package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.Link
import com.jackcaow.smoothmarkdown.ast.Text
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownASTParser
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge
import com.jackcaow.smoothmarkdown.nativeparser.SourceRange
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StreamingMarkdownWorkerTest {
    private fun request(context: Any, version: Long, source: String, epoch: Long = 1, complete: Boolean = false) =
        StreamingMarkdownRequest(context, epoch, version, source, complete)
    private fun receive(worker: StreamingMarkdownWorker): StreamingMarkdownResult = runBlocking {
        withTimeout(5_000) { worker.results.receive() }
    }
    private fun await(latch: CountDownLatch) { assertTrue("Worker barrier timed out", latch.await(5, TimeUnit.SECONDS)) }
    private fun root(source: String, vararg text: String) = NativeMarkdownNode(NativeMarkdownNode.Kind.DOCUMENT,
        source, SourceRange(0, source.length), text.map { value ->
            val start = source.indexOf(value)
            NativeMarkdownNode(NativeMarkdownNode.Kind.PARAGRAPH, value, SourceRange(start, value.length), listOf(
                NativeMarkdownNode(NativeMarkdownNode.Kind.TEXT, value, SourceRange(start, value.length), literalText = value)))
        })

    @Test fun newestPendingPrefixKeepsAllFragmentsAndFinishMetadata() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seen = java.util.concurrent.CopyOnWriteArrayList<String>()
        val closed = CountDownLatch(1)
        val owner = AtomicLong()
        val context = Any()
        val worker = StreamingMarkdownWorker {
            owner.set(Thread.currentThread().id)
            object : StreamingMarkdownBackend {
                override fun update(source: String): RustMarkdownBridge.StreamSession.Update {
                    assertEquals(owner.get(), Thread.currentThread().id)
                    seen += source
                    if (seen.size == 1) { entered.countDown(); await(release) }
                    return RustMarkdownBridge.StreamSession.Update(0, root(source, source))
                }
                override fun close() { assertEquals(owner.get(), Thread.currentThread().id); closed.countDown() }
            }
        }
        try {
            worker.submit(request(context, 1, "A"))
            await(entered)
            worker.submit(request(context, 2, "AB"))
            worker.submit(request(context, 3, "ABC"))
            worker.submit(request(context, 4, "ABCD", complete = true))
            release.countDown()
            var result = receive(worker)
            if (result.request.version == 1L) result = receive(worker)
            assertEquals("ABCD", result.request.source)
            assertTrue(result.request.complete)
            assertEquals(listOf("A", "ABCD"), seen.toList())
            assertEquals(1L, result.baseVersion)
            await(closed)
        } finally { release.countDown(); worker.close() }
    }

    @Test fun skippedUiResultUsesCompleteWorkerTreeInsteadOfWrongDeltaBase() {
        val context = Any()
        val sources = listOf("A", "A\n\nB", "A\n\nB\n\nC")
        StreamingMarkdownWorker {
            var calls = 0
            object : StreamingMarkdownBackend {
                override fun update(source: String): RustMarkdownBridge.StreamSession.Update {
                    val text = listOf("A", "B", "C")[calls]
                    return RustMarkdownBridge.StreamSession.Update(calls++, root(source, text))
                }
                override fun close() = Unit
            }
        }.use { worker ->
            val publisher = StreamingMarkdownPublisher()
            worker.submit(request(context, 1, sources[0]))
            val first = publisher.apply(receive(worker))
            worker.submit(request(context, 2, sources[1]))
            val skipped = receive(worker)
            assertEquals(1, skipped.retainedBlocks)
            worker.submit(request(context, 3, sources[2], complete = true))
            val third = receive(worker)
            assertEquals(2L, third.baseVersion)
            assertEquals(listOf("A", "B", "C"), third.fullTree!!.children.map { it.source })
            assertEquals(listOf("C"), third.replacement!!.children.map { it.source })
            val final = publisher.apply(third)
            assertEquals(listOf("A", "B", "C"), final.document.descendants().filterIsInstance<Text>().map { it.literal }.toList())
            assertNotSame("The skipped base must trigger full adaptation", first.document.firstChild, final.document.firstChild)
            assertEquals(listOf("A"), first.document.descendants().filterIsInstance<Text>().map { it.literal }.toList())
        }
    }

    @Test fun matchingBaseRetainsMarkupOnlyOnUiOwner() {
        val context = Any()
        val publisher = StreamingMarkdownPublisher()
        val firstRequest = request(context, 1, "A")
        val firstTree = root("A", "A")
        val first = publisher.apply(StreamingMarkdownResult(firstRequest, 0, 0, firstTree, firstTree, -1))
        val retainedNode = first.document.firstChild!!
        val retainedKey = first.blockKeys[retainedNode]
        val nextRequest = request(context, 2, "A\n\nB")
        val tail = root(nextRequest.source, "B")
        val next = publisher.apply(StreamingMarkdownResult(nextRequest, 1, 1, root(nextRequest.source, "A", "B"), tail, -1))
        assertSame(retainedNode, next.document.firstChild)
        assertEquals(retainedKey, next.blockKeys[next.document.firstChild])
        assertSame(next.document, next.document.firstChild!!.parent)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        Thread { try { publisher.apply(StreamingMarkdownResult(nextRequest, 2, 0, tail, tail, -1)) }
            catch (error: Throwable) { failure.set(error) } }.apply { start(); join() }
        assertTrue(failure.get() is IllegalStateException)
    }

    @Test fun replacementFallbackAndConfigurationInvalidateLateResults() {
        val requests = StreamingMarkdownRequests()
        val first = requests.prepare("Before", false, true).also { requests.commit(it, true) }
        val stale = StreamingMarkdownResult(first, 0, 0, root(first.source, "Before"), root(first.source, "Before"), -1)
        val replacement = requests.prepare("After", false, true).also { requests.commit(it, true) }
        assertTrue(replacement.epoch > first.epoch)
        assertFalse(requests.accepts(stale))
        val fallback = requests.prepare("After \$x\$", false, false).also { requests.commit(it, false) }
        assertTrue(fallback.epoch > replacement.epoch)
        val backToNative = requests.prepare("New", false, true).also { requests.commit(it, true) }
        assertTrue(backToNative.epoch > fallback.epoch)
        val otherConfiguration = StreamingMarkdownRequests()
        otherConfiguration.commit(otherConfiguration.prepare("Before", false, true), true)
        assertFalse("Matching source/version from a different context must be rejected", otherConfiguration.accepts(stale))
    }

    @Test fun completeRequiresFinalPublicationAndCannotFireTwice() {
        val context = Any()
        val gate = StreamingMarkdownCompletion()
        val prefix = request(context, 1, "Final", complete = false)
        val final = request(context, 2, "Final", complete = true)
        val replacedBeforeTheNextFrame = request(Any(), 2, "Final", complete = true)
        assertFalse("A replaced stream with the same source/version cannot revive the old completion ticket",
            gate.publish(final, 2, "Final", replacedBeforeTheNextFrame))
        assertFalse(gate.publish(prefix, 1, "Final"))
        assertFalse("Same source from the earlier request is not final publication", gate.publish(final, 1, "Final"))
        assertFalse(gate.publish(final, 2, "Earlier"))
        assertTrue(gate.publish(final, 2, "Final"))
        assertFalse(gate.publish(final, 2, "Final"))
        val cancelled = StreamingMarkdownRequests()
        cancelled.commit(cancelled.prepare("Other", false, true), true)
        assertFalse(cancelled.accepts(StreamingMarkdownResult(final, 1, 0, null, null, -1)))
    }

    @Test fun closeWaitsForInFlightParserBeforeFreeingOnSameThread() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val freed = CountDownLatch(1)
        val owner = AtomicLong()
        val parses = AtomicInteger()
        val worker = StreamingMarkdownWorker {
            owner.set(Thread.currentThread().id)
            object : StreamingMarkdownBackend {
                override fun update(source: String): RustMarkdownBridge.StreamSession.Update {
                    parses.incrementAndGet(); entered.countDown(); await(release)
                    assertEquals(owner.get(), Thread.currentThread().id)
                    return RustMarkdownBridge.StreamSession.Update(0, root(source, source))
                }
                override fun close() { assertEquals(owner.get(), Thread.currentThread().id); freed.countDown() }
            }
        }
        worker.submit(request(Any(), 1, "In flight"))
        await(entered)
        worker.submit(request(Any(), 2, "Must not execute", complete = true))
        worker.close(); worker.close()
        assertEquals(1L, freed.count)
        release.countDown(); await(freed)
        assertEquals(1, parses.get())
        runBlocking { withTimeout(5_000) { assertTrue(worker.results.receiveCatching().isClosed) } }
    }

    @Test fun backendFailureResetsNativeBaseAndKeepsErrorVisible() {
        val creations = AtomicInteger()
        val context = Any()
        StreamingMarkdownWorker {
            val fail = creations.incrementAndGet() == 1
            object : StreamingMarkdownBackend {
                override fun update(source: String): RustMarkdownBridge.StreamSession.Update {
                    if (fail) error("Expected parse failure")
                    return RustMarkdownBridge.StreamSession.Update(0, root(source, source))
                }
                override fun close() = Unit
            }
        }.use { worker ->
            worker.submit(request(context, 1, "Before"))
            assertEquals("Expected parse failure", receive(worker).error!!.message)
            worker.submit(request(context, 2, "After", complete = true))
            val recovered = receive(worker)
            assertNull(recovered.error)
            assertEquals(0L, recovered.baseVersion)
            assertEquals("After", recovered.fullTree!!.children.single().source)
            assertEquals(2, creations.get())
        }
    }

    @Test fun actualNativeWorkerSnapshotsResolveReferencesAndUseBackgroundThread() {
        assertTrue(RustMarkdownBridge.available)
        val before = RustMarkdownBridge.successfulParseCount
        val context = Any()
        val source = "# 中文🙂\n\n[future][x]\n\nSecond\n\nTail"
        StreamingMarkdownWorker().use { worker ->
            worker.submit(request(context, 1, source))
            val first = receive(worker)
            assertNotEquals(Thread.currentThread().id, first.parserThreadId)
            val finalSource = source + "\n\n[x]: /resolved\n"
            worker.submit(request(context, 2, finalSource, complete = true))
            val final = receive(worker)
            assertTrue(RustMarkdownBridge.successfulParseCount > before)
            assertEquals(NativeMarkdownASTParser().parse(finalSource), final.fullTree)
            assertEquals(0, final.retainedBlocks)
            val document = StreamingMarkdownPublisher().apply(final)
            assertEquals("/resolved", document.document.descendants().filterIsInstance<Link>().single().destination)
            assertTrue(document.document.firstChild is Heading)
        }
    }

    @Test fun finalResultReleasesExecutorThreadWithoutWaitingForUiDisposal() {
        val owner = java.util.concurrent.atomic.AtomicReference<Thread>()
        val closed = AtomicInteger()
        val worker = StreamingMarkdownWorker {
            owner.set(Thread.currentThread())
            object : StreamingMarkdownBackend {
                override fun update(source: String) = RustMarkdownBridge.StreamSession.Update(0, root(source, source))
                override fun close() { assertSame(owner.get(), Thread.currentThread()); closed.incrementAndGet() }
            }
        }
        try {
            worker.submit(request(Any(), 1, "Final", complete = true))
            assertEquals("Final", receive(worker).request.source)
            runBlocking { withTimeout(5_000) { assertTrue(worker.results.receiveCatching().isClosed) } }
            owner.get().join(5_000)
            assertFalse("A completed mounted chat bubble must not retain an OS thread", owner.get().isAlive)
            assertEquals(1, closed.get())
        } finally { worker.close() }
    }

    @Test fun missingNativeBackendLeavesARealUiFallbackRequest() {
        StreamingMarkdownWorker {
            object : StreamingMarkdownBackend {
                override fun update(source: String): RustMarkdownBridge.StreamSession.Update? = null
                override fun close() = Unit
            }
        }.use { worker ->
            val source = "# Heading\n\nA **bold** paragraph"
            worker.submit(request(Any(), 1, source, complete = true))
            val result = receive(worker)
            assertNull(result.error)
            assertNull(result.fullTree)
            assertNull(result.replacement)
            assertEquals(source, result.request.source)
            StreamingMarkdownSession().use { fallback ->
                val document = fallback.parse(result.request.source)
                assertTrue(document.document.firstChild is Heading)
                assertTrue(document.document.descendants().filterIsInstance<Text>().any { it.literal == "bold" })
            }
        }
    }
}
