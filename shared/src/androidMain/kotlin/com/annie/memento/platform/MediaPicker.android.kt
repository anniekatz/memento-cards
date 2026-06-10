package com.annie.memento.platform

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberImagePicker(onResult: (List<PickedMedia>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) {
            onResult(emptyList())
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val media = withContext(Dispatchers.IO) { uris.mapNotNull { context.readPicked(it, "jpg") } }
            onResult(media)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberSingleImagePicker(onResult: (PickedMedia?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val media = withContext(Dispatchers.IO) { context.readPicked(uri, "jpg") }
            onResult(media)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
actual fun rememberAudioPicker(onResult: (List<PickedMedia>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) {
            onResult(emptyList())
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val media = withContext(Dispatchers.IO) { uris.mapNotNull { context.readPicked(it, "m4a") } }
            onResult(media)
        }
    }
    return { launcher.launch("audio/*") }
}

// reads a uri, best guess file extension
private fun Context.readPicked(uri: Uri, defaultExtension: String): PickedMedia? {
    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return PickedMedia(bytes, contentResolver.getType(uri).toExtension(defaultExtension))
}

private fun String?.toExtension(default: String): String =
    this?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.takeIf { it.isNotBlank() } ?: default
