package com.annie.memento.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosAudioPlayer : AudioPlayer {

    private val _playing = MutableStateFlow<String?>(null)
    override val playing: StateFlow<String?> = _playing.asStateFlow()

    private var player: AVAudioPlayer? = null
    private var delegate: NSObject? = null

    override fun play(absolutePath: String) {
        stop()
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
            val created = AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(absolutePath), error = null)
            val finishDelegate = AudioCompletionDelegate {
                if (player === created) {
                    player = null
                    delegate = null
                    _playing.value = null
                }
            }
            created.delegate = finishDelegate
            delegate = finishDelegate
            player = created
            created.prepareToPlay()
            created.play()
            _playing.value = absolutePath
        }.onFailure {
            player = null
            delegate = null
            _playing.value = null
        }
    }

    override fun stop() {
        player?.stop()
        player = null
        delegate = null
        _playing.value = null
    }

    override fun release() = stop()
}

private class AudioCompletionDelegate(
    private val onFinish: () -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinish()
    }
}
