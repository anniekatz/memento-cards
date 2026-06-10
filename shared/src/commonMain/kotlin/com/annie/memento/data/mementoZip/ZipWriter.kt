package com.annie.memento.data.mementoZip

import okio.BufferedSink

internal class ZipWriter(private val sink: BufferedSink) {

    private class Entry(val nameBytes: ByteArray, val crc: Int, val size: Int, val offset: Long)

    private val entries = ArrayList<Entry>()
    private var offset = 0L
    private var finished = false

    fun add(name: String, data: ByteArray) {
        check(!finished) { "zip already finished" }
        require(entries.size < 0xFFFF) { "too many zip entries" }
        val nameBytes = name.encodeToByteArray()
        require(nameBytes.size <= 0xFFFF) { "zip entry name too long" }
        val crc = Crc32.of(data)
        entries.add(Entry(nameBytes, crc, data.size, offset))


        sink.writeIntLe(0x04034b50)
        sink.writeShortLe(20)
        sink.writeShortLe(FLAG_UTF8_NAMES)
        sink.writeShortLe(0)
        sink.writeShortLe(DOS_TIME)
        sink.writeShortLe(DOS_DATE)
        sink.writeIntLe(crc)
        sink.writeIntLe(data.size)
        sink.writeIntLe(data.size)
        sink.writeShortLe(nameBytes.size)
        sink.writeShortLe(0)
        sink.write(nameBytes)
        sink.write(data)
        offset += 30L + nameBytes.size + data.size
    }

    fun finish() {
        check(!finished) { "zip already finished" }
        finished = true
        val centralDirStart = offset
        entries.forEach { entry ->
            sink.writeIntLe(0x02014b50)
            sink.writeShortLe(20)
            sink.writeShortLe(20)
            sink.writeShortLe(FLAG_UTF8_NAMES)
            sink.writeShortLe(0)
            sink.writeShortLe(DOS_TIME)
            sink.writeShortLe(DOS_DATE)
            sink.writeIntLe(entry.crc)
            sink.writeIntLe(entry.size)
            sink.writeIntLe(entry.size)
            sink.writeShortLe(entry.nameBytes.size)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeIntLe(0)
            sink.writeIntLe(entry.offset.toInt())
            sink.write(entry.nameBytes)
            offset += 46L + entry.nameBytes.size
        }

        sink.writeIntLe(0x06054b50)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(entries.size)
        sink.writeShortLe(entries.size)
        sink.writeIntLe((offset - centralDirStart).toInt())
        sink.writeIntLe(centralDirStart.toInt())
        sink.writeShortLe(0)
    }

    private companion object {
        const val FLAG_UTF8_NAMES = 0x0800
        const val DOS_DATE = ((2026 - 1980) shl 9) or (1 shl 5) or 1
        const val DOS_TIME = 0
    }
}

internal object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(data: ByteArray): Int {
        var c = -1
        for (b in data) c = table[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }
}
