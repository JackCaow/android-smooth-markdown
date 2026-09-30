package com.jackcaow.smoothmarkdown

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers

internal data class NativeImageState(val data: NativeImageData? = null, val loading: Boolean = true, val error: Boolean = false, val failure: Throwable? = null)

@Composable
internal fun rememberNativeImage(source: String, resources: MarkdownResourceOptions = LocalMarkdownResources.current): NativeImageState {
    val context = LocalContext.current.applicationContext
    var state by remember(source, resources) { mutableStateOf(NativeImageState()) }
    LaunchedEffect(source, context, resources) {
        state = try {
            NativeImageState(data = NativeImageLoader.load(context, source, resources), loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            NativeImageState(loading = false, error = true, failure = failure)
        }
    }
    return state
}

/**
 * System-backed image content for a host's custom image builder. Supports HTTP(S), assets, data images,
 * and host-owned file/content/resource URIs. Markdown readers retain their stricter URL policy.
 */
@Composable
fun SmoothMarkdownImage(
    source: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    loading: @Composable () -> Unit = {},
    error: @Composable () -> Unit = {},
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
) {
    val state = rememberNativeImage(source, resourceOptions)
    val data = state.data
    val dimensions = data?.let { Modifier.aspectRatio(it.width / it.height) } ?: Modifier
    NativeMarkdownImage(source, contentDescription, modifier.then(dimensions), contentScale, loading, error, state, resourceOptions)
}

/** Native bitmap rendering or Android's built-in SVG-capable WebView, with shared bounded loading. */
@Composable
internal fun NativeMarkdownImage(
    source: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    loading: @Composable () -> Unit = {},
    error: @Composable () -> Unit = {},
    imageState: NativeImageState? = null,
    resourceOptions: MarkdownResourceOptions = LocalMarkdownResources.current,
) {
    val state = imageState ?: rememberNativeImage(source, resourceOptions)
    val resources = resourceOptions
    Box(modifier) {
        when (val data = state.data) {
            is NativeImageData.BitmapImage -> Image(
                bitmap = remember(data.bitmap) { data.bitmap.asImageBitmap() },
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
            is NativeImageData.SvgImage -> NativeSVGView(data, Modifier.matchParentSize(), resources)
            null -> if (state.loading) {
                (resources.loading ?: loading)()
            } else {
                resources.error?.invoke(state.failure) ?: error()
            }
        }
    }
}

/** Scripts, DOM storage, file access, popups and SVG-initiated navigation are disabled. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun NativeSVGView(image: NativeImageData.SvgImage, modifier: Modifier = Modifier, resources: MarkdownResourceOptions = LocalMarkdownResources.current) {
    val applicationContext = LocalContext.current.applicationContext
    val loadingScope = androidx.compose.runtime.rememberCoroutineScope()
    val currentResources by androidx.compose.runtime.rememberUpdatedState(resources)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            NativeSVGWebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                isFocusable = false
                isFocusableInTouchMode = false
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setSupportZoom(false)
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                    @Deprecated("Legacy platform fallback")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val uri = request?.url ?: return null
                        if (uri.scheme == "data") return null
                        val safe = uri.scheme in setOf("https", "http") ||
                            (uri.scheme == "file" && uri.path?.startsWith("/android_asset/") == true)
                        if (!safe) return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        // WebView invokes this hook on its background resource thread.
                        return try {
                            val data = runBlocking { loadingScope.async(Dispatchers.IO) { NativeImageLoader.loadBytes(applicationContext, uri.toString(), currentResources) }.await() }
                            val mime = when (uri.lastPathSegment?.substringAfterLast('.')?.lowercase()) {
                                "svg" -> "image/svg+xml"; "woff2" -> "font/woff2"; "woff" -> "font/woff"
                                "ttf" -> "font/ttf"; "otf" -> "font/otf"; "jpg", "jpeg" -> "image/jpeg"
                                "gif" -> "image/gif"; "webp" -> "image/webp"; else -> "image/png"
                            }
                            WebResourceResponse(mime, null, ByteArrayInputStream(data))
                        } catch (_: Exception) { WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))) }
                    }
                }
            }
        },
        update = { view -> view.show(image, resources) },
        onRelease = { view -> view.stopLoading(); view.destroy() },
    )
}

private class NativeSVGWebView(context: Context) : WebView(context) {
    private var currentImage: NativeImageData.SvgImage? = null
    private var resourceOptions: MarkdownResourceOptions? = null
    private var renderedHTML: String? = null
    private var renderedBaseURL: String? = null

    fun show(image: NativeImageData.SvgImage, resources: MarkdownResourceOptions) {
        if (resourceOptions != resources) { renderedHTML = null; renderedBaseURL = null; resourceOptions = resources }
        currentImage = image
        renderSizedDocument()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        renderSizedDocument()
    }

    private fun renderSizedDocument() {
        val image = currentImage ?: return
        if (width <= 0 || height <= 0) return
        val density = resources.displayMetrics.density
        val html = svgDocument(image, width / density, height / density)
        if (html != renderedHTML || image.baseUrl != renderedBaseURL) {
            renderedHTML = html
            renderedBaseURL = image.baseUrl
            loadDataWithBaseURL(image.baseUrl, html, "text/html", "UTF-8", null)
        }
    }
}

private fun svgDocument(image: NativeImageData.SvgImage, viewportWidth: Float, viewportHeight: Float): String {
    var markup = image.markup.replace(Regex("<\\?xml[^?]*\\?>", RegexOption.IGNORE_CASE), "")
    val root = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE).find(markup)
    if (root != null && !Regex("\\bviewBox\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(root.value)) {
        val tag = root.value.dropLast(1) + " viewBox=\"0 0 ${image.width} ${image.height}\">"
        markup = markup.replaceRange(root.range, tag)
    }
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"><style>html,body{margin:0;padding:0;width:${viewportWidth}px;height:${viewportHeight}px;overflow:hidden;background:transparent}body>svg{display:block;width:${viewportWidth}px!important;height:${viewportHeight}px!important;pointer-events:none}</style></head><body>$markup</body></html>"""
}
