package com.annie.memento.data.ankiImporter

import com.squareup.zstd.okio.zstdDecompress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.Source
import okio.buffer
import okio.openZip
import okio.use

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())

// anki compression differs per version
class ApkgPackage private constructor(
    private val zip: FileSystem,
    private val session: ImportSession,
    val version: Int,
) {
    val isModern: Boolean get() = version >= 3

    private val collectionEntry: Path = listOf(
        "/collection.anki21b".toPath(),
        "/collection.anki21".toPath(),
        "/collection.anki2".toPath(),
    ).firstOrNull { zip.exists(it) } ?: throw ApkgException.NotAnApkg("no collection database in archive")

    fun extractCollectionDb(): Path {
        val out = session.scratchPath("collection.db")
        try {
            FileSystem.SYSTEM.sink(out).buffer().use { sink ->
                val raw = zip.source(collectionEntry)
                val source = if (collectionEntry.name.endsWith("anki21b")) raw.zstdDecompress() else raw
                source.buffer().use { sink.writeAll(it) }
            }
        } catch (e: IOException) {
            throw ApkgException.CorruptArchive("couldn't extract collection database", e)
        }
        return out
    }

    fun readMediaMap(): Map<String, String> {
        val path = "/media".toPath()
        if (!zip.exists(path)) return emptyMap()
        val raw = try {
            zip.read(path) { readByteArray() }
        } catch (e: IOException) {
            throw ApkgException.CorruptArchive("couldn't read media map", e)
        }
        if (raw.isEmpty()) return emptyMap()
        return try {
            when {
                raw.startsWith(ZSTD_MAGIC) -> {
                    val decompressed = Buffer().also { buffer ->
                        (Buffer().write(raw) as Source).zstdDecompress().buffer().use { buffer.writeAll(it) }
                    }
                    parseMediaEntryNames(decompressed)
                        .withIndex()
                        .filter { it.value.isNotEmpty() }
                        .associate { (index, name) -> name to index.toString() }
                }
                raw[0] == '{'.code.toByte() -> {
                    Json.parseToJsonElement(raw.decodeToString()).jsonObject
                        .entries.associate { (entry, name) -> name.jsonPrimitive.content to entry }
                }
                raw[0].toInt() == 0x0A -> {
                    parseMediaEntryNames(Buffer().write(raw))
                        .withIndex()
                        .filter { it.value.isNotEmpty() }
                        .associate { (index, name) -> name to index.toString() }
                }
                else -> throw ApkgException.CorruptArchive("unrecognized media map format")
            }
        } catch (e: Exception) {
            if (e is ApkgException) throw e
            throw ApkgException.CorruptArchive("couldn't parse media map", e)
        }
    }
    
    fun openMedia(zipEntryName: String): Source? {
        val path = "/$zipEntryName".toPath()
        if (!zip.exists(path)) return null
        val raw = zip.source(path)
        return if (isModern) raw.zstdDecompress() else raw
    }

    companion object {
        fun open(session: ImportSession): ApkgPackage {
            val apkg = session.apkgPath.toPath()
            val magic = try {
                FileSystem.SYSTEM.read(apkg) { readByteArray(4) }
            } catch (e: IOException) {
                throw ApkgException.IoFailure(e)
            }
            if (!magic.startsWith(ZIP_MAGIC)) throw ApkgException.NotAnApkg("not a zip archive")

            val zip = try {
                FileSystem.SYSTEM.openZip(apkg)
            } catch (e: IOException) {
                throw ApkgException.CorruptArchive("couldn't open archive", e)
            }

            val metaPath = "/meta".toPath()
            val version = if (zip.exists(metaPath)) {
                try {
                    zip.read(metaPath) { parseMetaVersion(this) } ?: 1
                } catch (e: Exception) {
                    if (e is ApkgException) throw e
                    throw ApkgException.CorruptArchive("couldn't read package meta", e)
                }
            } else {
                if (zip.exists("/collection.anki21".toPath())) 2 else 1
            }
            if (version > 3) throw ApkgException.UnsupportedVersion(version)
            return ApkgPackage(zip, session, version)
        }
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}
