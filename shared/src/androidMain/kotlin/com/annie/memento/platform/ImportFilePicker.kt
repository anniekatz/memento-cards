package com.annie.memento.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            onResult(withContext(Dispatchers.IO) { context.copyPickedFile(uri) })
        }
    }
    // .apkg no registered mime type
    return { launcher.launch(arrayOf("*/*")) }
}

private fun Context.copyPickedFile(uri: Uri): PickedFile? = runCatching {
    // sweep
    cacheDir.listFiles()?.forEach { stale ->
        if (stale.isDirectory && stale.name.startsWith("anki_import_")) {
            runCatching { stale.deleteRecursively() }
        }
    }
    val dir = File(cacheDir, "anki_import_${UUID.randomUUID()}").apply { mkdirs() }
    val target = File(dir, "deck.apkg")
    val copied = contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false
    if (!copied) return@runCatching null
    PickedFile(
        tempPath = target.absolutePath,
        displayName = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "deck.apkg",
        sizeBytes = target.length(),
    )
}.getOrNull()

private fun Context.queryDisplayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
}.getOrNull()
