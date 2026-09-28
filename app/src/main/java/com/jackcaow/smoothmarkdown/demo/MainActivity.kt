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

    > The source editor now supports formatting commands and preview.

    HTML: <b>bold</b> and <span style="color:red">red</span>.

    <div align="center">Centered **Markdown**</div>

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
