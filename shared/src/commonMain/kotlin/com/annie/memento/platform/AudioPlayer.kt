package com.annie.memento.platform

import kotlinx.coroutines.flow.StateFlow

//plays 1 audio file at a time
interface AudioPlayer {
    val playing: StateFlow<String?>

    //stops any currently playing files and starts playing from file path
    fun play(absolutePath: String)

    fun stop()

    //release resources
    fun release()
}
