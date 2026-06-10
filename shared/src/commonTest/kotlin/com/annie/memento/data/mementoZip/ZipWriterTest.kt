package com.annie.memento.data.mementoZip

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.openZip
import okio.use
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZipWriterTest {

    @Test
    fun crcMatchesKnownVector() {
        assertEquals(0xCBF43926.toInt(), Crc32.of("123456789".encodeToByteArray()))
    }

    @Test
    fun okioCanReadBackWrittenZip() {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "memento_zip_writer_test"
        FileSystem.SYSTEM.createDirectories(dir)
        val path = dir / "test.zip"
        val manifest = """{"format":"memento-deck","version":1}"""
        val blob = ByteArray(70_000) { (it % 251).toByte() }
        try {
            FileSystem.SYSTEM.sink(path).buffer().use { sink ->
                val writer = ZipWriter(sink)
                writer.add("memento.json", manifest.encodeToByteArray())
                writer.add("media/blob.bin", blob)
                writer.add("media/日本語.mp3", byteArrayOf(1, 2, 3))
                writer.add("media/empty.bin", ByteArray(0))
                writer.finish()
            }
            val zip = FileSystem.SYSTEM.openZip(path)
            assertEquals(manifest, zip.read("/memento.json".toPath()) { readUtf8() })
            assertContentEquals(blob, zip.read("/media/blob.bin".toPath()) { readByteArray() })
            assertContentEquals(byteArrayOf(1, 2, 3), zip.read("/media/日本語.mp3".toPath()) { readByteArray() })
            assertContentEquals(ByteArray(0), zip.read("/media/empty.bin".toPath()) { readByteArray() })
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }
}
