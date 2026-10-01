# Migrating public configuration

Existing flat entry points remain available. The grouped entry point is selected by supplying `renderOptions`; ordinary calls without this argument keep their old meaning.

## Reader

```kotlin
SmoothMarkdown(
    markdown = source,
    renderOptions = MarkdownRenderOptions(useEnhancedComponents = true),
    selectionOptions = MarkdownSelectionOptions(mode = MarkdownSelectionMode.DOCUMENT),
    styleSheet = style,
    events = MarkdownEvents(onImageClick = { image -> openImage(image.source) }),
    resourceOptions = MarkdownResourceOptions(headers = authenticatedHeaders),
    strings = MarkdownStrings(copy = "复制", copied = "已复制"),
)
```

Import `com.jackcaow.smoothmarkdown.*`. The same groups are accepted by the Flow and StateFlow stream entry points. A hot StateFlow normally does not complete.

For parser-only use, depend on `com.github.JackCaow.android-smooth-markdown:smoothmarkdown-core:0.3.1` and call `MarkdownCoreParser().parse(source)` or `.renderHtml(source)`. A source integration must include both core and reader modules when using the reader.

## Appearance

Move new color overrides into `designTokens.document`, fonts into `designTokens.typography`, and component decoration into its named token group. Existing fields continue to supply fallbacks. Removing a token override restores that original fallback.

Keep one event handler per action in the new API. The old callback aliases may intentionally both run when both are supplied; do not copy that pattern to the structured API.

## Registries and validation

Registry mutations are observable; remove workarounds that recreate instances solely to refresh readers. Invalid visual values are normalized at consumption. Update old tests that expected a crash for negative/NaN dimensions to assert the safe resolved value instead.

See the [public library contract](public-library-contract.md) for precedence, syntax, module boundaries and release gates.
