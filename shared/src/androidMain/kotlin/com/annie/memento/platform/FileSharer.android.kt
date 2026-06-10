package com.annie.memento.platform

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
actual fun rememberFileSharer(): (String, String) -> Unit {
    val context = LocalContext.current
    return { filePath, mimeType ->
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(filePath))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null))
        }
    }
}
