package net.pipe01.pinepartner.scripting.api.adapters

import android.media.MediaMetadata
import android.media.session.PlaybackState
import net.pipe01.pinepartner.scripting.api.ApiScriptableObject
import org.mozilla.javascript.annotations.JSGetter
import kotlin.properties.Delegates

class PlaybackStateAdapter : ApiScriptableObject(PlaybackStateAdapter::class) {
    companion object {
        private val emptyMetadata = MediaMetadata.Builder().build()
    }

    private lateinit var playbackState: PlaybackState
    private lateinit var metadata: MediaMetadata
    private var hasMetadata by Delegates.notNull<Boolean>()

    fun init(playbackState: PlaybackState, metadata: MediaMetadata?) {
        this.playbackState = playbackState
        this.metadata = metadata ?: emptyMetadata
        this.hasMetadata = metadata != null
    }

    @JSGetter
    fun getIsPlaying() = playbackState.state == PlaybackState.STATE_PLAYING

    @JSGetter
    fun getPosition() = (playbackState.position / 1000).toInt()

    @JSGetter
    fun getDuration() = (metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000).toInt()

    @JSGetter
    fun getArtist(): String? {
        if (!hasMetadata) return null

        return metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
    }

    @JSGetter
    fun getTitle(): String? {
        if (!hasMetadata) return null

        return metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
    }

    @JSGetter
    fun getAlbum(): String? {
        if (!hasMetadata) return null

        return metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
    }
}