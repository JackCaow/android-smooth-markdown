# Smooth Markdown for Android

A native Kotlin and Jetpack Compose Markdown reader, stream renderer, and editor. Use it inside your own Compose screen; the included `app` module is a Demo, not an app dependency.

## Runtime dependencies

Version `0.2.0` uses the owned parser and system rendering described below. Version `0.1.0` uses the previous external engines.

The reader uses a library-owned Markdown AST/parser, Android bitmap/network APIs, and system WebView for SVG and MathML. It does not depend on CommonMark, Coil, RaTeX, AndroidSVG, OkHttp, MathJax or KaTeX.

Jetpack Compose and Kotlin coroutines remain framework dependencies. The library currently requires Compose; it is not a standalone Android View library.

Custom builders import `com.jackcaow.smoothmarkdown.ast.Node` and the other library-owned AST types. Applications upgrading from 0.1.0 must update their former `org.commonmark` imports.

## Install

Requires Android API 24+, Jetpack Compose, and Java 17. Add the public JitPack Maven repository to your app project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

In your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.JackCaow:android-smooth-markdown:0.2.0")
}
```

The library is also available as a source module. To use source instead, pin this repository to a reviewed commit, include `:smoothmarkdown` in `settings.gradle.kts`, and depend on `project(":smoothmarkdown")`. The `app` module is only the Demo.

## Quick start

### Read Markdown

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.SmoothMarkdown

@Composable
fun Article(markdown: String, openLink: (String) -> Unit) {
    SmoothMarkdown(
        markdown = markdown,
        selectable = true,
        onLinkClick = openLink,
    )
}
```

The reader scrolls vertically by default. In a parent scroll container or chat item, pass `scrollable = false`. Set `useEnhancedComponents = true` for the Demo's decorated headers, quotes, links, and code controls.

### Render a stream

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.StreamMarkdown
import kotlinx.coroutines.flow.Flow

@Composable
fun Reply(chunks: Flow<String>, onFinished: (String) -> Unit) {
    StreamMarkdown(chunks = chunks, onComplete = onFinished)
}
```

`chunks` is a finite flow of **new text fragments**. `onComplete` runs after that flow ends and the final source is published to the renderer. For cumulative snapshots, use the separate `prefixes: StateFlow<String>` overload; a normal hot `StateFlow` does not complete, so its completion callback does not run.

### Edit Markdown

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorMode
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor

@Composable
fun Editor(initialMarkdown: String, onSave: (String) -> Unit) {
    val controller = remember {
        MarkdownEditorController(initialMarkdown).apply {
            mode = MarkdownEditorMode.FORMATTED
        }
    }
    SmoothMarkdownEditor(controller = controller, onSave = onSave)
}
```

The controller defaults to **Source** mode; the example selects **Formatted** explicitly. The editor also offers Preview and Split modes.

## Features

| Need | Android API |
| --- | --- |
| Read and select Markdown | `SmoothMarkdown(markdown = …, selectable = true)` |
| Render incoming text | `StreamMarkdown(chunks = flow)` or `StreamMarkdown(prefixes = stateFlow)` |
| Edit source or formatted blocks | `MarkdownEditorController` + `SmoothMarkdownEditor` |
| Handle links and images | `onLinkClick`, `onImageClickWithMetadata` |
| Style rendered content | `styleSheet`, `useEnhancedComponents` |
| Replace built-in rendering | `imageBuilder`, `codeBlockBuilder`, `builderRegistry`, `plugins` |

The reader covers common Markdown, GFM tables and task lists, images, footnotes, code, math, and opt-in safe HTML. Parser and renderer plugins add mentions, hashtags, Mermaid diagrams, and AI-specific blocks. See the [implementation reference](docs/reference.md) for exact supported constructs, selection behavior, customization, and current limits.

## Demo

Open this repository in Android Studio and run the `app` configuration, or build it with:

```bash
./gradlew :app:assembleDebug
```

The Demo contains reader, streaming, editor, conversation, AI chat, and Mermaid screens. It is useful for checking rendering and interaction before embedding the library.

## Compatibility and limits

- Android API 24+; Compose and the plugin versions in this repository's Gradle files are the tested setup.
- HTML rendering is off by default; supported math is parsed by default.
- Complete Mermaid and HTML syntax coverage and fully rich formatted editing are still in progress. Review [known limits](docs/reference.md#status) before depending on advanced behavior.
- A local Demo API key is a debug-only convenience. Never ship or commit a key; the library does not require one.

## Development

```bash
./gradlew :smoothmarkdown:testDebugUnitTest
./gradlew :app:assembleDebug
```

Source: [`smoothmarkdown`](smoothmarkdown/) · Demo: [`app`](app/) · [Implementation reference](docs/reference.md)
