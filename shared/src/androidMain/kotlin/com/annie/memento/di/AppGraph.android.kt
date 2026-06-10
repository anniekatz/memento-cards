package com.annie.memento.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.annie.memento.data.MementoRepository
import com.annie.memento.db.MementoDatabase
import com.annie.memento.platform.AndroidAudioPlayer
import com.annie.memento.platform.AndroidMediaStorage
import com.annie.memento.platform.ioDispatcher

private val lock = Any()
private var instance: AppGraph? = null

//dependency graph
fun createAppGraph(context: Context): AppGraph {
    instance?.let { return it }
    return synchronized(lock) {
        instance ?: build(context.applicationContext).also { instance = it }
    }
}

private fun build(appContext: Context): AppGraph {
    val driver = AndroidSqliteDriver(
        schema = MementoDatabase.Schema,
        context = appContext,
        name = "memento.db",
        callback = object : AndroidSqliteDriver.Callback(MementoDatabase.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )
    val database = MementoDatabase(driver)
    val media = AndroidMediaStorage(appContext)
    return AppGraph(
        repository = MementoRepository(database, media, ioDispatcher),
        mediaStorage = media,
        audioPlayer = AndroidAudioPlayer(),
        cacheDirPath = appContext.cacheDir.absolutePath,
    )
}
