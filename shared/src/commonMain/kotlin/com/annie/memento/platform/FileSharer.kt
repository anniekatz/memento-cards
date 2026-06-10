package com.annie.memento.platform

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFileSharer(): (filePath: String, mimeType: String) -> Unit
