package com.annie.memento.platform

import androidx.compose.runtime.Composable

data class PickedFile(val tempPath: String, val displayName: String, val sizeBytes: Long)

// filepicker for import
@Composable
expect fun rememberFilePicker(onResult: (PickedFile?) -> Unit): () -> Unit
