package com.annie.memento.platform

import androidx.compose.runtime.Composable

data class PickedMedia(val bytes: ByteArray, val fileExtension: String) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedMedia && fileExtension == other.fileExtension && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + fileExtension.hashCode()
}

// system image picker: multiselect
@Composable
expect fun rememberImagePicker(onResult: (List<PickedMedia>) -> Unit): () -> Unit

// system image picker: single select (for deck photo)
@Composable
expect fun rememberSingleImagePicker(onResult: (PickedMedia?) -> Unit): () -> Unit

// system audio picker
@Composable
expect fun rememberAudioPicker(onResult: (List<PickedMedia>) -> Unit): () -> Unit
