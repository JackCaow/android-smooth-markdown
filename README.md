# Smooth Markdown for Android

A native Kotlin and Jetpack Compose Markdown reader, stream renderer, and editor, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown). Use it inside your own Compose screen; the included `app` module is a Demo, not an app dependency.

> **Distribution:** This repository currently supplies a source module. There is no published Maven artifact or versioned dependency coordinate.

## Install

Requires Android API 24+, Jetpack Compose, and Java 17. Add this repository to your app project, for example at `third_party/android-smooth-markdown`, then include only its library module in `settings.gradle.kts`:

```kotlin
include(":smoothmarkdown")
project(":smoothmarkdown").projectDir =
    file("third_party/android-smooth-markdown/smoothmarkdown")
```

In your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":smoothmarkdown"))
}
```

Your project must resolve the Android Gradle and Kotlin Compose plugins used by the library. See this repository's [Gradle configuration](build.gradle.kts) for the current plugin versions and repositories. Pin the source repository to a reviewed commit for shipped apps.

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

The reader covers common Markdown, GFM tables and task lists, images, footnotes, code, math, and opt-in safe HTML. Parser and renderer plugins add mentions, hashtags, Mermaid diagrams, and AI-specific blocks. See the [implementation reference](docs/reference.md) for exact supported constructs, selection behavior, customization, and current limits. A [cross-platform API map](https://github.com/JackCaow/flutter-smooth-markdown/blob/main/docs/native-api-map.md) compares Android, iOS, and Flutter.

## Demo

Open this repository in Android Studio and run the `app` configuration, or build it with:

```bash
./gradlew :app:assembleDebug
```

The Demo contains the Flutter example fixtures plus reader, streaming, editor, conversation, AI chat, and Mermaid screens. It is useful for checking rendering and interaction before embedding the library.

## Compatibility and limits

- Android API 24+; Compose and the plugin versions in this repository's Gradle files are the tested setup.
- HTML rendering is off by default. Math is parsed by default, unlike Flutter's default configuration.
- Full Flutter feature and visual parity, complete Mermaid/HTML coverage, and fully rich formatted editing are still in progress. Review [known limits](docs/reference.md#status) before depending on advanced behavior.
- A local Demo API key is a debug-only convenience. Never ship or commit a key; the library does not require one.

## Development

```bash
./gradlew :smoothmarkdown:testDebugUnitTest
./gradlew :app:assembleDebug
```

Source: [`smoothmarkdown`](smoothmarkdown/) · Demo: [`app`](app/) · [Implementation reference](docs/reference.md) · [Flutter origin](https://github.com/JackCaow/flutter-smooth-markdown) · [iOS library](https://github.com/JackCaow/ios-smooth-markdown)
