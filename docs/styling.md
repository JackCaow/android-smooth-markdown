# Styling the Android reader

The preferred customization entry point is `MarkdownStyleSheet.designTokens`. Legacy stylesheet fields remain supported as fallbacks:

- `MarkdownStyleSheet`: document typography, semantic colors, block spacing, lists, tables, code and quote decorations, and inline spans.
- `MarkdownStyleSheet.designTokens`: semantic document colors (`document`), explicit typography (`typography`), and component details for enhanced headings/quotes/links, code controls and syntax, details, keyboard keys, math, image placeholders, footnotes, built-in plugin panels, and Mermaid base themes.

The `0.3.0` release includes these additive component-token APIs. Applications upgrading from `0.2.0` can keep their legacy stylesheet values and add token overrides gradually.

## Start from a preset

`default()` inherits unspecified values from the surrounding Compose `MaterialTheme`. `light()`, `dark()`, `github(dark = …)`, and `vscode(dark = …)` provide explicit document colors and typography. Choose the preset in your app when its theme changes; the library does not own your app's theme state.

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet
import com.jackcaow.smoothmarkdown.MarkdownSyntaxColors
import com.jackcaow.smoothmarkdown.MarkdownDocumentTokens
import com.jackcaow.smoothmarkdown.SmoothMarkdown

val base = MarkdownStyleSheet.light()
val style = base.copy(
    blockSpacing = 12.dp,
    designTokens = base.designTokens.copy(
        document = MarkdownDocumentTokens(textColor = Color(0xFF202124)),
        typography = base.designTokens.typography.copy(
            paragraph = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
        ),
        heading = base.designTokens.heading.copy(
            accentColor = Color(0xFF6750A4),
            decoratedThroughLevel = 0, // Hide decorative heading bars/rules.
        ),
        code = base.designTokens.code.copy(
            headerPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            copyLabel = "Copy code",
            syntaxColors = MarkdownSyntaxColors.light().copy(
                keyword = Color(0xFF6750A4),
            ),
        ),
        details = base.designTokens.details.copy(
            cornerRadius = 12.dp,
            summaryPadding = PaddingValues(16.dp),
        ),
        math = base.designTokens.math.copy(
            textStyle = TextStyle(fontSize = 18.sp),
            displayScale = 1.1f,
            blockPadding = PaddingValues(vertical = 10.dp),
        ),
    ),
)

// Call inside a composable.
SmoothMarkdown(markdown = source, styleSheet = style, useEnhancedComponents = true)
```

Use nested `copy()` calls to retain all other values from the chosen preset. Replacing a group with a new token instance resets that group's other properties to their defaults.

Explicit tokens override legacy fields; null restores their fallback. Numeric values are normalized at consumption. See the [public library contract](public-library-contract.md) for configuration ownership and precedence.

## Which configuration controls what?

| Component | Public configuration |
| --- | --- |
| Paragraphs and headings | `designTokens.typography`, named six-heading values, `designTokens.document` |
| Links and inline formatting | `linkColor`, `linkStyle`, `boldStyle`, `italicStyle`, `inlineCodeStyle`, other span overrides |
| Lists and tables | `listBulletStyle`, spacing/indent, cell styles/padding, `tableBorder` and header background |
| Code body | `codeStyle`, `codeTextColor`, `codeBlockDecoration`, `codeBlockPadding` |
| Code header, syntax and scrollbar | `designTokens.code` |
| Enhanced heading bars and rules | `designTokens.heading` |
| Enhanced quote gradient and icon | `designTokens.quote` plus existing quote decoration/padding |
| Hover underlines and external-link icons | `designTokens.link` |
| HTML details and keyboard caps | `designTokens.details`, `designTokens.keyboard`, `kbdStyle` |
| Inline and block equations | `designTokens.math` |
| Footnote spacing and image placeholders | `designTokens.footnotePadding`, `designTokens.imagePlaceholderMinSize` |
| Built-in mentions, hashtags, admonitions and AI panels | `designTokens.plugins` |
| Mermaid base palette, typography and fence sizing | `designTokens.mermaid` |

`useEnhancedComponents` selects decorative heading, quote and link behavior. Those token groups take effect when that behavior is enabled. Code options such as syntax highlighting and copy controls remain controlled by `codeBlockOptions`; tokens style the controls that are shown.

## Precedence and inheritance

Existing `MarkdownStyleSheet` fields retain their roles. Tokens fill component styling gaps; they do not replace the document stylesheet.

- Text styles with explicit colors retain their colors. Otherwise the renderer uses its semantic stylesheet color and then `MaterialTheme` as a fallback. Inline span overrides merge over their built-in span defaults.
- `codeBlockDecoration` overrides the legacy code background when its background is supplied. `codeBlockPadding` overrides uniform `codePadding`. Code tokens control the header, syntax palette and overflow indicator.
- Explicit quote decoration/background and border colors take precedence over the enhanced quote defaults. `blockquotePadding` controls the content inset; quote tokens control the ornament and default gradient.
- Keyboard text merges its token style and then `kbdStyle`; explicit `kbdStyle` properties win. Keyboard background, border, padding and corner radius are independently configurable.
- Math text tokens merge over paragraph typography. `math.color` wins over the math text style color, then document text color and the inherited text/theme color. `fontFamily` accepts `serif`, `sans-serif`, or `monospace`; these are system MathML fonts, not Compose resource fonts.
- Nullable component colors/styles inherit the relevant document style or `MaterialTheme`. Fixed default token values are library defaults and remain unchanged until explicitly copied/overridden.

For syntax highlighting, a null `syntaxColors` automatically chooses the built-in light or dark palette from the resolved code background. To specify one yourself, use `MarkdownSyntaxColors.light()` or `.dark()` and copy individual colors. The syntax palette applies only when highlighting is enabled.

Dimensions must be finite and nonnegative; alpha values must be in `0..1`, and the math display scale must be positive. A zero border or rule thickness hides that rule. Use the documented boolean flags to hide optional ornaments and scrollbars.

## Streaming and plugins

Both `StreamMarkdown(chunks = …)` and `StreamMarkdown(prefixes = …)` pass the same `styleSheet` through to the reader. Reuse the same style object for articles and streamed replies:

```kotlin
StreamMarkdown(chunks = chunks, styleSheet = style, useEnhancedComponents = true)
```

Built-in plugin renderers read `designTokens.plugins` when rendered within the reader. Configure `mentionStyle`, `hashtagStyle`, `admonition`, `thinking`, `artifact`, and `toolCall` independently. Plugins still need to be enabled through `plugins`; styling a disabled plugin does not enable its syntax.

Custom builders and custom plugins own their Compose output. They can read the stylesheet from the builder context and apply its tokens, but the library does not automatically restyle arbitrary custom UI.

## Separate surfaces

This token API covers the core reader and the listed built-in plugin components. It is not a single replacement theme for every module:

- Editor chrome and source/formatted editing surfaces use `editor.MarkdownEditorTheme`. Pass `styleSheet = style` to `SmoothMarkdownEditor` to apply reader styling in Preview and Split modes; this parameter does not restyle source/formatted editing controls.
- Mermaid base colors and library-owned typography are configurable through `designTokens.mermaid`. `mermaid.MermaidThemeColors.preset(name)` returns the `default`, `dark`, `forest`, or `neutral` palette for copying. Explicit token colors override the fence theme preset; source `style`/`classDef` directives still override individual nodes. `outerPadding` and `maxHeight` apply to fenced diagrams; standalone views retain their caller modifier. Specialty diagram layout geometry and chart series/status palettes remain fixed inside their diagram implementations.
- App navigation bars, conversation controls, loading/error content supplied by your app, and the Demo's screen controls belong to the host app. Configure these with Compose/Material APIs in your app; the library does not provide a Demo navigation-bar token.

For example, customize Mermaid without modifying its renderer:

```kotlin
val palette = com.jackcaow.smoothmarkdown.mermaid.MermaidThemeColors.preset("neutral")
val diagramStyle = style.copy(
    designTokens = style.designTokens.copy(
        mermaid = style.designTokens.mermaid.copy(
            colors = palette.copy(nodeFill = Color(0xFFF3E8FF)),
            outerPadding = PaddingValues(vertical = 12.dp),
            maxHeight = 520.dp,
        ),
    ),
)
```

If a visual change needs a different component structure, a custom diagram series palette, or new layout geometry, use `builderRegistry`, `imageBuilder`, `codeBlockBuilder`, or a custom renderer plugin.
