package com.annie.memento.data.ankiImporter

expect class AnkiDb(path: String) {
    fun forEachRow(sql: String, onRow: (List<String?>) -> Unit)

    fun close()
}

internal fun AnkiDb.queryAll(sql: String): List<List<String?>> {
    val rows = ArrayList<List<String?>>()
    forEachRow(sql) { rows.add(it) }
    return rows
}

internal fun AnkiDb.firstRow(sql: String): List<String?>? = queryAll(sql).firstOrNull()
