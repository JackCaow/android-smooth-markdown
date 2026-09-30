package com.jackcaow.smoothmarkdown

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Real Android bitmap/XML behavior, independent of the networking provider and external libraries. */
@RunWith(AndroidJUnit4::class)
class NativeImageLoaderTest {
    @Test fun svgBytesWithoutFilenameKeepNaturalViewBoxSize() {
        val bytes = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 77 20\"><rect width=\"77\" height=\"20\" fill=\"red\"/></svg>".toByteArray()
        val image = NativeImageLoader.decode(bytes, "https://example.com/badge") as NativeImageData.SvgImage
        assertEquals(77f, image.width, 0f)
        assertEquals(20f, image.height, 0f)
        assertEquals("https://example.com/badge", image.baseUrl)
    }

    @Test fun svgUnitsAndPartialDimensionsPreserveRatio() {
        assertEquals(96f to 48f, NativeImageLoader.svgSize("<svg width=\"1in\" viewBox=\"0 0 200 100\"/>"))
        assertEquals(40f to 20f, NativeImageLoader.svgSize("<svg height=\"20px\" viewBox=\"0 0 200 100\"/>"))
    }

    @Test fun rasterUsesRealUnscaledBitmapDimensions() {
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        val image = NativeImageLoader.decode(stream.toByteArray()) as NativeImageData.BitmapImage
        assertEquals(20f, image.width, 0f)
        assertEquals(10f, image.height, 0f)
        assertEquals(20, image.bitmap.width)
        assertEquals(10, image.bitmap.height)
    }

    @Test fun explicitHostFileUriLoadsWithSystemBitmapDecoder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("smooth-native-image-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(30, 15, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val image = NativeImageLoader.load(context, file.toURI().toString()) as NativeImageData.BitmapImage
            assertEquals(30f, image.width, 0f)
            assertEquals(15f, image.height, 0f)
        } finally { bitmap.recycle(); file.delete() }
    }

    @Test fun malformedAndDtdSvgAreRejected() {
        rejects { NativeImageLoader.svgSize("<svg><g></svg>") }
        rejects { NativeImageLoader.svgSize("<!DOCTYPE svg [<!ENTITY x 'hello'>]><svg>&x;</svg>") }
        rejects { NativeImageLoader.svgSize("<html/>") }
        rejects { NativeImageLoader.decode(ByteArray(NativeImageLoader.MAX_SVG_BYTES + 1).also {
            "<svg>".toByteArray().copyInto(it)
        }) }
    }

    @Test fun assetPathsDecodeUriEscapesWithoutAllowingFilesystemOrTraversal() {
        assertEquals("icons/a b.png", NativeImageLoader.assetPath("icons/a%20b.png?version=1#preview"))
        assertEquals("icons/a b.png", NativeImageLoader.assetPath("file:///android_asset/icons/a%20b.png"))
        rejects { NativeImageLoader.assetPath("file:///etc/passwd") }
        rejects { NativeImageLoader.assetPath("icons/%2e%2e/secret.png") }
        rejects { NativeImageLoader.assetPath("//example.com/icon.png") }
    }

    @Test fun boundedReadsAcceptExactLimitAndRejectOneExtraByte() {
        assertArrayEquals(byteArrayOf(1, 2, 3), NativeImageLoader.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
        rejects { NativeImageLoader.readBounded(ByteArrayInputStream(ByteArray(4)), 3) }
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Invalid image was accepted") } catch (_: IllegalArgumentException) {
        } catch (_: org.xmlpull.v1.XmlPullParserException) { }
    }
}
