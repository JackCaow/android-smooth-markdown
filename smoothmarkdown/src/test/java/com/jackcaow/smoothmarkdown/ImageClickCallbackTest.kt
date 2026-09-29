package com.jackcaow.smoothmarkdown

import org.commonmark.node.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageClickCallbackTest {
    @Test fun markdownImagePreservesAltAndTitleForBothCallbacks() {
        val paragraph = parseMarkdown("![Diagram](https://example.com/a.svg \"Overview\")").firstChild as Paragraph
        val image = inlineRender(paragraph, enableHtml = false).images.values.single()
        val calls = mutableListOf<String>()

        dispatchImageClick(image, { calls += "legacy:$it" }, { url, alt, title ->
            calls += "metadata:$url:$alt:$title"
        })

        assertEquals(listOf(
            "legacy:https://example.com/a.svg",
            "metadata:https://example.com/a.svg:Diagram:Overview",
        ), calls)
    }

    @Test fun htmlImagePreservesMetadataAndLegacyOnlyStillWorks() {
        val image = SafeHtml.imageTag("<img src='icon.svg' alt='Badge' title='Release'>")!!
        val metadata = mutableListOf<Triple<String, String?, String?>>()
        var legacySource: String? = null
        dispatchImageClick(image, { legacySource = it }, { url, alt, title ->
            metadata += Triple(url, alt, title)
        })
        assertEquals("icon.svg", legacySource)
        assertEquals(listOf(Triple("icon.svg", "Badge", "Release")), metadata)

        dispatchImageClick(image, { legacySource = it }, null)
        assertEquals("icon.svg", legacySource)
    }
}
