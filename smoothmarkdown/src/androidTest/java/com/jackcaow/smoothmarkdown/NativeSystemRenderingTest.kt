package com.jackcaow.smoothmarkdown

import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Pixel checks include actual Android WebViews through UiAutomation's window screenshot. */
class NativeSystemRenderingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var hostView: View

    @Test fun smallAndScaledSvgKeepTheirFullViewportAndMathRenders() {
        render(dark = false)
        val screenshot = settledScreenshot("native-system-light.png", false)
        assertSvgViewport(screenshot)
        assertMathInk(screenshot, "native-math-fraction", false)
        assertMathInk(screenshot, "native-math-matrix", false)
        assertMathInk(screenshot, "native-math-cases", false)
    }

    @Test fun transparentSvgAndMathStayReadableInDarkTheme() {
        render(dark = true)
        val screenshot = settledScreenshot("native-system-dark.png", true)
        assertSvgViewport(screenshot)
        assertMathInk(screenshot, "native-math-fraction", true)
        assertMathInk(screenshot, "native-math-matrix", true)
        assertMathInk(screenshot, "native-math-cases", true)
    }

    private fun render(dark: Boolean) {
        val small = NativeImageLoader.decode("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"77\" height=\"20\"><rect width=\"77\" height=\"20\" fill=\"red\"/></svg>".toByteArray()) as NativeImageData.SvgImage
        val scaled = NativeImageLoader.decode("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"600\" height=\"200\"><rect width=\"300\" height=\"200\" fill=\"red\"/><rect x=\"300\" width=\"300\" height=\"200\" fill=\"blue\"/></svg>".toByteArray()) as NativeImageData.SvgImage
        compose.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Column(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White).padding(16.dp)) {
                    Text("Native Android SVG + MathML", color = if (dark) Color.White else Color.Black)
                    Text("77 × 20 natural badge", color = if (dark) Color.White else Color.Black)
                    NativeSVGView(small, Modifier.size(77.dp, 20.dp).testTag("native-svg-small"))
                    Text("600 × 200 SVG fitted to 300 × 100", color = if (dark) Color.White else Color.Black)
                    NativeSVGView(scaled, Modifier.size(300.dp, 100.dp).testTag("native-svg-scaled"))
                    Text("Fraction, matrix and cases", color = if (dark) Color.White else Color.Black)
                    SystemMath("\\frac{a+b}{c+d}", true, Modifier.testTag("native-math-fraction"))
                    SystemMath("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}", true, Modifier.testTag("native-math-matrix"))
                    SystemMath("f(x)=\\begin{cases}x^2&x>0\\\\0&x\\leq0\\end{cases}", true, Modifier.testTag("native-math-cases"))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun settledScreenshot(name: String, dark: Boolean): Bitmap {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var screenshot: Bitmap? = null
        compose.waitUntil(20_000) {
            screenshot?.recycle()
            screenshot = automation.takeScreenshot()
            screenshot?.let { image ->
                isRed(sample(image, "native-svg-small", .25f, .5f)) &&
                    isBlue(sample(image, "native-svg-scaled", .75f, .5f)) &&
                    listOf("native-math-fraction", "native-math-matrix", "native-math-cases").all { mathInkCount(image, it, dark) > 10 }
            } == true
        }
        val image = requireNotNull(screenshot)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "native-review").apply { mkdirs() }
        val output = File(directory, name)
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("UI_REVIEW_SCREENSHOT=${output.absolutePath}")
        return image
    }

    private fun sample(image: Bitmap, tag: String, x: Float, y: Float): Int {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val origin = positionOnScreen(node)
        val bounds = node.boundsInRoot
        return image.getPixel((origin.x + bounds.width * x).toInt().coerceIn(0, image.width-1),
            (origin.y + bounds.height * y).toInt().coerceIn(0, image.height-1))
    }

    private fun assertSvgViewport(image: Bitmap) {
        for (x in listOf(.1f, .9f)) for (y in listOf(.1f, .9f))
            assertTrue("77×20 badge corner must be red, not clipped or autoshrunk", isRed(sample(image, "native-svg-small", x, y)))
        for (y in listOf(.1f, .9f)) {
            assertTrue("scaled SVG left must be red", isRed(sample(image, "native-svg-scaled", .1f, y)))
            assertTrue("scaled SVG right must be blue, not cropped", isBlue(sample(image, "native-svg-scaled", .9f, y)))
        }
    }

    private fun assertMathInk(image: Bitmap, tag: String, dark: Boolean) {
        assertTrue("$tag must contain visible ${if (dark) "light" else "dark"} math pixels", mathInkCount(image, tag, dark) > 10)
    }

    private fun mathInkCount(image: Bitmap, tag: String, dark: Boolean): Int {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val origin = positionOnScreen(node)
        val bounds = node.boundsInRoot
        var count = 0
        for (y in origin.y.toInt().coerceAtLeast(0) until (origin.y + bounds.height).toInt().coerceAtMost(image.height)) {
            for (x in origin.x.toInt().coerceAtLeast(0) until (origin.x + bounds.width).toInt().coerceAtMost(image.width)) {
                val pixel = image.getPixel(x, y)
                val channel = (AndroidColor.red(pixel) + AndroidColor.green(pixel) + AndroidColor.blue(pixel)) / 3
                if (if (dark) channel > 150 else channel < 100) count++
            }
        }
        return count
    }

    private fun positionOnScreen(node: SemanticsNode): Offset = compose.runOnIdle {
        val screen = IntArray(2)
        val window = IntArray(2)
        hostView.getLocationOnScreen(screen)
        hostView.getLocationInWindow(window)
        node.positionInWindow + Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
    }

    private fun isRed(pixel: Int) = AndroidColor.red(pixel) > 200 && AndroidColor.green(pixel) < 50 && AndroidColor.blue(pixel) < 50
    private fun isBlue(pixel: Int) = AndroidColor.blue(pixel) > 200 && AndroidColor.red(pixel) < 50 && AndroidColor.green(pixel) < 50
}
