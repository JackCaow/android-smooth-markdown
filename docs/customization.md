# Android custom appearance and components

## Chart token contract

`MarkdownMermaidTokens.charts` owns `MarkdownMermaidChartTokens`. The same semantic
field names are used across the native libraries; colors and dimensions remain native types.

| Field | Scope |
| --- | --- |
| `seriesColors` | Cyclic palette for pie, timeline, radar and XY series; empty preserves defaults |
| `ganttStatusColors` | Overrides individual Gantt status colors; missing keys inherit defaults |
| `kanbanPriorityColors` | Overrides individual Kanban priorities; missing keys inherit defaults |
| `todayColor`, `todayLineWidth` | Gantt Today marker and its label |
| `overLimitColor`, `atLimitColor`, `belowLimitColor` | Kanban WIP state colors |
| `gridColor`, `gridWidth` | Radar/XY/Gantt grid painting |
| `columnBackground`, `cardBackground` | Kanban column/card fills |
| `sectorBorderColor`, `sectorBorderWidth` | Pie slice separators |
| `seriesLineWidth` | Radar/XY series strokes |
| `pointRadius` | Radar/XY data markers; nil preserves each platform's previous markers |
| `fillOpacity` | Radar series fill and XY bars |
| `barCornerRadius`, `taskCornerRadius` | XY bars and Gantt task bars |
| `columnCornerRadius`, `cardCornerRadius`, `borderWidth` | Kanban decoration; iOS Gantt task borders also use `borderWidth` |
| `priorityStripeWidth` | Kanban task priority stripe |

Null/nil overrides and empty palettes/maps preserve each platform's previous appearance.
They do not enforce pixel-identical defaults. Palette indexes wrap in series order.
`normalized()` returns a safe copy: invalid/negative dimensions become zero, opacity
is clamped to 0...1, and Android `Color.Unspecified` entries are ignored. Transparent
colors are valid explicit overrides. Existing base palettes and source style directives
retain their existing precedence; chart overrides control the listed chart roles.

These are appearance tokens. They do not select a new layout algorithm or make arbitrary
fonts fit fixed chart geometry. Use a custom renderer for different chart structures.

```kotlin
val charts = MarkdownMermaidChartTokens(
    seriesColors = listOf(Color.Magenta, Color.Cyan),
    ganttStatusColors = mapOf(MermaidGanttStatus.Critical to Color.Red),
    kanbanPriorityColors = mapOf(MermaidKanbanPriority.High to Color.Yellow),
    seriesLineWidth = 3.dp,
    cardCornerRadius = 12.dp,
)
val style = MarkdownStyleSheet.light().let { base ->
    base.copy(designTokens = base.designTokens.copy(
        mermaid = base.designTokens.mermaid.copy(charts = charts),
    ))
}
```

## Custom component with native interaction

The complete compilable example is
[`CustomHeading.kt`](../consumer-reader/src/main/java/consumer/CustomHeading.kt).
It registers only headings, uses `context.renderInlineChildren` to preserve formatting
and selectable child text, declares `NATIVE_TEXT`, and returns the same semantic text
through `documentText`. It exposes a heading accessibility trait and an explicit Copy
button using the system clipboard. The reader selects `DOCUMENT` ownership.

Keep the visible selectable text in the same order as `documentText`. A custom view
with arbitrary Compose `Text`, reordered children or decorative extra text is not
implicitly compatible with controlled cross-block selection. Use `SmoothSelectableText`
for host-produced text or delegate AST children through the context.

Compile the example using public imports only:

```sh
./gradlew :consumer-reader:compileDebugKotlin
```

## Appearance ownership and compatibility

Reader and stream appearance use `MarkdownStyleSheet`. Source/formatted editor controls
and toolbar chrome use `MarkdownEditorTheme`; editor Preview/Split use the reader style.
Host app navigation and conversation controls belong to the app. This separation is
intentional; pass both styles when customizing an editor.

This change is not in the existing published tag. Build the current source branch until
a release includes it. Existing source calls retain their defaults. Android's added
data-class field changes constructor and `copy` JVM signatures: recompile the consumer
and dependent precompiled modules when upgrading; replacing only the AAR is not ABI-safe.
Swift source packages also require recompilation; no new binary compatibility promise
is made.
