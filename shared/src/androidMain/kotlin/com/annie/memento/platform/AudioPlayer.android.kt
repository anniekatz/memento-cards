package com.annie.memento.platform

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAudioPlayer : AudioPlayer {

    private val _playing = MutableStateFlow<String?>(null)
    override val playing: StateFlow<String?> = _playing.asStateFlow()

    private var player: MediaPlayer? = null

    override fun play(absolutePath: String) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setOnCompletionListener { mp ->
                    mp.release()
                    if (player === mp) {
                        player = null
                        _playing.value = null
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (player === mp) {
                        player = null
                        _playing.value = null
                    }
                    true
                }
                setDataSource(absolutePath)
                prepare()
                start()
            }
            _playing.value = absolutePath
        }.onFailure {
            player?.release()
            player = null
            _playing.value = null
        }
    }

    override fun stop() {
        player?.let { mp ->
            runCatching { mp.stop() }
            mp.release()
        }
        player = null
        _playing.value = null
    }

    override fun release() = stop()
}
