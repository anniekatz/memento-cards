@file:OptIn(ExperimentalLayoutApi::class)

package com.annie.memento.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.annie.memento.ui.theme.InsetShape
import com.annie.memento.platform.PickedMedia
import com.annie.memento.platform.rememberAudioPicker
import com.annie.memento.platform.rememberImagePicker
import com.annie.memento.platform.rememberSingleImagePicker

// deck photo preview size
private val SquarePreviewSize = 160.dp

// media thumbnail size when adding/editing card
private val ThumbnailSize = 64.dp

@Composable
fun ImageListEditor(
    images: List<MediaField>,
    onChange: (List<MediaField>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Photos",
) {
    val pick = rememberImagePicker { picked ->
        if (picked.isNotEmpty()) onChange(images + picked.map { MediaField.Picked(it) })
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MementoOutlineButton("Add photo", onClick = pick, leading = "+", modifier = Modifier.fillMaxWidth())
        if (images.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                images.forEachIndexed { index, field ->
                    RemovableThumbnail(onRemove = { onChange(images.filterIndexed { i, _ -> i != index }) }) {
                        when (field) {
                            is MediaField.Existing -> StoredImage(field.path, Modifier.fillMaxSize(), ContentScale.Crop)
                            is MediaField.Picked -> BytesImage(field.media.bytes, Modifier.fillMaxSize(), ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemovableThumbnail(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(ThumbnailSize)) {
        Box(Modifier.fillMaxSize().clip(InsetShape)) { content() }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = Color.White, fontSize = 11.sp)
        }
    }
}

// square photo for deck image
@Composable
fun SquarePhotoField(
    photo: MediaField?,
    onChange: (MediaField?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Photo",
) {
    var pendingCrop by remember { mutableStateOf<PickedMedia?>(null) }
    val pick = rememberSingleImagePicker { picked -> if (picked != null) pendingCrop = picked }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (photo != null) {
            Box(Modifier.size(SquarePreviewSize).clip(InsetShape)) {
                when (photo) {
                    is MediaField.Existing -> StoredImage(photo.path, Modifier.fillMaxSize(), ContentScale.Crop)
                    is MediaField.Picked -> BytesImage(photo.media.bytes, Modifier.fillMaxSize(), ContentScale.Crop)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MementoOutlineButton("Replace", onClick = pick)
                TextButton(onClick = { onChange(null) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            MementoOutlineButton("Add photo", onClick = pick, leading = "+")
        }
    }

    pendingCrop?.let { src ->
        SquareCropDialog(
            source = src,
            onCancel = { pendingCrop = null },
            onConfirm = { cropped ->
                onChange(MediaField.Picked(cropped))
                pendingCrop = null
            },
        )
    }
}

@Composable
fun AudioListEditor(
    audios: List<MediaField>,
    onChange: (List<MediaField>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Audio",
) {
    val pick = rememberAudioPicker { picked -> if (picked.isNotEmpty()) onChange(audios + picked.map { MediaField.Picked(it) }) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MementoOutlineButton("Add audio", onClick = pick, leading = "+", modifier = Modifier.fillMaxWidth())
        audios.forEachIndexed { index, field ->
            Surface(
                shape = InsetShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (field) {
                        is MediaField.Existing -> AudioPlayButton(field.path)
                        is MediaField.Picked -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            GlyphIcon("♪", fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = { onChange(audios.filterIndexed { i, _ -> i != index }) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
