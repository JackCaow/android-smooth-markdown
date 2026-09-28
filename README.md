# android-smooth-markdown

Native Kotlin/Jetpack Compose library and demo, based on [Flutter Smooth Markdown](https://github.com/JackCaow/flutter-smooth-markdown) version 0.10.0.

## Status

Reader and editor work in progress. The library renders headings, paragraphs, inline emphasis, links, enhanced code blocks with language labels, copy and initial syntax colors, blockquotes, ordered/bullet/task lists, GFM tables, standalone or mixed inline network/bundled-asset bitmap and SVG images, footnotes, collapsible `details` blocks, inline `$...$` and display `$$...$$` math, and horizontal rules. Opt-in parser/renderer plugins provide mentions, hashtags, emoji shortcodes, admonitions, AI thinking/artifact/tool-call blocks, and an initial Mermaid flowchart/sequence/pie/timeline/Gantt/Kanban/Radar/XYChart/ER renderer. Inline images without explicit dimensions use a 32 dp placeholder. It supports per-text-block selection, `Flow<String>` chunk accumulation with a 50 ms update throttle and completion flush, and blocks unsafe link/image schemes. Opt-in HTML handles common inline formatting, safe link styling, bounded font/color styles, `br`, `hr`, standalone or mixed `img` with pixel dimensions and alt fallback, and `div`/`p`/`center`/`blockquote` containers; streaming withholds incomplete tags outside code. The source editor supports UTF-16 selections, undo/redo, grouped transactions, search, formatting commands, and source/preview/split layouts. Source-backed GFM tables support insertion, cell replacement, row/column edits, alignment, and deletion at the current selection. The initial Formatted mode edits paragraphs, ATX headings, fenced code, and GFM table cells with add/remove row and column controls; other blocks remain source-only. Full inline/nested formatted editing, table selection/header flags and rich cell editing, full Mermaid syntax, intrinsic image sizing, full HTML behavior, cross-block selection, richer AI chat presentation, and complete Flutter style-sheet coverage still need implementation. Do not treat this as a parity release.

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
        it.registerAll(listOf(MentionPlugin(), HashtagPlugin(), EmojiPlugin(), AdmonitionPlugin(), MermaidPlugin(), ThinkingPlugin(), ArtifactPlugin(), ToolCallPlugin()))
    }
}
SmoothMarkdown(markdown = content, plugins = plugins)
```

`InlineParserPlugin` provides a one-character trigger, `canParse`, `parse`, and `render` hooks. `BlockParserPlugin` provides `canStart`, `createNode`, `isClosingLine`, `complete`, and a Compose `RenderBlock` hook; the node-aware `isClosingLine(node, line)` variant supports multiple delimiter styles. Its `parseFencedCodeBlock` hook converts CommonMark fenced blocks while preserving ordinary code on a `null` result. Custom nodes extend `PluginInlineNode` or `PluginBlockNode`. A `null` parse result lets the next plugin or CommonMark handle the source. Higher priorities run first; equal priorities keep registration order. Registry IDs must be unique within the block or inline group. `copy`, `clear`, lookup, and unregister operations are available. Configure the registry before passing it to Compose; provide a new registry instance after changing its contents so the Markdown AST is rebuilt.

The built-in plugins match Flutter's mention, hashtag, emoji, admonition, and Mermaid fence syntax. Emoji supports a custom shortcode map. Admonition content is parsed as Markdown. The opt-in Thinking, Artifact, and ToolCall plugins parse Flutter's corresponding AI chat block syntaxes. Thinking starts collapsed and can be expanded; artifact and tool inputs are rendered as selectable text without executing HTML or tool calls. Code artifacts reuse the native code block renderer. These readers do not yet provide artifact download, live tool status updates, or rich HTML/component previews.

## Mermaid prototype

`MermaidDiagramView(source)` is a standalone Compose view backed by `MermaidParser` and `MermaidLayout`. Register `MermaidPlugin()` to render supported fenced `mermaid` blocks in `SmoothMarkdown` or `StreamMarkdown`; without it, fences remain ordinary code. Empty or unsupported diagram families also remain code. Both backtick and tilde fences are recognized; the parsed node retains fence type, info, and optional `theme=` metadata. The renderer currently uses the Compose color scheme rather than the fence's `theme=` setting. Supported flowchart syntax: `graph`/`flowchart` with `TD`/`TB`/`BT`/`LR`/`RL`; plain nodes and rectangle, rounded, stadium, diamond, hexagon, circle, subroutine, cylinder, asymmetric, parallelogram, and trapezoid node declarations; chained solid/thick/dotted edges with `|label|`; `classDef`, `class`, inline `style`, and nested `subgraph` membership. Supported sequence syntax: `sequenceDiagram`, `participant`/`actor` with aliases, and `->`, `-->`, `->>`, `-->>`, `-x`, `--x`, `-)`, `--)` messages with optional labels. Pie supports title, `showData`, quoted or unquoted labels, positive decimal slices, percentages, and a legend. Timeline supports title, period/event rows, multiple events per period, and descriptions. Gantt supports titles, sections, explicit dates, day/week/month/year durations, task status, dependencies, milestones, and a scrolling date grid. Kanban supports columns, task metadata, priority colors, WIP badges, and `ticketBaseUrl` frontmatter parsing. Radar supports labeled axes and curves, scale options, polygon/circle graticules, and a legend. XYChart supports category labels, numeric y range, grouped bars, line series, negative values, and horizontal orientation. ER diagrams support entity aliases, attribute compartments, labeled solid or dotted relations, and all four cardinalities at both endpoints. The Canvas renderer draws these diagram types natively with scrolling for larger content.

For a quick local preview after installing the Demo APK, run `adb shell am start -n com.jackcaow.smoothmarkdown.demo/.MermaidDemoActivity` and use the button to cycle through flowchart, sequence, pie, timeline, Gantt, Kanban, Radar, XYChart, and ER. Gantt `excludes`, `axisFormat`, and `todayMarker` directives are retained in data but not yet reflected in the chart; Kanban ticket URLs are retained but not opened. XYChart numeric x-axis ranges are retained but still use series indices for point positions. ER layout uses a simple non-overlapping row or column and does not yet provide full Dagre routing for dense graphs. Other Mermaid diagram families, advanced flowchart routing and shapes, self loops, sequence notes/activation/control blocks, responsive layout, and full Flutter visual parity remain open.
