package net.pipe01.pinepartner.scripting.api

import android.media.AudioManager
import net.pipe01.pinepartner.scripting.api.adapters.VolumeStreamAdapter
import org.mozilla.javascript.annotations.JSGetter

class Volume : ApiScriptableObject("Volume") {
    private lateinit var audioManager: AudioManager

    fun init(audioManager: AudioManager) {
        this.audioManager = audioManager
    }

    @JSGetter
    fun getVoiceCallStream() = getStream(AudioManager.STREAM_VOICE_CALL)

    @JSGetter
    fun getSystemStream() = getStream(AudioManager.STREAM_SYSTEM)

    @JSGetter
    fun getRingStream() = getStream(AudioManager.STREAM_RING)

    @JSGetter
    fun getMusicStream() = getStream(AudioManager.STREAM_MUSIC)

    @JSGetter
    fun getAlarmStream() = getStream(AudioManager.STREAM_ALARM)

    @JSGetter
    fun getNotificationStream() = getStream(AudioManager.STREAM_NOTIFICATION)

    @JSGetter
    fun getAccessibilityStream() = getStream(AudioManager.STREAM_ACCESSIBILITY)

    private fun getStream(index: Int): VolumeStreamAdapter {
        return newObject<VolumeStreamAdapter>("VolumeStream").also {
            it.init(index, audioManager)
        }
    }
}