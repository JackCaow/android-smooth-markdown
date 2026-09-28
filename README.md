# android-smooth-markdown

Native Kotlin/Jetpack Compose library and demo, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown) version 0.10.0.

## Status

Reader and editor work in progress. The library renders headings, paragraphs, inline emphasis, links, code blocks, blockquotes, ordered/bullet/task lists, GFM tables, network images, and horizontal rules. It supports per-text-block selection, `Flow<String>` chunk accumulation with a 50 ms update throttle and completion flush, and blocks unsafe link/image schemes. Opt-in HTML handles common inline formatting, safe link styling, bounded font/color styles, `br`, `hr`, and `div`/`p`/`center`/`blockquote` containers; streaming withholds incomplete tags outside code. The source editor supports UTF-16 selections, undo/redo, grouped transactions, search, formatting commands, and source/preview/split layouts. Source-backed GFM tables support insertion, cell replacement, row/column edits, alignment, and deletion at the current selection. Formatted-block editing, semantic table selection/header flags and inline-preserving cell edits, math and Mermaid rendering, inline HTML images, full HTML behavior, cross-block selection, plugins, and configurable themes still need implementation. Do not treat this as a parity release.

Open this folder in Android Studio or run `./gradlew :app:assembleDebug`. Run `./gradlew :smoothmarkdown:testDebugUnitTest` for parser and URL-policy tests.

The public components include `SmoothMarkdown(markdown, modifier, onLinkClick, onImageClick, enableHtml)`, `StreamMarkdown(chunks, ..., enableHtml)`, `MarkdownEditorController`, and `SmoothMarkdownEditor(controller, modifier, onSave)`. HTML is disabled by default. CommonMark Java parses Markdown to an AST; Compose renders each block directly. The [Flutter source and tests](https://github.com/JackCaow/flutter-smooth-markdown) remain the behavior reference.
