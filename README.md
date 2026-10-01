# Smooth Markdown for Android

A native Kotlin and Jetpack Compose Markdown reader, stream renderer, and editor. Use it inside your own Compose screen; the included `app` module is a Demo, not an app dependency.

## Runtime dependencies

Version `0.3.1` includes the owned Rust parser, structured configuration, design tokens, resource loading and localized labels. The parser and JNI transport use no third-party code or crates.

The reader uses a library-owned Markdown AST and Rust parser, Android bitmap/network APIs, and system WebView for SVG and MathML. It does not depend on CommonMark, Coil, RaTeX, AndroidSVG, OkHttp, MathJax or KaTeX.

Jetpack Compose and Kotlin coroutines remain framework dependencies. The reader requires Compose. The independent `smoothmarkdown-core` module provides CommonMark/GFM parsing and HTML export on the JVM without Android, Compose, or coroutines. The existing `smoothmarkdown` module includes the core transitively and retains the original AST package names.

Custom builders import `com.jackcaow.smoothmarkdown.ast.Node` and the other library-owned AST types. Applications upgrading from 0.1.0 must update their former `org.commonmark` imports.

## Install

The reader requires Android API 24+, **compileSdk 37 or newer**, Jetpack Compose, and Java 17. Kotlin `2.4.20` and its matching Compose compiler plugin are the tested compiler setup. Add the public JitPack Maven repository to your app project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.JackCaow.android-smooth-markdown") }
        }
    }
}
```

In your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown:0.3.1")
}
```

The reader includes `smoothmarkdown-core` transitively; do not add a separate Core dependency when using the reader. Packaged AARs include native libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; applications do not need Rust or the NDK.

### Parser and HTML export only

For a JVM application without Android or Compose, use the same JitPack repository and only this JAR:

```kotlin
dependencies {
    implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown-core:0.3.1")
}
```

```kotlin
import com.jackcaow.smoothmarkdown.MarkdownCoreParser

val ast = MarkdownCoreParser().parse("# Hello")
val html = MarkdownCoreParser().renderHtml("**Hello**")
```

Core needs Java 17 and Kotlin's standard library. Its JVM fallback works without native binaries.

`0.3.0` remains a failed JitPack build tag; `0.3.1` fixes the build environment without replacing that tag.

These are the individual-module coordinates for the `0.3.1` release; see [JitPack's multi-module guide](https://docs.jitpack.io/building/#multi-module-projects) and the [release page](https://github.com/JackCaow/android-smooth-markdown/releases). The old `com.github.JackCaow:android-smooth-markdown:0.2.0` coordinate identifies the previous single-module AAR.

**Maven Central has not been published.** Its prepared coordinates are `io.github.jackcaow:smooth-markdown:0.3.1` and `io.github.jackcaow:smoothmarkdown-core:0.3.1`; use the JitPack coordinates above until a Central release is announced. `google()` resolves Android framework dependencies; it does not host this library.

For source integration, pin this repository to a reviewed commit, include both `:smoothmarkdown-core` and `:smoothmarkdown` in `settings.gradle.kts`, and depend on `project(":smoothmarkdown")`. Building these source modules also requires the [Rust/NDK toolchain](docs/rust-parser-build.md). The `app` module is only the Demo.

See the [public library contract](docs/public-library-contract.md) and [migration guide](docs/public-api-migration.md) for the structured APIs in `0.3.1`.

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

### Customize appearance

Document styles and component tokens are exposed through `MarkdownStyleSheet`:

```kotlin
val base = MarkdownStyleSheet.light()
val style = base.copy(
    blockSpacing = 12.dp,
    designTokens = base.designTokens.copy(
        heading = base.designTokens.heading.copy(decoratedThroughLevel = 0),
        details = base.designTokens.details.copy(cornerRadius = 12.dp),
    ),
)
SmoothMarkdown(markdown = content, styleSheet = style)
```

Import `com.jackcaow.smoothmarkdown.MarkdownStyleSheet` and `androidx.compose.ui.unit.dp`. The `0.3.1` release includes component tokens. See the [styling guide](docs/styling.md) for presets, nested overrides, precedence, streaming, plugin panels, Mermaid palettes, and editor preview styling. `SmoothMarkdownEditor(styleSheet = style)` uses reader styling in Preview/Split; editor controls use `MarkdownEditorTheme`.

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
| Style rendered content | `styleSheet`, `styleSheet.designTokens`, `useEnhancedComponents` |
| Replace built-in rendering | `imageBuilder`, `codeBlockBuilder`, `builderRegistry`, `plugins` |

The reader covers common Markdown, GFM tables and task lists, images, footnotes, code, math, and opt-in safe HTML. Parser and renderer plugins add mentions, hashtags, Mermaid diagrams, and AI-specific blocks. See the [implementation reference](docs/reference.md) for exact supported constructs, selection behavior, customization, and current limits.

## Demo

Open this repository in Android Studio and run the `app` configuration, or build it with:

```bash
./gradlew :app:assembleDebug
```

The Demo contains reader, streaming, editor, conversation, AI chat, and Mermaid screens. It is useful for checking rendering and interaction before embedding the library.

## Compatibility and limits

- Android API 24+, compileSdk 37+, and Java 17; the Compose and Kotlin versions in this repository's Gradle files are the tested setup.
- HTML rendering is off by default; supported math is parsed by default.
- Complete Mermaid and HTML syntax coverage and fully rich formatted editing are still in progress. Review [known limits](docs/reference.md#status) before depending on advanced behavior.
- A local Demo API key is a debug-only convenience. Never ship or commit a key; the library does not require one.

## Development

Source builds use the owned Rust parser and need its build toolchain; see the [parser build guide](docs/rust-parser-build.md). Packaged AAR consumers need no Rust or NDK installation.

```bash
./gradlew :smoothmarkdown:testDebugUnitTest
./gradlew :app:assembleDebug
```

Source: [`smoothmarkdown`](smoothmarkdown/) · Demo: [`app`](app/) · [Implementation reference](docs/reference.md)

### Streaming performance

Eligible plain Markdown streams reuse committed AST blocks and send only their mutable tail across the native bridge. This bypasses the global parse cache and preserves the existing public API. Plugins, formulas/footnotes, enabled HTML and details keep the established full-document parser; references invalidate earlier blocks. Large single containers may see no improvement. Parsing is currently synchronous; background scheduling is not implemented.

See [same-input parser measurements and compatibility boundaries](benchmarks/android-stream-parser-2026-10-01.md). The measured host speedup is not a Compose frame-rate or device claim.
