package com.annie.memento.platform

// stores media as files in local storage, paths stored in sqldelight
interface MediaStorage {
    suspend fun save(bytes: ByteArray, extension: String): String

    suspend fun saveSource(source: okio.Source, extension: String): String

    suspend fun loadBytes(storedName: String): ByteArray?

    fun absolutePath(storedName: String): String

    fun delete(storedName: String)
}
