package com.annie.memento.platform

import android.content.Context
import okio.Source
import okio.buffer
import okio.sink
import java.io.File
import java.util.UUID

class AndroidMediaStorage(context: Context) : MediaStorage {

    private val mediaDir: File = File(context.filesDir, "media").apply { mkdirs() }

    override suspend fun save(bytes: ByteArray, extension: String): String {
        val name = "media_${UUID.randomUUID()}.${extension.ifBlank { "bin" }}"
        File(mediaDir, name).writeBytes(bytes)
        return name
    }

    override suspend fun saveSource(source: Source, extension: String): String {
        val name = "media_${UUID.randomUUID()}.${extension.ifBlank { "bin" }}"
        File(mediaDir, name).sink().buffer().use { it.writeAll(source) }
        return name
    }

    override suspend fun loadBytes(storedName: String): ByteArray? {
        val file = File(mediaDir, storedName)
        return if (file.exists()) file.readBytes() else null
    }

    override fun absolutePath(storedName: String): String = File(mediaDir, storedName).absolutePath

    override fun delete(storedName: String) {
        runCatching { File(mediaDir, storedName).delete() }
    }
}
