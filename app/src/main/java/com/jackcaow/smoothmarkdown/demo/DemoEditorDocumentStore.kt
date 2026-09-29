package com.jackcaow.smoothmarkdown.demo

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Private image copies and exact UTF-8 Markdown I/O for the Android document-picker demo. */
internal class DemoEditorDocumentStore(private val filesDirectory: File) {
    companion object {
        const val MAX_MARKDOWN_BYTES = 2 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        private val imageAlias = Regex("^demo-images/[0-9a-f-]{36}\\.(png|jpg|gif|webp)$")
    }

    fun readMarkdown(input: InputStream): String {
        val bytes = readBounded(input, MAX_MARKDOWN_BYTES)
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    fun writeMarkdown(output: OutputStream, markdown: String) {
        output.write(markdown.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    /** Returns a safe relative Markdown source. The app image builder resolves this alias. */
    fun storeImage(input: InputStream, mimeType: String?): String {
        val extension = when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> throw IOException("Unsupported image format")
        }
        val bytes = readBounded(input, MAX_IMAGE_BYTES)
        if (!hasImageSignature(bytes, extension)) throw IOException("Invalid image data")
        val directory = File(filesDirectory, "demo-images")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create image directory")
        val name = "${UUID.randomUUID()}.$extension"
        val temporary = File(directory, ".$name.tmp")
        val target = File(directory, name)
        try {
            temporary.outputStream().use { it.write(bytes) }
            if (!temporary.renameTo(target)) throw IOException("Cannot save selected image")
        } finally {
            temporary.delete()
        }
        return "demo-images/$name"
    }

    fun resolveImage(source: String): File? {
        if (!imageAlias.matches(source)) return null
        return File(filesDirectory, source).takeIf { it.isFile }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > maximum) throw IOException("Document exceeds ${maximum / (1024 * 1024)} MiB limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun hasImageSignature(bytes: ByteArray, extension: String): Boolean = when (extension) {
        "png" -> bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        "jpg" -> bytes.size >= 3 && bytes[0] == (-1).toByte() && bytes[1] == (-40).toByte() &&
            bytes[2] == (-1).toByte()
        "gif" -> bytes.size >= 6 && String(bytes, 0, 6, StandardCharsets.US_ASCII) in setOf("GIF87a", "GIF89a")
        "webp" -> bytes.size >= 12 && String(bytes, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
        else -> false
    }
}
