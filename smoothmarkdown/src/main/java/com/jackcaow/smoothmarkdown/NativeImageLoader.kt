package com.jackcaow.smoothmarkdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.util.Xml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Bounded image data decoded with Android system facilities. Dimensions retain the original size. */
internal sealed class NativeImageData(val width: Float, val height: Float) {
    class BitmapImage(val bitmap: Bitmap, width: Float, height: Float) : NativeImageData(width, height)
    class SvgImage(val markup: String, width: Float, height: Float, val baseUrl: String?) : NativeImageData(width, height)
}

internal object NativeImageLoader {
    const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    const val MAX_SVG_BYTES = 2 * 1024 * 1024
    private const val MAX_DECODE_PIXELS = 4_000_000L
    private const val MAX_SOURCE_PIXELS = 64_000_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = Semaphore(6)
    private data class CacheKey(val source: String, val headers: Map<String, String>, val loader: MarkdownResourceLoader?)
    private val cache = object : LruCache<CacheKey, NativeImageData>(32 * 1024 * 1024) {
        override fun sizeOf(key: CacheKey, value: NativeImageData): Int = when (value) {
            is NativeImageData.BitmapImage -> value.bitmap.allocationByteCount
            is NativeImageData.SvgImage -> value.markup.length * 2
        }
    }

    suspend fun load(context: Context, source: String, options: MarkdownResourceOptions = MarkdownResourceOptions()): NativeImageData {
        val request = MarkdownResourceRequest(source, options.headers.toMap(), options.cachePolicy)
        val key = CacheKey(source, request.headers, options.loader)
        if (options.cachePolicy == MarkdownResourceCachePolicy.DEFAULT) synchronized(this) { cache.get(key)?.let { return it } }
        val data = downloads.withPermit {
            if (options.loader != null) withContext(Dispatchers.IO) { decode(options.loader.load(request), source) }
            else suspendCancellableCoroutine { continuation ->
                val connection = AtomicReference<HttpURLConnection?>()
                val cancelled = AtomicBoolean(false)
                val job = scope.launch {
                    try {
                        val encoded = read(context.applicationContext, source, request.headers, connection, cancelled)
                        val result = decode(encoded.first, encoded.second)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
                continuation.invokeOnCancellation { cancelled.set(true); connection.get()?.disconnect(); job.cancel() }
            }
        }
        currentCoroutineContext().ensureActive()
        if (options.cachePolicy != MarkdownResourceCachePolicy.NO_STORE) synchronized(this) { cache.put(key, data) }
        return data
    }

    private val resourceCache = object : LruCache<CacheKey, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: CacheKey, value: ByteArray) = value.size
    }

    /** Raw nested SVG images/fonts use the same host transport and credential/cache isolation. */
    suspend fun loadBytes(context: Context, source: String, options: MarkdownResourceOptions): ByteArray {
        val request = MarkdownResourceRequest(source, options.headers.toMap(), options.cachePolicy)
        val key = CacheKey(source, request.headers, options.loader)
        if (options.cachePolicy == MarkdownResourceCachePolicy.DEFAULT) synchronized(this) { resourceCache.get(key)?.let { return it } }
        val bytes = if (options.loader != null) options.loader.load(request) else suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpURLConnection?>()
            val cancelled = AtomicBoolean(false)
            val job = scope.launch {
                try {
                    val result = read(context.applicationContext, source, request.headers, connection, cancelled).first
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
            continuation.invokeOnCancellation { cancelled.set(true); connection.get()?.disconnect(); job.cancel() }
        }
        currentCoroutineContext().ensureActive()
        require(bytes.size <= MAX_DOWNLOAD_BYTES) { "Resource exceeds size limit" }
        if (options.cachePolicy != MarkdownResourceCachePolicy.NO_STORE) synchronized(this) { resourceCache.put(key, bytes) }
        return bytes
    }

    private fun read(context: Context, source: String, headers: Map<String, String>, active: AtomicReference<HttpURLConnection?>, cancelled: AtomicBoolean): Pair<ByteArray, String?> {
        val bytes: ByteArray
        var baseUrl: String? = null
        when {
            source.startsWith("data:", true) -> bytes = decodeDataUrl(source)
            source.startsWith("https://", true) || source.startsWith("http://", true) -> {
                val response = readRemote(source, headers, active, cancelled)
                bytes = response.first
                baseUrl = response.second
            }
            source.startsWith("file:", true) && !source.startsWith("file:///android_asset/") -> {
                // Only explicit host image builders can reach local files; reader source checks reject them.
                val uri = Uri.parse(source)
                require(uri.authority.isNullOrEmpty()) { "Remote filesystem image URI is unsupported" }
                val path = requireNotNull(uri.path) { "Local image path is missing" }
                bytes = File(path).inputStream().use { readBounded(it, MAX_DOWNLOAD_BYTES) }
            }
            source.startsWith("content://", true) || source.startsWith("android.resource://", true) -> {
                // Host-owned sources only: Markdown's imageModel continues rejecting these schemes.
                val uri = Uri.parse(source)
                require(!uri.authority.isNullOrBlank()) { "Local image URI is missing its authority" }
                bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "Local image cannot be opened" }
                    .use { readBounded(it, MAX_DOWNLOAD_BYTES) }
            }
            else -> {
                val asset = assetPath(source)
                bytes = context.assets.open(asset).use { readBounded(it, MAX_DOWNLOAD_BYTES) }
                baseUrl = Uri.Builder().scheme("file").path("/android_asset/$asset").build().toString()
            }
        }
        return bytes to baseUrl
    }

    /** Match Android asset URI decoding, while denying traversal and general filesystem access. */
    internal fun assetPath(source: String): String {
        require(!source.startsWith("//")) { "Protocol-relative images are unsupported" }
        val uri = Uri.parse(if (source.startsWith("file:///android_asset/")) source
            else "file:///android_asset/${source.trimStart('/')}")
        require(uri.scheme == "file" && uri.authority.isNullOrEmpty()) { "Unsupported image source" }
        val path = uri.path.orEmpty()
        require(path.startsWith("/android_asset/")) { "Image is outside Android assets" }
        val asset = path.removePrefix("/android_asset/")
        require(asset.isNotBlank() && !asset.contains(':') && !asset.contains('\\') &&
            asset.split('/').none { it == ".." }) { "Unsupported image source" }
        return asset
    }

    internal fun decode(bytes: ByteArray, baseUrl: String? = null): NativeImageData {
        require(bytes.isNotEmpty() && bytes.size <= MAX_DOWNLOAD_BYTES) { "Image data exceeds size limit" }
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, 4096)).toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n', '\t')
        if (Regex("<svg(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) {
            require(bytes.size <= MAX_SVG_BYTES) { "SVG exceeds size limit" }
            val markup = bytes.toString(Charsets.UTF_8)
            val size = svgSize(markup)
            return NativeImageData.SvgImage(markup, size.first, size.second, baseUrl)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "Unsupported or oversized bitmap" }
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_DECODE_PIXELS ||
            bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Bitmap decoding failed" }
        return NativeImageData.BitmapImage(bitmap, bounds.outWidth.toFloat(), bounds.outHeight.toFloat())
    }

    /** Validate XML with the system parser, rejecting DTDs and malformed SVG before it reaches WebView. */
    internal fun svgSize(markup: String): Pair<Float, Float> {
        require(!Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(markup)) { "SVG DTDs are unsupported" }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(markup))
        var width: Float? = null
        var height: Float? = null
        var ratio: Float? = null
        var rootSeen = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && !rootSeen) {
                require(parser.name == "svg") { "Image XML root must be SVG" }
                rootSeen = true
                width = svgLength(parser.getAttributeValue(null, "width"))
                height = svgLength(parser.getAttributeValue(null, "height"))
                val box = parser.getAttributeValue(null, "viewBox")?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }
                if (box?.size == 4 && box[2] > 0 && box[3] > 0 && box.all { it.isFinite() }) {
                    ratio = box[2] / box[3]
                    if (width == null && height == null) { width = box[2]; height = box[3] }
                }
            }
            parser.next()
        }
        require(rootSeen) { "SVG root is missing" }
        val naturalWidth = width ?: if (height != null && ratio != null) height * ratio else 300f
        val naturalHeight = height ?: if (ratio != null) naturalWidth / ratio else 150f
        require(naturalWidth.isFinite() && naturalHeight.isFinite() && naturalWidth > 0f && naturalHeight > 0f &&
            naturalWidth <= 100_000f && naturalHeight <= 100_000f) { "Invalid SVG dimensions" }
        return naturalWidth to naturalHeight
    }

    private fun svgLength(value: String?): Float? {
        val match = value?.trim()?.let { Regex("([+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)(px|pt|pc|in|cm|mm|q)?", RegexOption.IGNORE_CASE).matchEntire(it) } ?: return null
        val factor = when (match.groupValues[2].lowercase()) { "pt" -> 96f/72; "pc" -> 16f; "in" -> 96f; "cm" -> 96f/2.54f; "mm" -> 96f/25.4f; "q" -> 96f/101.6f; else -> 1f }
        return match.groupValues[1].toFloatOrNull()?.times(factor)?.takeIf { it.isFinite() && it > 0f }
    }

    private fun decodeDataUrl(source: String): ByteArray {
        require(source.length <= MAX_DOWNLOAD_BYTES * 4 / 3 + 4096) { "Data image exceeds size limit" }
        val comma = source.indexOf(',')
        require(comma > 5) { "Malformed data image" }
        val metadata = source.substring(5, comma)
        require(metadata.startsWith("image/", true)) { "Data URL must contain an image" }
        val body = source.substring(comma + 1)
        return if (metadata.split(';').any { it.equals("base64", true) }) Base64.decode(body, Base64.DEFAULT)
        else URLDecoder.decode(body.replace("+", "%2B"), "UTF-8").toByteArray(Charsets.UTF_8)
    }

    private fun readRemote(source: String, headers: Map<String, String>, active: AtomicReference<HttpURLConnection?>, cancelled: AtomicBoolean): Pair<ByteArray, String> {
        val original = URL(source)
        var current = original
        repeat(6) { redirect ->
            require(current.protocol == "https" || current.protocol == "http") { "Unsupported image redirect" }
            val connection = current.openConnection() as HttpURLConnection
            active.set(connection)
            if (cancelled.get() || Thread.currentThread().isInterrupted) { connection.disconnect(); throw InterruptedException() }
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "image/*")
                connection.setRequestProperty("User-Agent", "SmoothMarkdown/Android")
                if (current.host == original.host && current.port == original.port && current.protocol == original.protocol) {
                    headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                }
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    require(redirect < 5) { "Too many image redirects" }
                    val next = URL(current, requireNotNull(connection.getHeaderField("Location")))
                    require(original.protocol != "https" || next.protocol == "https") { "Insecure image redirect" }
                    current = next
                } else {
                    require(status in 200..299) { "Image request failed: $status" }
                    require(connection.contentLengthLong <= MAX_DOWNLOAD_BYTES) { "Image exceeds size limit" }
                    return connection.inputStream.use { readBounded(it, MAX_DOWNLOAD_BYTES) } to current.toExternalForm()
                }
            } finally { active.compareAndSet(connection, null); connection.disconnect() }
        }
        error("Too many image redirects")
    }

    internal fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 8192))
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= limit) { "Image exceeds size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
