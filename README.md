# android-smooth-markdown

Native Kotlin/Jetpack Compose library and demo, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown) version 0.10.0.

## Status

Reader work in progress. The library renders headings, paragraphs, inline emphasis, links, code blocks, blockquotes, ordered/bullet/task lists, GFM tables, network images, and horizontal rules. It supports per-text-block selection, basic `Flow<String>` chunk accumulation, and blocks unsafe link/image schemes. Math, Mermaid, streaming throttle/partial-HTML behavior, cross-block selection, plugins, configurable themes, opt-in HTML and editing still need implementation. Do not treat this as a parity release.

Open this folder in Android Studio or run `./gradlew :app:assembleDebug`. Run `./gradlew :smoothmarkdown:testDebugUnitTest` for parser and URL-policy tests.

The public components are `SmoothMarkdown(markdown, modifier, onLinkClick, onImageClick)` and `StreamMarkdown(chunks, ...)`. CommonMark Java parses Markdown to an AST; Compose renders each block directly. The [Flutter source and tests](https://github.com/JackCaow/flutter-smooth-markdown) remain the behavior reference.
