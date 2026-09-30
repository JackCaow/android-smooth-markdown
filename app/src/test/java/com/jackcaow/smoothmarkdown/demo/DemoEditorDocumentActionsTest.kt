package com.jackcaow.smoothmarkdown.demo

import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImageSelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoEditorDocumentActionsTest {
    @Test fun defaultModeKeepsFlutterExampleCallbacksWithoutOpeningDeviceFiles() = runBlocking {
        var deviceCalls = 0
        val actions = DemoEditorDocumentActions(
            pickDeviceImage = { deviceCalls++; error("Device picker must stay opt-in") },
            importDeviceMarkdown = { deviceCalls++; error("Device picker must stay opt-in") },
            exportDeviceMarkdown = { deviceCalls++; error("Device writer must stay opt-in") },
        )

        assertEquals(MarkdownEditorImageSelection("https://picsum.photos/640/360", "Sample image", "Demo image"),
            actions.pickImage(useDeviceFiles = false))
        assertEquals("## Imported markdown\n\nThis came from the host callback.",
            actions.importMarkdown(useDeviceFiles = false))
        actions.exportMarkdown(useDeviceFiles = false, markdown = "# Exact\r\n")
        assertEquals(0, deviceCalls)
    }

    @Test fun deviceModeDelegatesRealPickerImportAndExactExport() = runBlocking {
        val selected = MarkdownEditorImageSelection("demo-images/09b7f42b-c170-4caa-9d79-253b628e9dbe.png")
        var exported: String? = null
        val actions = DemoEditorDocumentActions(
            pickDeviceImage = { selected },
            importDeviceMarkdown = { "# Imported\r\n\r\nBody  " },
            exportDeviceMarkdown = { exported = it },
        )

        assertEquals(selected, actions.pickImage(useDeviceFiles = true))
        assertEquals("# Imported\r\n\r\nBody  ", actions.importMarkdown(useDeviceFiles = true))
        actions.exportMarkdown(useDeviceFiles = true, markdown = "# Exact\r\nBody  ")
        assertEquals("# Exact\r\nBody  ", exported)
    }
}
