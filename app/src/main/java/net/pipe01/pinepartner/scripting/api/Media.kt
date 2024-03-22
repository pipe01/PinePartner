package net.pipe01.pinepartner.scripting.api

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.view.KeyEvent
import net.pipe01.pinepartner.MyNotificationListener
import net.pipe01.pinepartner.scripting.api.adapters.PlaybackStateAdapter
import org.mozilla.javascript.annotations.JSFunction
import org.mozilla.javascript.annotations.JSGetter

class Media : ApiScriptableObject(CLASS_NAME) {
    companion object {
        val CLASS_NAME = "Media"
    }

    private lateinit var mediaSessionManager: MediaSessionManager

    fun init(mediaSessionManager: MediaSessionManager) {
        this.mediaSessionManager = mediaSessionManager
    }

    @JSFunction
    fun play() {
        sendKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    @JSFunction
    fun pause() {
        sendKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    @JSFunction
    fun next() {
        sendKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    @JSFunction
    fun previous() {
        sendKeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    @JSGetter
    fun getState(): PlaybackStateAdapter? {
        val sessions = getSessions()

        if (sessions.isEmpty())
            return null

        return sessions[0].playbackState?.let { state ->
            newObject(PlaybackStateAdapter.CLASS_NAME) {
                init(state, sessions[0].metadata)
            }
        }
    }

    private fun sendKeyCode(keyCode: Int) {
        val sessions = getSessions()

        if (sessions.isEmpty())
            return

        sessions[0].dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        sessions[0].dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun getSessions() = mediaSessionManager.getActiveSessions(ComponentName("net.pipe01.pinepartner", MyNotificationListener::class.simpleName!!))
}