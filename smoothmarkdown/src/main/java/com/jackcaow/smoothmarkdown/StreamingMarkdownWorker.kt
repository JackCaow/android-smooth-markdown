package com.jackcaow.smoothmarkdown

import com.jackcaow.smoothmarkdown.ast.Node
import com.jackcaow.smoothmarkdown.nativeparser.NativeMarkdownNode
import com.jackcaow.smoothmarkdown.nativeparser.RustMarkdownBridge
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class StreamingMarkdownRequest(
    val context: Any, val epoch: Long, val version: Long, val source: String, val complete: Boolean,
)

internal data class StreamingMarkdownResult(
    val request: StreamingMarkdownRequest,
    val baseVersion: Long,
    val retainedBlocks: Int,
    val fullTree: NativeMarkdownNode?,
    val replacement: NativeMarkdownNode?,
    val parserThreadId: Long,
    val error: Throwable? = null,
)

/** Only immutable native trees leave this interface; mutable public Markup stays on the UI. */
internal interface StreamingMarkdownBackend : AutoCloseable {
    fun update(source: String): RustMarkdownBridge.StreamSession.Update?
}

internal class StreamingMarkdownWorker(
    private val backendFactory: () -> StreamingMarkdownBackend = {
        val session = RustMarkdownBridge.StreamSession.create()
        object : StreamingMarkdownBackend {
            override fun update(source: String) = session?.update(source)
            override fun close() { session?.close() }
        }
    },
) : AutoCloseable {
    val results = Channel<StreamingMarkdownResult>(Channel.CONFLATED)
    private val pending = AtomicReference<StreamingMarkdownRequest?>()
    private val wake = Semaphore(0)
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "smoothmarkdown-stream").apply { isDaemon = true }
    }

    init { executor.execute(::run) }

    /** Each request is a complete prefix. Coalescing replaces queued snapshots, never fragments. */
    fun submit(request: StreamingMarkdownRequest) {
        if (closed.get()) return
        if (pending.getAndSet(request) == null) wake.release()
        if (closed.get()) pending.compareAndSet(request, null)
    }

    private fun run() {
        var backend: StreamingMarkdownBackend? = null
        var previous: StreamingMarkdownResult? = null
        try {
            while (true) {
                wake.acquire()
                if (closed.get()) break
                val request = pending.getAndSet(null) ?: continue
                val old = previous?.takeIf {
                    it.request.context === request.context && it.request.epoch == request.epoch
                }
                if (old == null) { backend?.close(); backend = null; previous = null }
                val result = try {
                    val engine = backend ?: backendFactory().also { backend = it }
                    val update = engine.update(request.source)
                    val oldChildren = old?.fullTree?.children.orEmpty()
                    if (update != null) {
                        check(update.retainedBlocks <= oldChildren.size) { "Missing worker retained prefix" }
                        val full = update.replacement.copy(children =
                            oldChildren.take(update.retainedBlocks) + update.replacement.children)
                        StreamingMarkdownResult(request, old?.request?.version ?: 0, update.retainedBlocks,
                            full, update.replacement, Thread.currentThread().id)
                    } else StreamingMarkdownResult(request, 0, 0, null, null, Thread.currentThread().id)
                } catch (error: Throwable) {
                    backend?.close(); backend = null; previous = null
                    StreamingMarkdownResult(request, 0, 0, null, null, Thread.currentThread().id, error)
                }
                // Commit the worker's complete tree even if the UI has already skipped this result.
                // The next delta is relative to this version, not the last UI publication.
                previous = result.takeIf { it.error == null && it.fullTree != null }
                if (request.complete) { backend?.close(); backend = null }
                if (!closed.get()) results.trySend(result)
                // Finite chat bubbles may remain mounted indefinitely. Their final result
                // remains buffered, but they must not retain an idle native handle or OS thread.
                if (request.complete) break
            }
        } finally {
            try { backend?.close() } finally {
                closed.set(true)
                pending.set(null)
                results.close()
                executor.shutdown()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            pending.set(null)
            wake.release()
            // In-flight FFI returns before the owning thread frees the handle. shutdownNow
            // could discard cleanup or make a cancelled continuation run on another thread.
            executor.shutdown()
        }
    }
}

/** Prepared on the UI; committed only after a successful composition. */
internal class StreamingMarkdownRequests {
    private val context = Any()
    var latest: StreamingMarkdownRequest? = null
        private set
    private var eligible = true
    fun prepare(source: String, complete: Boolean, nativeEligible: Boolean): StreamingMarkdownRequest {
        val old = latest
        if (old != null && old.source == source && old.complete == complete && eligible == nativeEligible) return old
        val reset = old != null && (!source.startsWith(old.source) || eligible != nativeEligible)
        return StreamingMarkdownRequest(context, (old?.epoch ?: 1) + if (reset) 1 else 0,
            (old?.version ?: 0) + 1, source, complete)
    }
    fun commit(request: StreamingMarkdownRequest, nativeEligible: Boolean): Boolean {
        val changed = latest !== request || eligible != nativeEligible
        latest = request
        eligible = nativeEligible
        return changed
    }
    fun accepts(result: StreamingMarkdownResult): Boolean = latest?.let {
        it.context === result.request.context && it.epoch == result.request.epoch &&
            it.version == result.request.version && it.source == result.request.source
    } == true
}

/** Adapt on the owner/UI thread. A skipped worker version requires its full immutable tree. */
internal class StreamingMarkdownPublisher {
    private var previous: StreamingMarkdownDocument? = null
    private var context: Any? = null
    private var epoch = 0L
    private var appliedVersion = 0L
    private var nextKey = 1L
    private val owner = Thread.currentThread()
    private val converter = NativeMarkdownParser(enableGFM = true, enableExtensions = true)

    fun invalidate() {
        check(Thread.currentThread() === owner)
        previous = null; context = null; appliedVersion = 0
    }

    fun apply(result: StreamingMarkdownResult): StreamingMarkdownDocument {
        check(Thread.currentThread() === owner) { "Stream Markup must be adapted on its UI owner thread" }
        val native = requireNotNull(result.fullTree)
        val canReuse = context === result.request.context && epoch == result.request.epoch &&
            appliedVersion == result.baseVersion && previous != null
        val retained = if (canReuse) result.retainedBlocks else 0
        val tree = if (canReuse) requireNotNull(result.replacement) else native
        val document = converter.convertTree(result.request.source, tree)
        if (retained > 0) {
            val old = previous!!.document.children().toList()
            check(retained <= old.size) { "Missing UI retained prefix" }
            old.take(retained).asReversed().forEach(document::prependChild)
        }
        FootnoteReferencePostProcessor(result.request.source).process(document)
        val keys = java.util.IdentityHashMap<Node, Long>()
        document.children().forEach { keys[it] = if (canReuse) previous?.blockKeys?.get(it) ?: nextKey++ else nextKey++ }
        return StreamingMarkdownDocument(result.request.source, document, null, false, keys,
            result.parserThreadId).also {
            previous = it; context = result.request.context; epoch = result.request.epoch
            appliedVersion = result.request.version
        }
    }
}

internal class StreamingMarkdownCompletion {
    private var completed = false
    fun publish(request: StreamingMarkdownRequest, publishedVersion: Long, source: String,
                latestRequest: StreamingMarkdownRequest? = request): Boolean {
        if (latestRequest !== request || completed || !request.complete ||
            publishedVersion != request.version || source != request.source) return false
        completed = true
        return true
    }
}

internal fun streamingNativeEligible(source: String, plugins: ParserPluginRegistry?, enableHtml: Boolean): Boolean =
    plugins == null && !enableHtml && !source.contains("<details", ignoreCase = true) &&
        !source.contains("[^") && !source.contains('$') && !source.contains("\\(") && !source.contains("\\[")

internal fun emptyStreamingDocument(): StreamingMarkdownDocument = StreamingMarkdownDocument("",
    com.jackcaow.smoothmarkdown.ast.Document(), null, false, emptyMap())

internal data class StreamingMarkdownEmission(val snapshot: StreamSnapshot, val identity: Any)
