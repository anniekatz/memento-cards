package com.annie.memento.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.annie.memento.platform.PickedMedia
import com.annie.memento.platform.cropToSquareBytes
import com.annie.memento.platform.decodeImageBitmap
import com.annie.memento.platform.ioDispatcher
import com.annie.memento.ui.theme.CardPanelShape
import com.annie.memento.ui.theme.InsetShape
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

// let user crop image to square - for deck images
@Composable
fun SquareCropDialog(
    source: PickedMedia,
    onCancel: () -> Unit,
    onConfirm: (PickedMedia) -> Unit,
) {
    val bitmap = remember(source) { decodeImageBitmap(source.bytes) }
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offsetFrac by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = CardPanelShape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CROP PHOTO", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Drag to reposition, pinch to zoom. The photo is saved as a square.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (bitmap == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val bw = bitmap.width.toFloat()
                    val bh = bitmap.height.toFloat()
                    val side = min(bw, bh)

                    fun clamp(o: Offset, s: Float): Offset {
                        val maxX = (((bw * s) / side) - 1f).coerceAtLeast(0f) / 2f
                        val maxY = (((bh * s) / side) - 1f).coerceAtLeast(0f) / 2f
                        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(InsetShape)
                            .pointerInput(bitmap) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val frame = this.size.width.toFloat()
                                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                                    val panFrac = if (frame > 0f) Offset(pan.x / frame, pan.y / frame) else Offset.Zero
                                    scale = newScale
                                    offsetFrac = clamp(offsetFrac + panFrac, newScale)
                                }
                            },
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetFrac.x * this.size.width
                                translationY = offsetFrac.y * this.size.height
                            },
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Text("CANCEL", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MementoButton(
                            text = "Use photo",
                            enabled = !busy,
                            onClick = {
                                busy = true
                                val widthFraction = side / (scale * bw)
                                val heightFraction = side / (scale * bh)
                                val leftFraction = 0.5f - widthFraction * (offsetFrac.x + 0.5f)
                                val topFraction = 0.5f - heightFraction * (offsetFrac.y + 0.5f)
                                scope.launch {
                                    val cropped = withContext(ioDispatcher) {
                                        cropToSquareBytes(source.bytes, leftFraction, topFraction, widthFraction, heightFraction)
                                    }
                                    onConfirm(cropped?.let { PickedMedia(it, "jpg") } ?: source)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
