package com.annie.memento.data.ankiImporter

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.createDatabaseManager
import okio.Path.Companion.toPath

// sqliter
actual class AnkiDb actual constructor(path: String) {
    private val connection: DatabaseConnection = try {
        val p = path.toPath()
        val manager = createDatabaseManager(
            DatabaseConfiguration(
                name = p.name,
                version = 1,
                create = {},
                upgrade = { _, _, _ -> },
                journalMode = JournalMode.DELETE,
                extendedConfig = DatabaseConfiguration.Extended(basePath = p.parent?.toString() ?: "."),
            ),
        )
        manager.createMultiThreadedConnection()
    } catch (e: Exception) {
        throw ApkgException.CorruptCollection("couldn't open collection database", e)
    }

    actual fun forEachRow(sql: String, onRow: (List<String?>) -> Unit) {
        try {
            val statement = connection.createStatement(sql)
            try {
                val cursor = statement.query()
                val count = cursor.columnCount
                while (cursor.next()) {
                    onRow(List(count) { i -> if (cursor.isNull(i)) null else cursor.getString(i) })
                }
            } finally {
                statement.finalizeStatement()
            }
        } catch (e: Exception) {
            if (e is ApkgException) throw e
            throw ApkgException.CorruptCollection("collection query failed", e)
        }
    }

    actual fun close() {
        runCatching { connection.close() }
    }
}
