package com.annie.memento.di

import com.annie.memento.data.MementoRepository
import com.annie.memento.platform.AudioPlayer
import com.annie.memento.platform.MediaStorage

class AppGraph(
    val repository: MementoRepository,
    val mediaStorage: MediaStorage,
    val audioPlayer: AudioPlayer,
    // scratch space deck exports
    val cacheDirPath: String,
)
