package com.jackcaow.smoothmarkdown.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.jackcaow.smoothmarkdown.SmoothMarkdown

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SmoothMarkdown(
                    markdown = """
                        # Smooth Markdown Android

                        A **native** renderer with *inline formatting* and [links](https://github.com/JackCaow/flutter-smooth-markdown).

                        > This is the first vertical slice of the Flutter port.

                        ```kotlin
                        SmoothMarkdown(markdown = "Hello")
                        ```
                    """.trimIndent(),
                )
            }
        }
    }
}
