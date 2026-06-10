package com.annie.memento.data.ankiImporter

import okio.Buffer
import okio.BufferedSource

internal fun BufferedSource.readVarint(): Long {
    var result = 0L
    var shift = 0
    while (true) {
        val b = readByte().toInt() and 0xFF
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
        if (shift >= 64) throw ApkgException.CorruptArchive("malformed varint")
    }
}

private fun BufferedSource.skipProtoField(wireType: Int) {
    when (wireType) {
        0 -> readVarint()
        1 -> skip(8)
        2 -> skip(readVarint())
        5 -> skip(4)
        else -> throw ApkgException.CorruptArchive("unsupported protobuf wire type $wireType")
    }
}

internal fun parseMetaVersion(source: BufferedSource): Int? {
    while (!source.exhausted()) {
        val tag = source.readVarint()
        val field = (tag ushr 3).toInt()
        val wire = (tag and 0x7L).toInt()
        if (field == 1 && wire == 0) return source.readVarint().toInt()
        source.skipProtoField(wire)
    }
    return null
}

internal fun parseMediaEntryNames(source: BufferedSource): List<String> {
    val names = ArrayList<String>()
    while (!source.exhausted()) {
        val tag = source.readVarint()
        val field = (tag ushr 3).toInt()
        val wire = (tag and 0x7L).toInt()
        if (field == 1 && wire == 2) {
            val length = source.readVarint()
            val entry = Buffer()
            entry.write(source, length)
            var name: String? = null
            while (!entry.exhausted()) {
                val innerTag = entry.readVarint()
                val innerField = (innerTag ushr 3).toInt()
                val innerWire = (innerTag and 0x7L).toInt()
                if (innerField == 1 && innerWire == 2) {
                    name = entry.readUtf8(entry.readVarint())
                } else {
                    entry.skipProtoField(innerWire)
                }
            }
            names.add(name.orEmpty())
        } else {
            source.skipProtoField(wire)
        }
    }
    return names
}
