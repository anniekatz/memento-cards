package com.annie.memento.data.ankiImporter

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

class ImportSession(val apkgPath: String, val displayName: String) {
    val directory: Path = apkgPath.toPath().parent ?: apkgPath.toPath()

    fun scratchPath(name: String): Path = directory / name

    fun delete() {
        runCatching { FileSystem.SYSTEM.deleteRecursively(directory) }
    }
}
