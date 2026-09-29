package com.jackcaow.smoothmarkdown

import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgImageTest {
    @Test fun detectsSvgAcrossNetworkAssetsAndQueryStrings() {
        assertTrue(isSvgImageSource("smooth-markdown-mark.svg"))
        assertTrue(isSvgImageSource("https://example.com/icon.SVG?version=2#preview"))
        assertFalse(isSvgImageSource("https://example.com/icon.png?format=svg"))
    }

    @Test fun mixedSvgImagesKeepTheirSourcesAndAlternativeText() {
        val paragraph = parseMarkdown(
            "network ![logo](https://example.com/logo.svg) and " +
                "![asset](smooth-markdown-mark.svg) plus " +
                "<img src='smooth-markdown-mark.svg' alt='HTML SVG' width='24'>",
        ).firstChild as Paragraph

        val rendered = inlineRender(paragraph, enableHtml = true)
        assertEquals(3, rendered.images.size)
        assertEquals(
            listOf("logo", "asset", "HTML SVG"),
            rendered.images.values.map { it.alt },
        )
        assertEquals(24f, rendered.images.values.last().width)
        assertTrue(rendered.images.values.all { isSvgImageSource(it.source) })
        assertFalse(rendered.text.text.contains("<img"))
    }
}
