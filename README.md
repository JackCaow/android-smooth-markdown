# android-smooth-markdown

Native Kotlin/Jetpack Compose library and demo, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown) version 0.10.0.

## Status

Reader and editor work in progress. The library renders headings, paragraphs, inline emphasis, links, code blocks, blockquotes, ordered/bullet/task lists, GFM tables, network images, and horizontal rules. It supports per-text-block selection, basic `Flow<String>` chunk accumulation, and blocks unsafe link/image schemes. The source editor supports UTF-16 selections, undo/redo, grouped transactions, search, formatting commands, and source/preview/split layouts. Formatted-block editing, table-cell operations, math and Mermaid rendering, streaming throttle/partial-HTML behavior, cross-block selection, plugins, configurable themes, and opt-in HTML still need implementation. Do not treat this as a parity release.

Open this folder in Android Studio or run `./gradlew :app:assembleDebug`. Run `./gradlew :smoothmarkdown:testDebugUnitTest` for parser and URL-policy tests.

The public components include `SmoothMarkdown(markdown, modifier, onLinkClick, onImageClick)`, `StreamMarkdown(chunks, ...)`, `MarkdownEditorController`, and `SmoothMarkdownEditor(controller, modifier, onSave)`. CommonMark Java parses Markdown to an AST; Compose renders each block directly. The [Flutter source and tests](https://github.com/JackCaow/flutter-smooth-markdown) remain the behavior reference.
