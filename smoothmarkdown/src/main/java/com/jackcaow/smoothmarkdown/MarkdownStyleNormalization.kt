package com.jackcaow.smoothmarkdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

internal fun Dp.safe(): Dp = if (value.isFinite() && value >= 0) this else 0.dp
internal fun Float.safeAlpha(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f
internal fun TextUnit.safe(allowNegative: Boolean = false): TextUnit = if (this == TextUnit.Unspecified ||
    (value.isFinite() && (allowNegative || value >= 0))) this else TextUnit.Unspecified
internal fun TextStyle.safe(): TextStyle = copy(fontSize = fontSize.safe(), lineHeight = lineHeight.safe(), letterSpacing = letterSpacing.safe(true))
internal fun SpanStyle.safe(): SpanStyle = copy(fontSize = fontSize.safe(), letterSpacing = letterSpacing.safe(true))
internal fun PaddingValues.safe(): PaddingValues = PaddingValues(
    start = calculateLeftPadding(LayoutDirection.Ltr).safe(), end = calculateRightPadding(LayoutDirection.Ltr).safe(),
    top = calculateTopPadding().safe(), bottom = calculateBottomPadding().safe(),
)

/** Normalizes invalid remote/user configuration at consumption; source configuration remains unchanged. */
fun MarkdownDesignTokens.normalized(): MarkdownDesignTokens = copy(
    heading = heading.copy(decoratedThroughLevel = heading.decoratedThroughLevel.coerceIn(0, 6),
        padding = heading.padding.safe(), barWidth = heading.barWidth.safe(), barRadius = heading.barRadius.safe(), barSpacing = heading.barSpacing.safe(),
        ruleThickness = heading.ruleThickness.safe(), barEndAlpha = heading.barEndAlpha.safeAlpha(), ruleStartAlpha = heading.ruleStartAlpha.safeAlpha(), ruleEndAlpha = heading.ruleEndAlpha.safeAlpha()),
    quote = quote.copy(backgroundStartAlpha = quote.backgroundStartAlpha.safeAlpha(), backgroundEndAlpha = quote.backgroundEndAlpha.safeAlpha(),
        borderAlpha = quote.borderAlpha.safeAlpha(), iconAlpha = quote.iconAlpha.safeAlpha(), iconStyle = quote.iconStyle.safe(), iconSpacing = quote.iconSpacing.safe(),
        borderWidth = quote.borderWidth?.safe(), padding = quote.padding?.safe()),
    code = code.copy(headerPadding = code.headerPadding.safe(), languageStyle = code.languageStyle?.safe(), copyStyle = code.copyStyle?.safe(),
        copyFeedbackMillis = code.copyFeedbackMillis.coerceAtLeast(0), scrollbarPadding = code.scrollbarPadding.safe(), scrollbarThickness = code.scrollbarThickness.safe(),
        scrollbarMinThumbWidth = code.scrollbarMinThumbWidth.safe(), scrollbarTrackAlpha = code.scrollbarTrackAlpha.safeAlpha(), scrollbarThumbAlpha = code.scrollbarThumbAlpha.safeAlpha(),
        borderWidth = code.borderWidth?.safe(), cornerRadius = code.cornerRadius?.safe(), padding = code.padding?.safe()),
    link = link.copy(hoverDurationMillis = link.hoverDurationMillis.coerceAtLeast(0), underlineAlpha = link.underlineAlpha.safeAlpha(), hoverUnderlineAlpha = link.hoverUnderlineAlpha.safeAlpha(),
        underlineThickness = link.underlineThickness.safe(), hoverUnderlineThickness = link.hoverUnderlineThickness.safe(), underlineOffset = link.underlineOffset.safe(),
        iconSize = link.iconSize.safe(), iconGap = link.iconGap.safe(), iconTopOffset = link.iconTopOffset.safe(), iconStrokeWidth = link.iconStrokeWidth.safe()),
    details = details.copy(cornerRadius = details.cornerRadius.safe(), outerPadding = details.outerPadding.safe(), borderWidth = details.borderWidth.safe(),
        summaryPadding = details.summaryPadding.safe(), bodyPadding = details.bodyPadding.safe(), iconSpacing = details.iconSpacing.safe(), iconStyle = details.iconStyle?.safe(), dividerThickness = details.dividerThickness.safe()),
    keyboard = keyboard.copy(borderWidth = keyboard.borderWidth.safe(), cornerRadius = keyboard.cornerRadius.safe(), padding = keyboard.padding.safe(),
        extraWidth = keyboard.extraWidth.safe(), extraHeight = keyboard.extraHeight.safe(), textStyle = keyboard.textStyle.safe(), backgroundAlpha = keyboard.backgroundAlpha.safeAlpha()),
    math = math.copy(textStyle = math.textStyle?.safe(), blockPadding = math.blockPadding.safe(),
        displayScale = math.displayScale.takeIf { it.isFinite() && it > 0 } ?: 1.2f,
        fontFamily = math.fontFamily.takeIf { it in setOf("serif", "sans-serif", "monospace") } ?: "serif"),
    footnotePadding = footnotePadding.safe(), imagePlaceholderMinSize = imagePlaceholderMinSize.safe(),
    mermaid = mermaid.normalized(), plugins = plugins.normalized(), typography = typography.normalized(),
)

fun MarkdownMermaidTokens.normalized(): MarkdownMermaidTokens = copy(outerPadding = outerPadding.safe(),
    nodeCornerRadius = nodeCornerRadius.safe(), nodePadding = nodePadding.safe(), edgeWidth = edgeWidth.safe(),
    arrowSize = arrowSize.safe(), labelPadding = labelPadding.safe(), rankGap = rankGap.safe(), siblingGap = siblingGap.safe(),
    maxHeight = maxHeight.takeIf { it.value.isFinite() && it.value > 0 } ?: 420.dp,
    typography = typography?.let { it.copy(bodySmall = it.bodySmall?.safe(), bodyMedium = it.bodyMedium?.safe(), bodyLarge = it.bodyLarge?.safe(),
        labelSmall = it.labelSmall?.safe(), labelMedium = it.labelMedium?.safe(), labelLarge = it.labelLarge?.safe(),
        titleSmall = it.titleSmall?.safe(), titleMedium = it.titleMedium?.safe(), titleLarge = it.titleLarge?.safe(),
        headlineSmall = it.headlineSmall?.safe(), headlineMedium = it.headlineMedium?.safe(), headlineLarge = it.headlineLarge?.safe(),
        displaySmall = it.displaySmall?.safe(), displayMedium = it.displayMedium?.safe(), displayLarge = it.displayLarge?.safe()) })

private fun MarkdownTypographyTokens.normalized(): MarkdownTypographyTokens = copy(paragraph = paragraph?.safe(), code = code?.safe(), tableHeader = tableHeader?.safe(),
    tableCell = tableCell?.safe(), listBullet = listBullet?.safe(), keyboard = keyboard?.safe(), headings = headings?.let {
        MarkdownHeadingStyles(it.h1.safe(), it.h2.safe(), it.h3.safe(), it.h4.safe(), it.h5.safe(), it.h6.safe())
    })

fun MarkdownPluginPanelTokens.normalized(): MarkdownPluginPanelTokens = copy(outerPadding = outerPadding.safe(), headerPadding = headerPadding.safe(),
    contentPadding = contentPadding.safe(), metadataPadding = metadataPadding.safe(), cornerRadius = cornerRadius.safe(), borderWidth = borderWidth.safe(),
    titleStyle = titleStyle?.safe(), contentStyle = contentStyle?.safe(), metadataStyle = metadataStyle?.safe(), statusStyle = statusStyle?.safe(), iconStyle = iconStyle?.safe(),
    dividerThickness = dividerThickness.safe(), iconSpacing = iconSpacing.safe())
fun MarkdownAdmonitionTokens.normalized(): MarkdownAdmonitionTokens = copy(outerPadding = outerPadding.safe(), contentPadding = contentPadding.safe(),
    contentSpacing = contentSpacing.safe(), cornerRadius = cornerRadius.safe(), borderWidth = borderWidth.safe(), backgroundAlpha = backgroundAlpha.safeAlpha(),
    accentWidth = accentWidth.safe(), accentHeight = accentHeight.safe(), titleStyle = titleStyle?.safe(), accentColors = accentColors.toMap())
private fun MarkdownPluginTokens.normalized(): MarkdownPluginTokens = copy(mentionStyle = mentionStyle.safe(), hashtagStyle = hashtagStyle.safe(),
    admonition = admonition.normalized(), thinking = thinking.normalized(), artifact = artifact.normalized(), toolCall = toolCall.normalized())
internal fun MarkdownTableBorder.normalized(color: androidx.compose.ui.graphics.Color? = null): MarkdownTableBorder = copy(
    top = top?.copy(width = top.width.safe(), color = color ?: top.color), right = right?.copy(width = right.width.safe(), color = color ?: right.color),
    bottom = bottom?.copy(width = bottom.width.safe(), color = color ?: bottom.color), left = left?.copy(width = left.width.safe(), color = color ?: left.color),
    horizontalInside = horizontalInside?.copy(width = horizontalInside.width.safe(), color = color ?: horizontalInside.color), verticalInside = verticalInside?.copy(width = verticalInside.width.safe(), color = color ?: verticalInside.color),
)
