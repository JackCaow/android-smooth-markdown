# Native AST migration

The current source replaces CommonMark, Coil and RaTeX with library-owned implementations. Published 0.1.0 continues to use the earlier dependency set until a new release is published.

## Host integration

The Android reader continues to require Jetpack Compose and Kotlin coroutines. Minimum Android version remains API 24. Reader, streaming, editor, callbacks and plugin registration keep their existing entry points.

Custom builders and plugins must replace `org.commonmark.node` imports with `com.jackcaow.smoothmarkdown.ast`. The library owns `Markup` (`Node` is an alias), `Heading`, `Paragraph`, `Text`, `ListItem`, `TableBlock`, and other AST classes. Tree pointers are explicitly nullable; use `requireNotNull(node.firstChild)` when the host requires a child. `children()` returns a Kotlin sequence; use `.toList()` for indexed access.

```kotlin
import com.jackcaow.smoothmarkdown.ast.Heading
import com.jackcaow.smoothmarkdown.ast.Node
```

This is a source migration for code that directly used CommonMark types. Previously compiled custom builders must be rebuilt against the new AST.

## Rendering

- Markdown and HTML export use the same owned AST and original Kotlin parser.
- Bitmap loading uses Android BitmapFactory and HttpURLConnection.
- SVG renders using Android system WebView, with scripts and navigation disabled.
- TeX formulas use an owned parser and system MathML rendering offline.
- Markdown image URL validation continues to reject arbitrary local filesystem URLs.
- Hosts can use `SmoothMarkdownImage(source, contentDescription, modifier)` inside `imageBuilder` for trusted local file/content/resource URIs. This does not widen Markdown URL permissions.
- The Demo also uses the native image component and does not require Coil.
- Test-only standard specification fixtures are excluded from the AAR.

## Verification

- CommonMark 0.31.2: all 652 official examples match exact HTML.
- GFM extension fixtures: all 24 examples match exact HTML.
- UTF-16 source ranges are checked against the original source.
- Library JVM regression: 441 tests, zero failures, one skipped.
- Demo JVM regression: 23 tests, zero failures.
- Native image loader: seven Android instrumentation tests passed on an emulator.
- SVG natural size/scaling and offline MathML: two light/dark screenshot tests passed; the resulting images were visually reviewed.
- Library Release AAR and Demo Debug APK build successfully.
- All eight Demo fixture groups match the Flutter example content.

Public registry publication remains a separate delivery gate; local Maven publication does not confirm that a public registry has accepted the artifacts.
