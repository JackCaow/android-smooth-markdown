package com.jackcaow.smoothmarkdown.editor

import com.jackcaow.smoothmarkdown.SafeHtml
import com.jackcaow.smoothmarkdown.NativeMarkdownParser
import com.jackcaow.smoothmarkdown.NativeMarkdownHTMLSerializer
import kotlinx.coroutines.CancellationException

/** Image data supplied by an app picker or uploader. */
data class MarkdownEditorImageSelection(val url: String, val alt: String = "", val title: String? = null)

enum class MarkdownEditorImagePickStatus { PICKING, CANCELLED, FAILED, INSERTED }

data class MarkdownEditorImagePickEvent(
    val status: MarkdownEditorImagePickStatus,
    val selection: MarkdownEditorImageSelection? = null,
    val error: Throwable? = null,
)

enum class MarkdownEditorHostAction { IMAGE, IMPORT_MARKDOWN, EXPORT_MARKDOWN, EXPORT_PDF }
enum class MarkdownEditorHostResult { SUCCESS, CANCELLED, STALE, FAILED }

/** Source-backed host operations shared by the Compose editor and focused JVM tests. */
object MarkdownEditorHostActions {
    /** Flutter's PDF action asks the host to create a PDF from Markdown and rendered HTML. */
    suspend fun exportPdf(
        controller: MarkdownEditorController,
        exporter: suspend (String, String) -> Unit,
        onError: ((MarkdownEditorHostAction, Throwable) -> Unit)? = null,
    ): MarkdownEditorHostResult = try {
        val markdown = controller.text
        val document = NativeMarkdownParser(enableGFM = true).parse(markdown)
        val html = NativeMarkdownHTMLSerializer(escapeHtml = true).render(document)
        exporter(markdown, html)
        MarkdownEditorHostResult.SUCCESS
    } catch (cancelled: CancellationException) {
        MarkdownEditorHostResult.CANCELLED
    } catch (error: Throwable) {
        onError?.invoke(MarkdownEditorHostAction.EXPORT_PDF, error)
        MarkdownEditorHostResult.FAILED
    }

    suspend fun pickAndInsertImage(
        controller: MarkdownEditorController,
        picker: suspend () -> MarkdownEditorImageSelection?,
        onEvent: ((MarkdownEditorImagePickEvent) -> Unit)? = null,
        onError: ((MarkdownEditorHostAction, Throwable) -> Unit)? = null,
    ): MarkdownEditorHostResult {
        val before = controller.value
        val selectedText = controller.selectedText
        onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.PICKING))
        val picked = try { picker() } catch (cancelled: CancellationException) {
            onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.CANCELLED))
            return MarkdownEditorHostResult.CANCELLED
        } catch (error: Throwable) {
            onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.FAILED, error = error))
            onError?.invoke(MarkdownEditorHostAction.IMAGE, error)
            return MarkdownEditorHostResult.FAILED
        }
        if (picked == null) {
            onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.CANCELLED))
            return MarkdownEditorHostResult.CANCELLED
        }
        val normalized = picked.copy(url = picked.url.trim(), alt = picked.alt.trim().ifEmpty { selectedText },
            title = picked.title?.trim()?.takeIf { it.isNotEmpty() })
        val markdown = imageMarkdown(normalized)
        if (markdown == null) {
            val error = IllegalArgumentException("Image URL or Markdown metadata is invalid")
            onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.FAILED, error = error))
            onError?.invoke(MarkdownEditorHostAction.IMAGE, error)
            return MarkdownEditorHostResult.FAILED
        }
        if (controller.value != before) {
            onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.CANCELLED))
            return MarkdownEditorHostResult.STALE
        }
        controller.insertMarkdownBlock(markdown)
        onEvent?.invoke(MarkdownEditorImagePickEvent(MarkdownEditorImagePickStatus.INSERTED, normalized))
        return MarkdownEditorHostResult.SUCCESS
    }

    suspend fun importMarkdown(
        controller: MarkdownEditorController,
        importer: suspend () -> String?,
        onError: ((MarkdownEditorHostAction, Throwable) -> Unit)? = null,
    ): MarkdownEditorHostResult {
        val before = controller.value
        val markdown = try { importer() } catch (cancelled: CancellationException) {
            return MarkdownEditorHostResult.CANCELLED
        } catch (error: Throwable) {
            onError?.invoke(MarkdownEditorHostAction.IMPORT_MARKDOWN, error)
            return MarkdownEditorHostResult.FAILED
        }
        if (markdown.isNullOrBlank()) return MarkdownEditorHostResult.CANCELLED
        if (controller.value != before) return MarkdownEditorHostResult.STALE
        controller.insertMarkdownBlock(markdown)
        return MarkdownEditorHostResult.SUCCESS
    }

    suspend fun exportMarkdown(
        controller: MarkdownEditorController,
        exporter: suspend (String) -> Unit,
        onError: ((MarkdownEditorHostAction, Throwable) -> Unit)? = null,
    ): MarkdownEditorHostResult = try {
        exporter(controller.text)
        MarkdownEditorHostResult.SUCCESS
    } catch (cancelled: CancellationException) {
        MarkdownEditorHostResult.CANCELLED
    } catch (error: Throwable) {
        onError?.invoke(MarkdownEditorHostAction.EXPORT_MARKDOWN, error)
        MarkdownEditorHostResult.FAILED
    }

    internal fun imageMarkdown(selection: MarkdownEditorImageSelection): String? {
        val url = selection.url
        val local = !url.startsWith("//") && !url.contains(':') && !url.contains('\\')
        val remote = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        if (!SafeHtml.isSafeImageSource(url) || !local && !remote || url.contains("..") ||
            url.any { it.isWhitespace() || it.isISOControl() || it == '<' || it == '>' || it == '"' || it == '\\' } ||
            selection.alt.any { it.isISOControl() } ||
            selection.title?.any { it.isISOControl() } == true) return null
        val alt = escapeLabel(selection.alt)
        val title = selection.title?.let { " \"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" } ?: ""
        val destination = url.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        return "![$alt]($destination$title)"
    }

    private fun escapeLabel(value: String): String = buildString {
        value.forEach { character ->
            if (character in "\\[]*_`!" ) append('\\')
            append(character)
        }
    }
}
