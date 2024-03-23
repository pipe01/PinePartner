package net.pipe01.pinepartner.scripting.api.adapters

import android.media.AudioManager
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter
import org.mozilla.javascript.annotations.JSSetter
import kotlin.properties.Delegates

class VolumeStreamAdapter : ApiScriptableObject(VolumeStreamAdapter::class) {
    private var volumeStream by Delegates.notNull<Int>()
    private lateinit var audioManager: AudioManager

    fun init(volumeStream: Int, audioManager: AudioManager) {
        this.volumeStream = volumeStream
        this.audioManager = audioManager
    }

    @JSGetter
    fun getVolume() = audioManager.getStreamVolume(volumeStream)

    @JSSetter
    fun setVolume(volume: Int) {
        audioManager.setStreamVolume(volumeStream, volume, AudioManager.FLAG_SHOW_UI)
    }

    @JSFunction
    fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(volumeStream, direction, AudioManager.FLAG_SHOW_UI)
    }
}