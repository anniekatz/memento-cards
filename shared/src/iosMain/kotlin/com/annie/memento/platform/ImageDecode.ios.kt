package com.annie.memento.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.min

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? = runCatching {
    downscaleIfNeeded(Image.makeFromEncoded(bytes), maxDimension).toComposeImageBitmap()
}.getOrNull()

actual fun cropToSquareBytes(
    bytes: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int,
): ByteArray? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    val w = image.width
    val h = image.height
    val left = (leftFraction * w).toInt().coerceIn(0, w - 1)
    val top = (topFraction * h).toInt().coerceIn(0, h - 1)
    val cropW = (widthFraction * w).toInt().coerceIn(1, w - left)
    val cropH = (heightFraction * h).toInt().coerceIn(1, h - top)
    val side = min(cropW, cropH).toFloat() // keep it exactly square
    val surface = Surface.makeRasterN32Premul(outputSize, outputSize)
    surface.canvas.drawImageRect(
        image,
        Rect.makeXYWH(left.toFloat(), top.toFloat(), side, side),
        Rect.makeWH(outputSize.toFloat(), outputSize.toFloat()),
    )
    surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)?.bytes
}.getOrNull()

private fun downscaleIfNeeded(image: Image, maxDimension: Int): Image {
    val longest = maxOf(image.width, image.height)
    if (maxDimension <= 0 || longest <= maxDimension) return image
    val scale = maxDimension.toFloat() / longest
    val dstW = (image.width * scale).toInt().coerceAtLeast(1)
    val dstH = (image.height * scale).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(dstW, dstH)
    surface.canvas.drawImageRect(image, Rect.makeWH(dstW.toFloat(), dstH.toFloat()))
    return surface.makeImageSnapshot()
}
