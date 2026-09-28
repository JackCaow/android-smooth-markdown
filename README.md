# android-smooth-markdown

Native Kotlin/Jetpack Compose library and demo, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown) version 0.10.0.

## Status

Research scaffold and first vertical slice. The library renders headings, paragraphs, inline emphasis, links, code blocks, lists and blockquotes. Tables, images, math, Mermaid, streaming, selection and editing still need implementation. Do not treat this as a parity release.

Open this folder in Android Studio or run `./gradlew :app:assembleDebug`.

The public component is `SmoothMarkdown(markdown, modifier, onLinkClick)`. CommonMark Java parses Markdown to an AST; Compose renders each block directly. The [Flutter source and tests](https://github.com/JackCaow/flutter-smooth-markdown) remain the behavior reference.
