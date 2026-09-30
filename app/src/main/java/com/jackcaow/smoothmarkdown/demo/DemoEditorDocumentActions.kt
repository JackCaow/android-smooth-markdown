package com.jackcaow.smoothmarkdown.demo

import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImageSelection

/** Keeps the Flutter example callbacks as the default; device files are an explicit Demo option. */
internal class DemoEditorDocumentActions(
    private val pickDeviceImage: suspend () -> MarkdownEditorImageSelection?,
    private val importDeviceMarkdown: suspend () -> String?,
    private val exportDeviceMarkdown: suspend (String) -> Unit,
) {
    suspend fun pickImage(useDeviceFiles: Boolean): MarkdownEditorImageSelection? =
        if (useDeviceFiles) pickDeviceImage()
        else MarkdownEditorImageSelection("https://picsum.photos/640/360", "Sample image", "Demo image")

    suspend fun importMarkdown(useDeviceFiles: Boolean): String? =
        if (useDeviceFiles) importDeviceMarkdown()
        else "## Imported markdown\n\nThis came from the host callback."

    suspend fun exportMarkdown(useDeviceFiles: Boolean, markdown: String) {
        if (useDeviceFiles) exportDeviceMarkdown(markdown)
    }
}
