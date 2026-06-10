package com.annie.memento.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    runCatching { decodeSampled(bytes, maxDimension)?.asImageBitmap() }.getOrNull()

actual fun cropToSquareBytes(
    bytes: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int,
): ByteArray? = runCatching {
    val working = decodeSampled(bytes, (outputSize * 3).coerceAtLeast(1024)) ?: return@runCatching null
    val w = working.width
    val h = working.height
    val left = (leftFraction * w).roundToInt().coerceIn(0, w - 1)
    val top = (topFraction * h).roundToInt().coerceIn(0, h - 1)
    val cropW = (widthFraction * w).roundToInt().coerceIn(1, w - left)
    val cropH = (heightFraction * h).roundToInt().coerceIn(1, h - top)
    val side = min(cropW, cropH) // keep it exactly square
    val cropped = Bitmap.createBitmap(working, left, top, side, side)
    val scaled = if (side == outputSize) cropped else Bitmap.createScaledBitmap(cropped, outputSize, outputSize, true)
    ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}.getOrNull()

//keep both side <= max dimension
private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    val cap = maxDimension.coerceAtLeast(1)
    var sample = 1
    while (w / sample > cap || h / sample > cap) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
