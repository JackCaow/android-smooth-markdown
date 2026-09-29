package com.jackcaow.smoothmarkdown

/** Logical image size within the width offered by the Markdown container. */
internal data class ImageSize(val width: Float, val height: Float)

/**
 * HTML dimensions override the corresponding intrinsic dimension. An omitted dimension keeps the
 * decoded image's aspect ratio, while an oversized image scales down to the available line width.
 */
internal fun imageSize(
    width: Float?,
    height: Float?,
    intrinsic: ImageSize?,
    maxWidth: Float,
    fallback: Float = 32f,
): ImageSize {
    val natural = intrinsic?.takeIf { it.width > 0f && it.height > 0f }
    val resolvedWidth = width ?: when {
        height != null && natural != null -> height * natural.width / natural.height
        natural != null -> natural.width
        else -> fallback
    }
    val resolvedHeight = height ?: when {
        width != null && natural != null -> width * natural.height / natural.width
        natural != null -> natural.height
        else -> fallback
    }
    val scale = if (maxWidth.isFinite() && maxWidth > 0f && resolvedWidth > maxWidth) maxWidth / resolvedWidth else 1f
    return ImageSize(resolvedWidth * scale, resolvedHeight * scale)
}
