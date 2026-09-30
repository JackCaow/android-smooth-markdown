package com.jackcaow.smoothmarkdown

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.util.Locale

/** Native WebView MathML renderer. JavaScript, network, file and content access remain disabled. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SystemMath(latex: String, displayMode: Boolean, modifier: Modifier = Modifier) {
    if(latex.isEmpty()) return
    val sheet=LocalMarkdownStyleSheet.current
    val tokens=sheet.designTokens.math
    val style=(sheet.paragraphStyle ?: MaterialTheme.typography.bodyLarge).merge(tokens.textStyle)
    val density=LocalDensity.current
    val font=style.fontSize.takeIf { it != TextUnit.Unspecified }?.value ?: 16f
    val cssSize=(font * if(displayMode)tokens.displayScale else 1f) * density.fontScale
    val foreground=(tokens.color ?: tokens.textStyle?.color?.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: sheet.textColor ?: style.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface).toArgb()
    val cssColor=remember(foreground) { String.format(Locale.ROOT,"#%06X",foreground and 0xFFFFFF) }
    val tree=remember(latex) { NativeTeXParser.parse(latex) }
    val extent=remember(tree,cssSize,displayMode,tokens.fontFamily,style.fontWeight,style.fontStyle) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create(tokens.fontFamily,mathTypefaceStyle(style)) }
        NativeTeXMetrics.preferredExtent(tree,cssSize,displayMode) { text,size->paint.textSize=size;paint.measureText(text) }
    }
    // A small reserve avoids clipping italic overhangs and operator dictionary spacing.
    val width=extent.width
    var height by remember(latex,cssSize,displayMode,tokens.fontFamily,style.fontWeight,style.fontStyle) { mutableFloatStateOf(extent.height) }
    val html=remember(latex,cssSize,displayMode,cssColor,tokens.fontFamily,style.fontWeight,style.fontStyle) {
        NativeTeXMathML.html(latex,cssSize,displayMode,cssColor,tokens.fontFamily,
            style.fontWeight?.weight ?: 400, style.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { webView?.stopLoading();webView?.destroy();webView=null } }
    AndroidView(
        modifier=modifier.size(width.dp,height.dp).semantics { contentDescription=latex },
        factory={ context ->
            WebView(context).apply {
                webView=this
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isHorizontalScrollBarEnabled=false;isVerticalScrollBarEnabled=false
                settings.apply {
                    javaScriptEnabled=false;domStorageEnabled=false
                    allowFileAccess=false;allowContentAccess=false;blockNetworkLoads=true
                    setSupportZoom(false);builtInZoomControls=false;displayZoomControls=false
                    textZoom=100;defaultTextEncodingName="UTF-8"
                }
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest?)=true
                    override fun shouldInterceptRequest(view: WebView?,request: WebResourceRequest?)=
                        WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                    override fun onPageFinished(view: WebView,url: String?) {
                        // Platform contentHeight needs no DOM script or Javascript bridge.
                        view.post { val measured=view.contentHeight.toFloat();if(measured>0&&measured<16000)height=maxOf(height,measured+2f) }
                    }
                }
            }
        },
        update={ view -> if(view.tag != html) { view.tag=html;view.loadDataWithBaseURL(null,html,"text/html","UTF-8",null) } }
    )
}
