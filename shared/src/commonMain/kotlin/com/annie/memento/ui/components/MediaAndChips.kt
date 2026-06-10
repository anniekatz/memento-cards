package com.annie.memento.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annie.memento.di.LocalAppGraph
import com.annie.memento.platform.decodeImageBitmap
import com.annie.memento.platform.ioDispatcher
import com.annie.memento.ui.theme.ChipShape
import com.annie.memento.ui.theme.NeutralChipColor
import com.annie.memento.ui.theme.SwatchColors
import com.annie.memento.ui.theme.toArgbLong
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// tags/levels, can be selectable
@Composable
fun ColoredChip(
    label: String,
    color: Color?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val base = color ?: NeutralChipColor
    val container = if (selected) base.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val borderColor = if (selected) base else base.copy(alpha = 0.40f)
    Surface(
        modifier = if (onClick != null) modifier.clip(ChipShape).clickable(onClick = onClick) else modifier,
        shape = ChipShape,
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(width = 4.dp, height = 12.dp).background(base))
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            if (selected) Text("✓", color = base, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

//selectable colors for tags/levels
@Composable
fun ColorPickerRow(
    selected: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
            ColorDot(color = NeutralChipColor, selected = selected == null, isNone = true) { onSelect(null) }
        }
        items(SwatchColors) { swatch ->
            val argb = swatch.toArgbLong()
            ColorDot(color = swatch, selected = selected == argb) { onSelect(argb) }
        }
    }
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    isNone: Boolean = false,
    onClick: () -> Unit,
) {
    val swatchShape = RoundedCornerShape(2.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(swatchShape)
            .background(if (isNone) color.copy(alpha = 0.16f) else color)
            .border(
                BorderStroke(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                ),
                shape = swatchShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isNone -> Text("∅", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            selected -> Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

//loads and displays saved image
@Composable
fun StoredImage(
    storedName: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val storage = LocalAppGraph.current.mediaStorage
    val bitmap by produceState<ImageBitmap?>(initialValue = null, storedName) {
        value = withContext(ioDispatcher) { storage.loadBytes(storedName)?.let { decodeImageBitmap(it) } }
    }
    ImageBox(bitmap, modifier, contentScale)
}

//loads and displays newly picked image
@Composable
fun BytesImage(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap = remember(bytes) { decodeImageBitmap(bytes) }
    ImageBox(bitmap, modifier, contentScale)
}

@Composable
private fun ImageBox(bitmap: ImageBitmap?, modifier: Modifier, contentScale: ContentScale) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun StoredImageAutoHeight(
    storedName: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val storage = LocalAppGraph.current.mediaStorage
    val bitmap by produceState<ImageBitmap?>(initialValue = null, storedName) {
        value = withContext(ioDispatcher) { storage.loadBytes(storedName)?.let { decodeImageBitmap(it) } }
    }
    AspectRatioImage(bitmap, modifier, shape)
}

@Composable
fun BytesImageAutoHeight(
    bytes: ByteArray,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val bitmap = remember(bytes) { decodeImageBitmap(bytes) }
    AspectRatioImage(bitmap, modifier, shape)
}

@Composable
private fun AspectRatioImage(bitmap: ImageBitmap?, modifier: Modifier, shape: Shape) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .clip(shape),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier.aspectRatio(1f).clip(shape), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun AudioPlayButton(storedName: String, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val path = remember(storedName) { graph.mediaStorage.absolutePath(storedName) }
    val playingPath by graph.audioPlayer.playing.collectAsState()
    val isPlaying = playingPath == path
    FilledTonalIconButton(
        onClick = {
            scope.launch(ioDispatcher) {
                if (isPlaying) graph.audioPlayer.stop() else graph.audioPlayer.play(path)
            }
        },
        modifier = modifier,
    ) {
        if (isPlaying) PauseGlyph() else GlyphIcon("▶", fontSize = 16.sp)
    }
}

// the damn pause button keeps shiowign up as an emoji omg
@Composable
private fun PauseGlyph(color: Color = LocalContentColor.current) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(width = 3.5.dp, height = 12.dp).background(color))
        Box(Modifier.size(width = 3.5.dp, height = 12.dp).background(color))
    }
}
