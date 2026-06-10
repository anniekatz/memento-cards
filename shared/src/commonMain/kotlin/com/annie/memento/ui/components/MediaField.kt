package com.annie.memento.ui.components

import com.annie.memento.model.MediaInput
import com.annie.memento.platform.PickedMedia

sealed interface MediaField {
    data class Existing(val path: String) : MediaField
    data class Picked(val media: PickedMedia) : MediaField

    fun toInput(): MediaInput = when (this) {
        is Existing -> MediaInput.Keep(path)
        is Picked -> MediaInput.New(media.bytes, media.fileExtension)
    }
}

fun List<MediaField>.toInputs(): List<MediaInput> = map { it.toInput() }
