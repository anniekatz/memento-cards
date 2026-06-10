package com.annie.memento.platform

import androidx.compose.ui.graphics.ImageBitmap

// jpeg/png/etc to image bitmap within max dimensions
expect fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int = 2048): ImageBitmap?

//crops to square (used for deck image adding/editing)
expect fun cropToSquareBytes(
    bytes: ByteArray,
    leftFraction: Float,
    topFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    outputSize: Int = 1024,
): ByteArray?
