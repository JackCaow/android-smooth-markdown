package com.jackcaow.smoothmarkdown.demo

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorImageSelection
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Android Storage Access Framework adapter for the Demo editor's public host callbacks. */
internal class DemoEditorActivityHost(private val activity: ComponentActivity) {
    private val store = DemoEditorDocumentStore(activity.filesDir)
    private val imageRequest = PendingUriRequest()
    private val importRequest = PendingUriRequest()
    private val exportRequest = PendingUriRequest()
    private val imageLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        imageRequest.complete(it)
    }
    private val importLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        importRequest.complete(it)
    }
    private val exportLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        exportRequest.complete(it)
    }

    suspend fun pickImage(): MarkdownEditorImageSelection? {
        val uri = imageRequest.await { imageLauncher.launch(arrayOf("image/*")) } ?: return null
        val alias = withContext(Dispatchers.IO) {
            val input = activity.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected image")
            input.use { store.storeImage(it, activity.contentResolver.getType(uri)) }
        }
        return MarkdownEditorImageSelection(url = alias)
    }

    suspend fun importMarkdown(): String? {
        val uri = importRequest.await {
            importLauncher.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
        } ?: return null
        return withContext(Dispatchers.IO) {
            val input = activity.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected Markdown")
            input.use(store::readMarkdown)
        }
    }

    suspend fun exportMarkdown(markdown: String) {
        val uri = exportRequest.await { exportLauncher.launch("smooth-markdown.md") }
            ?: throw CancellationException("Markdown export cancelled")
        withContext(Dispatchers.IO) {
            val output = activity.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot write selected Markdown document")
            output.use { store.writeMarkdown(it, markdown) }
        }
    }

    fun resolveImage(source: String) = store.resolveImage(source)

    private class PendingUriRequest {
        private var continuation: CancellableContinuation<Uri?>? = null

        suspend fun await(launch: () -> Unit): Uri? = suspendCancellableCoroutine { next ->
            if (continuation != null) {
                next.resumeWithException(IllegalStateException("Document picker is already open"))
                return@suspendCancellableCoroutine
            }
            continuation = next
            next.invokeOnCancellation { if (continuation === next) continuation = null }
            try { launch() } catch (error: Throwable) {
                if (continuation === next) continuation = null
                if (next.isActive) next.resumeWithException(error)
            }
        }

        fun complete(uri: Uri?) {
            val waiting = continuation ?: return
            continuation = null
            if (waiting.isActive) waiting.resume(uri)
        }
    }
}
