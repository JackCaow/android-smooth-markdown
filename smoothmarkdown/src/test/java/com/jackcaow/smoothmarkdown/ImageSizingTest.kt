package com.jackcaow.smoothmarkdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {
    @Test fun usesIntrinsicDimensionsUntilContainerRequiresScaling() {
        val intrinsic = ImageSize(240f, 120f)
        assertEquals(ImageSize(240f, 120f), imageSize(null, null, intrinsic, 300f))
        assertEquals(ImageSize(150f, 75f), imageSize(null, null, intrinsic, 150f))
    }

    @Test fun omittedHtmlDimensionPreservesAspectRatio() {
        val intrinsic = ImageSize(240f, 120f)
        assertEquals(ImageSize(80f, 40f), imageSize(80f, null, intrinsic, 300f))
        assertEquals(ImageSize(60f, 30f), imageSize(null, 30f, intrinsic, 300f))
        assertEquals(ImageSize(100f, 50f), imageSize(200f, null, intrinsic, 100f))
    }

    @Test fun explicitDimensionsAndMissingImageRemainBounded() {
        assertEquals(ImageSize(80f, 40f), imageSize(80f, 40f, null, 300f))
        assertEquals(ImageSize(100f, 50f), imageSize(200f, 100f, null, 100f))
        assertEquals(ImageSize(32f, 32f), imageSize(null, null, null, 300f))
    }
}
