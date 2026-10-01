# Smooth Markdown for Android

A native Kotlin and Jetpack Compose Markdown reader, stream renderer, and editor. Use it inside your own Compose screen; the included `app` module is a Demo, not an app dependency.

## Runtime dependencies

Version `0.4.0` includes the owned Rust parser, structured configuration, design tokens, resource loading and localized labels. The parser and JNI transport use no third-party code or crates.

The reader uses a library-owned Markdown AST and Rust parser, Android bitmap/network APIs, and system WebView for SVG and MathML. It does not depend on CommonMark, Coil, RaTeX, AndroidSVG, OkHttp, MathJax or KaTeX.

Jetpack Compose and Kotlin coroutines remain framework dependencies. The reader requires Compose. The independent `smoothmarkdown-core` module provides CommonMark/GFM parsing and HTML export on the JVM without Android, Compose, or coroutines. The existing `smoothmarkdown` module includes the core transitively and retains the original AST package names.

Custom builders import `com.jackcaow.smoothmarkdown.ast.Node` and the other library-owned AST types. Applications upgrading from 0.1.0 must update their former `org.commonmark` imports.

Version `0.4.1` adds backslash formula delimiters, broader native code highlighting and correct inline table alignment. Existing constructor and consumer baselines remain supported.

## Install

### Compatibility baseline

Source builds now use Kotlin **1.9.24**, Compose Foundation **1.6.8**, Compose compiler **1.5.14**, compileSdk **35**, AGP **8.9.1** and Gradle **8.11.1**. The reader AAR declares compileSdk **35** and AGP **8.6.0** as consumer minimums. Android API 24 and Java 17 are unchanged. The reader does not export a Compose BOM; your application chooses its own Compose versions at or above the Foundation baseline.

Version `0.4.0` lowers the consumer baseline and includes background streaming improvements. The older `0.3.1` artifact remains unchanged and still requires compileSdk 37. See [Android compatibility](docs/android-compatibility.md) for verified consumers and custom-builder migration.

### Install 0.4.1

The `0.4.0` reader requires Android API 24+, **compileSdk 35 or newer**, AGP 8.6.0+, Jetpack Compose Foundation 1.6.8+, and Java 17. Kotlin 1.9.22 and 2.1.20 consumers have been verified. Match the Compose compiler to your application's Kotlin version. Add the public JitPack Maven repository to your app project's `settings.gradle.kts`:

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
    implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown:0.4.1")
}
```

The reader includes `smoothmarkdown-core` transitively; do not add a separate Core dependency when using the reader. Packaged AARs include native libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; applications do not need Rust or the NDK.

### Parser and HTML export only

For a JVM application without Android or Compose, use the same JitPack repository and only this JAR:

```kotlin
dependencies {
    implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown-core:0.4.1")
}
```

```kotlin
import com.jackcaow.smoothmarkdown.MarkdownCoreParser

val ast = MarkdownCoreParser().parse("# Hello")
val html = MarkdownCoreParser().renderHtml("**Hello**")
```

Core needs Java 17 and Kotlin's standard library. Its JVM fallback works without native binaries.

`0.3.0` remains a failed JitPack build tag; `0.3.1` fixes the build environment without replacing that tag.

These are the individual-module coordinates for the `0.4.0` release; see [JitPack's multi-module guide](https://docs.jitpack.io/building/#multi-module-projects) and the [release page](https://github.com/JackCaow/android-smooth-markdown/releases). The old `com.github.JackCaow:android-smooth-markdown:0.2.0` coordinate identifies the previous single-module AAR.

**Maven Central has not been published.** Its prepared coordinates are `io.github.jackcaow:smooth-markdown:0.4.0` and `io.github.jackcaow:smoothmarkdown-core:0.4.1`; use the JitPack coordinates above until a Central release is announced. `google()` resolves Android framework dependencies; it does not host this library.

For source integration, pin this repository to a reviewed commit, include both `:smoothmarkdown-core` and `:smoothmarkdown` in `settings.gradle.kts`, and depend on `project(":smoothmarkdown")`. Building these source modules also requires the [Rust/NDK toolchain](docs/rust-parser-build.md). The `app` module is only the Demo.

See the [public library contract](docs/public-library-contract.md) and [migration guide](docs/public-api-migration.md) for the structured APIs in `0.4.0`.

## Quick start

[中文接入手册](docs/integration-guide.zh-CN.md)：安装、样式、流式回复、图片、插件、编辑器及最新固定提交接入。

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

Import `com.jackcaow.smoothmarkdown.MarkdownStyleSheet` and `androidx.compose.ui.unit.dp`. The `0.4.0` release includes component tokens. See the [styling guide](docs/styling.md) for presets, nested overrides, precedence, streaming, plugin panels, Mermaid palettes, and editor preview styling. `SmoothMarkdownEditor(styleSheet = style)` uses reader styling in Preview/Split; editor controls use `MarkdownEditorTheme`.

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

- Source builds use Android API 24+, compileSdk 35 and Java 17; the existing `0.3.1` artifact still needs compileSdk 37. See the [compatibility baseline](docs/android-compatibility.md) for compiler and custom-builder migration.
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

Eligible plain Markdown streams reuse committed AST blocks and send only their mutable tail across the native bridge. This bypasses the global parse cache and preserves the existing public API. Plugins, formulas/footnotes, enabled HTML and details keep the established full-document parser; references invalidate earlier blocks. Large single containers may see no improvement. Eligible streams parse and decode on a serial background worker. Pending updates coalesce complete source prefixes; the final prefix is published before `onComplete`. Native AST state advances even when an intermediate UI update is skipped. Markup adaptation and UI publication remain on the main thread. Compatibility paths still run on the UI thread to preserve plugin behavior.

See [same-input parser measurements and compatibility boundaries](benchmarks/android-stream-parser-2026-10-01.md). The measured host speedup is not a Compose frame-rate or device claim.
