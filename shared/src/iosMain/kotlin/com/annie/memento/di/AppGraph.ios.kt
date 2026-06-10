package com.annie.memento.di

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.annie.memento.data.MementoRepository
import com.annie.memento.db.MementoDatabase
import com.annie.memento.platform.IosAudioPlayer
import com.annie.memento.platform.IosMediaStorage
import com.annie.memento.platform.ioDispatcher
import platform.Foundation.NSTemporaryDirectory

private val graph: AppGraph by lazy { build() }

fun createAppGraph(): AppGraph = graph

private fun build(): AppGraph {
    val driver = NativeSqliteDriver(
        schema = MementoDatabase.Schema,
        name = "memento.db",
        onConfiguration = { configuration: DatabaseConfiguration ->
            configuration.copy(
                extendedConfig = configuration.extendedConfig.copy(foreignKeyConstraints = true),
            )
        },
    )
    val database = MementoDatabase(driver)
    val media = IosMediaStorage()
    return AppGraph(
        repository = MementoRepository(database, media, ioDispatcher),
        mediaStorage = media,
        audioPlayer = IosAudioPlayer(),
        cacheDirPath = NSTemporaryDirectory().trimEnd('/'),
    )
}
