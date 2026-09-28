package com.jackcaow.smoothmarkdown.demo

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.jackcaow.smoothmarkdown.SmoothMarkdown

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            MaterialTheme {
                SmoothMarkdown(
                    modifier = Modifier.safeDrawingPadding(),
                    markdown = """
                        # Smooth Markdown Android

                        A **native** renderer with *inline formatting* and [links](https://github.com/JackCaow/flutter-smooth-markdown).

                        > This is the first vertical slice of the Flutter port.

                        - [x] Render headings and emphasis
                        - [ ] Port the editor

                        | Platform | Renderer |
                        | --- | --- |
                        | Android | Compose |

                        ![GitHub logo](https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png)

                        ```kotlin
                        SmoothMarkdown(markdown = "Hello")
                        ```
                    """.trimIndent(),
                    onLinkClick = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                )
            }
        }
    }
}
