package com.annie.memento.data.ankiImporter

import android.database.sqlite.SQLiteDatabase

actual class AnkiDb actual constructor(path: String) {
    private val db: SQLiteDatabase = try {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
    } catch (e: Exception) {
        throw ApkgException.CorruptCollection("couldn't open collection database", e)
    }

    actual fun forEachRow(sql: String, onRow: (List<String?>) -> Unit) {
        try {
            db.rawQuery(sql, null).use { cursor ->
                val count = cursor.columnCount
                while (cursor.moveToNext()) {
                    onRow(List(count) { i -> if (cursor.isNull(i)) null else cursor.getString(i) })
                }
            }
        } catch (e: Exception) {
            if (e is ApkgException) throw e
            throw ApkgException.CorruptCollection("collection query failed", e)
        }
    }

    actual fun close() {
        runCatching { db.close() }
    }
}
