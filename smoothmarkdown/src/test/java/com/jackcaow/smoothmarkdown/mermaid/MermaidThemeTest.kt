package com.jackcaow.smoothmarkdown.mermaid

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.jackcaow.smoothmarkdown.MermaidDiagramNode
import com.jackcaow.smoothmarkdown.MermaidPlugin
import com.jackcaow.smoothmarkdown.ParserPluginRegistry
import com.jackcaow.smoothmarkdown.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class MermaidThemeTest {
    private val plugins = ParserPluginRegistry().also { it.register(MermaidPlugin()) }

    @Test fun fencedThemeReachesFlutterColorPreset() {
        val expected = mapOf(
            "default" to 0xFFFFFFFF,
            "dark" to 0xFF1E1E1E,
            "forest" to 0xFFF1F8E9,
            "neutral" to 0xFFFAFAFA,
        )
        expected.forEach { (name, background) ->
            val markdown = "```mermaid theme=$name\nflowchart LR\nA --> B\n```"
            val node = parseMarkdown(markdown, plugins).firstChild as MermaidDiagramNode
            assertEquals(name, node.theme)
            assertEquals(Color(background.toInt()), mermaidThemeColors(node.theme!!).background)
        }
    }

    @Test fun themeNameIsCaseInsensitiveAndUnknownNameMatchesFlutterLightFallback() {
        assertEquals(mermaidThemeColors("forest"), mermaidThemeColors("FoReSt"))
        assertEquals(mermaidThemeColors("default"), mermaidThemeColors("unknown"))
    }

    @Test fun diagramThemeOverridesOnlyDiagramColorRoles() {
        val host = lightColorScheme(secondary = Color.Magenta, onSurface = Color.Red)
        val dark = mermaidThemeColors("dark")
        val actual = host.withMermaidTheme(dark)
        assertEquals(dark.background, actual.surface)
        assertEquals(dark.nodeFill, actual.surfaceVariant)
        assertEquals(dark.nodeStroke, actual.primary)
        assertEquals(dark.text, actual.onSurface)
        assertEquals(dark.edge, actual.outline)
        assertEquals(host.secondary, actual.secondary)
    }
}
