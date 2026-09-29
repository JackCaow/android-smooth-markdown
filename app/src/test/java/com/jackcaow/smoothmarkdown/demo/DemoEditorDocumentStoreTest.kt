package com.jackcaow.smoothmarkdown.demo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class DemoEditorDocumentStoreTest {
    @Test fun exactMarkdownRoundTripKeepsCrLfTrailingSpacesAndUnicode() {
        val root = Files.createTempDirectory("markdown-store").toFile()
        try {
            val store = DemoEditorDocumentStore(root)
            val source = "# 标题\r\n\r\nBody  \r\n"
            val output = ByteArrayOutputStream()
            store.writeMarkdown(output, source)
            assertArrayEquals(source.toByteArray(StandardCharsets.UTF_8), output.toByteArray())
            assertEquals(source, store.readMarkdown(ByteArrayInputStream(output.toByteArray())))
        } finally { root.deleteRecursively() }
    }

    @Test fun invalidUtf8AndOversizedMarkdownFailWithoutFallbackText() {
        val root = Files.createTempDirectory("markdown-store").toFile()
        val store = DemoEditorDocumentStore(root)
        try {
            assertFails { store.readMarkdown(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
            assertFails { store.readMarkdown(ByteArrayInputStream(ByteArray(DemoEditorDocumentStore.MAX_MARKDOWN_BYTES + 1))) }
        } finally { root.deleteRecursively() }
    }

    @Test fun imageCopyUsesSafeAliasAndRejectsSpoofedOrOversizedContent() {
        val root = Files.createTempDirectory("markdown-store").toFile()
        try {
            val store = DemoEditorDocumentStore(root)
            val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3)
            val alias = store.storeImage(ByteArrayInputStream(png), "image/png")
            assertTrue(alias.startsWith("demo-images/"))
            assertTrue(alias.endsWith(".png"))
            assertArrayEquals(png, store.resolveImage(alias)?.readBytes())
            assertArrayEquals(png, DemoEditorDocumentStore(root).resolveImage(alias)?.readBytes())
            assertNull(store.resolveImage("../$alias"))
            assertNull(store.resolveImage("demo-images/../../secret.png"))
            assertFails { store.storeImage(ByteArrayInputStream("text".toByteArray()), "image/png") }
            assertFails { store.storeImage(ByteArrayInputStream(png), "image/svg+xml") }
            assertFails { store.storeImage(ByteArrayInputStream(ByteArray(DemoEditorDocumentStore.MAX_IMAGE_BYTES + 1)), "image/png") }
            assertEquals(1, File(root, "demo-images").listFiles()?.size)
        } finally { root.deleteRecursively() }
    }

    private inline fun assertFails(action: () -> Unit) {
        var failed = false
        try { action() } catch (_: Exception) { failed = true }
        assertTrue("Expected an I/O or decoding failure", failed)
    }
}
