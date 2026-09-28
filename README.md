# android-smooth-markdown

Native Kotlin/Jetpack Compose library and demo, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown) version 0.10.0.

## Status

Reader and editor work in progress. The library renders headings, paragraphs, inline emphasis, links, enhanced code blocks with language labels, copy and initial syntax colors, blockquotes, ordered/bullet/task lists, GFM tables, standalone or mixed inline network/bundled-asset bitmap and SVG images, footnotes, collapsible `details` blocks, inline `$...$` and display `$$...$$` math, and horizontal rules. Opt-in parser/renderer plugins provide mentions, hashtags, emoji shortcodes, and admonitions. Inline images without explicit dimensions use a 32 dp placeholder. It supports per-text-block selection, `Flow<String>` chunk accumulation with a 50 ms update throttle and completion flush, and blocks unsafe link/image schemes. Opt-in HTML handles common inline formatting, safe link styling, bounded font/color styles, `br`, `hr`, standalone or mixed `img` with pixel dimensions and alt fallback, and `div`/`p`/`center`/`blockquote` containers; streaming withholds incomplete tags outside code. The source editor supports UTF-16 selections, undo/redo, grouped transactions, search, formatting commands, and source/preview/split layouts. Source-backed GFM tables support insertion, cell replacement, row/column edits, alignment, and deletion at the current selection. Formatted-block editing, semantic table selection/header flags and inline-preserving cell edits, Mermaid rendering, intrinsic image sizing, full HTML behavior, cross-block selection, remaining AI chat plugins, and complete Flutter style-sheet coverage still need implementation. Do not treat this as a parity release.

Open this folder in Android Studio or run `./gradlew :app:assembleDebug`. Run `./gradlew :smoothmarkdown:testDebugUnitTest` for parser and URL-policy tests.

The public components include `SmoothMarkdown(markdown, modifier, onLinkClick, onImageClick, enableHtml, styleSheet)`, `StreamMarkdown(chunks, ..., enableHtml, styleSheet)`, `MarkdownEditorController`, and `SmoothMarkdownEditor(controller, modifier, onSave)`. HTML is disabled by default. CommonMark Java parses Markdown to an AST; Compose renders each block directly. The [Flutter source and tests](https://github.com/JackCaow/flutter-smooth-markdown) remain the behavior reference.

`MarkdownStyleSheet` supports MaterialTheme-backed defaults plus `light()`, `dark()`, `github(dark)`, and `vscode(dark)` presets. Use `copy` to override colors, text styles, or spacing:

```kotlin
SmoothMarkdown(
    markdown = content,
    styleSheet = MarkdownStyleSheet.github(dark = true).copy(
        linkColor = Color.Cyan,
        blockSpacing = 16.dp,
    ),
)
```

The demo's Theme button cycles through all presets. This first native style API covers common reader elements; Flutter's full stylesheet includes additional properties such as alternating table rows and separate inline-format styles.

Math uses the native [RaTeX Android renderer](https://github.com/erweixin/RaTeX) for formulas including fractions, roots, and scripts. Unsupported TeX shows its source text. `CodeBlockOptions` controls copying, language labels, and preliminary highlighting for Kotlin, Swift, Dart, Java, JavaScript/TypeScript, Python, JSON, Bash, and SQL. The public `codeBlockBuilder` and `onCodeCopied` parameters allow custom code rendering and copy handling.

## Parser and renderer plugins

Plugins are disabled by default. Create a registry before composing the reader and pass it to `SmoothMarkdown`:

```kotlin
val plugins = remember {
    ParserPluginRegistry().also {
        it.registerAll(listOf(MentionPlugin(), HashtagPlugin(), EmojiPlugin(), AdmonitionPlugin()))
    }
}
SmoothMarkdown(markdown = content, plugins = plugins)
```

`InlineParserPlugin` provides a one-character trigger, `canParse`, `parse`, and `render` hooks. `BlockParserPlugin` provides `canStart`, `createNode`, `isClosingLine`, `complete`, and a Compose `RenderBlock` hook. Custom nodes extend `PluginInlineNode` or `PluginBlockNode`. A `null` parse result lets the next plugin or CommonMark handle the source. Higher priorities run first; equal priorities keep registration order. Registry IDs must be unique within the block or inline group. `copy`, `clear`, lookup, and unregister operations are available. Configure the registry before passing it to Compose; provide a new registry instance after changing its contents so the Markdown AST is rebuilt.

The built-in plugins match Flutter's mention, hashtag, emoji, and admonition syntax. Emoji supports a custom shortcode map. Admonition content is parsed as Markdown. Flutter's remaining thinking, artifact, tool-call, and Mermaid plugins are not yet implemented on Android.
