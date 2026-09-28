package com.jackcaow.smoothmarkdown.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.jackcaow.smoothmarkdown.SmoothMarkdown
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor

private val demoMarkdown = """
    # Smooth Markdown Android

    A **native** renderer with *inline formatting* and [links](https://github.com/JackCaow/flutter-smooth-markdown).

    Inline math ${'$'}E=mc^2${'$'} and ${'$'}x^2+y^2=z^2${'$'} in one paragraph.

    ${'$'}${'$'}\frac{a}{b} = \sqrt{x^2 + 1}${'$'}${'$'}

    Native footnotes render in text[^note] and beside another reference[^2].

    [^note]: This is a **formatted** note.
        A continued line.
    [^2]: Another note.

    ```kotlin
    val greeting = "Hello from Android"
    val longLine = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    println(greeting)
    ```

    <details>
    <summary>Tap for hidden Markdown</summary>
    - **First** nested item
    - Second nested item
    </details>

    <details open>
    <summary>Already open</summary>
    Visible **Markdown** content.
    </details>

    Inline Markdown image ![GitHub](https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png) and text.

    Inline HTML image <img src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" alt="GitHub" width="24" height="24"> and text.

    > The source editor now supports formatting commands and preview.

    HTML: <b>bold</b> and <span style="color:red">red</span>.

    <div align="center">Centered **Markdown**</div>

    <img src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png" alt="HTML GitHub logo" width="64" height="64">

    SVG network: ![W3C SVG](https://www.w3.org/Icons/SVG/svg-logo-v.svg)

    SVG asset: ![Bundled SVG](smooth-markdown-mark.svg)

    Inline SVG ![mark](smooth-markdown-mark.svg) stays in this sentence.

    - [x] Render headings and emphasis
    - [ ] Complete formatted-block editing

    | Platform | Renderer |
    | --- | --- |
    | Android | Compose |

    ![GitHub logo](https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png)

    ```kotlin
    SmoothMarkdown(markdown = "Hello")
    ```
""".trimIndent()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            val controller = remember { MarkdownEditorController(demoMarkdown) }
            var showEditor by remember { mutableStateOf(false) }
            var enableHtml by remember { mutableStateOf(false) }
            MaterialTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    TextButton(onClick = { showEditor = !showEditor }) {
                        Text(if (showEditor) "Read" else "Open editor")
                    }
                    if (!showEditor) {
                        TextButton(onClick = { enableHtml = !enableHtml }) {
                            Text(if (enableHtml) "HTML on" else "Enable HTML")
                        }
                    }
                    if (showEditor) {
                        SmoothMarkdownEditor(controller, Modifier.weight(1f))
                    } else {
                        SmoothMarkdown(
                            markdown = controller.text,
                            modifier = Modifier.weight(1f),
                            onLinkClick = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            enableHtml = enableHtml,
                        )
                    }
                }
            }
        }
    }
}
