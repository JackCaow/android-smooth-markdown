package com.jackcaow.smoothmarkdown.mermaid

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidGalleryCoverageTest {
    @Test fun flutterGalleryExamplesHaveNativeDiagrams() {
        val assets = File("../app/src/main/assets/examples/mermaid")
        val missing = (1..40).mapNotNull { number ->
            val source = File(assets, "mermaid-%02d.mmd".format(number)).readText()
            if (MermaidParser.parse(source) == null) number else null
        }
        assertTrue("Unrendered Flutter gallery examples: $missing", missing.isEmpty())
    }
}
