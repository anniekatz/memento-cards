package com.annie.memento.platform

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source
import okio.buffer
import okio.use
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
class IosMediaStorage : MediaStorage {

    private val fileManager = NSFileManager.defaultManager

    private val mediaDir: String = run {
        val documents = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String).orEmpty()
        val dir = "$documents/media"
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(
                path = dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        dir
    }

    override suspend fun save(bytes: ByteArray, extension: String): String {
        val name = "media_${NSUUID().UUIDString}.${extension.ifBlank { "bin" }}"
        bytes.toNSData().writeToFile(path(name), atomically = true)
        return name
    }

    override suspend fun saveSource(source: Source, extension: String): String {
        val name = "media_${NSUUID().UUIDString}.${extension.ifBlank { "bin" }}"
        FileSystem.SYSTEM.sink(path(name).toPath()).buffer().use { it.writeAll(source) }
        return name
    }

    override suspend fun loadBytes(storedName: String): ByteArray? =
        fileManager.contentsAtPath(path(storedName))?.toByteArray()

    override fun absolutePath(storedName: String): String = path(storedName)

    override fun delete(storedName: String) {
        fileManager.removeItemAtPath(path(storedName), error = null)
    }

    private fun path(name: String): String = "$mediaDir/$name"
}
