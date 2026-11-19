package com.example.odysseyglyph

import android.graphics.Bitmap
import android.media.session.PlaybackState

object MusicPlaybackState {
    var trackTitle: String = ""
    var artist: String = ""
    var album: String = ""
    var durationMs: Long = 0L
    var packageName: String = ""
    var mediaId: String = ""
    var albumArt: Bitmap? = null
    
    var isPlaying: Boolean = false
    var position: Long = 0
    var lastUpdateTime: Long = 0
    var playbackSpeed: Float = 1.0f
    
    var hasActiveSession: Boolean = false
    @Volatile var manualOverrideLyrics: List<Pair<Long, String>>? = null
    @Volatile var manualOverrideTrackName: String = ""
    var activeSource: String = "" // e.g. "Spotify" or "YT Music"
    
    fun currentIdentity(): MediaTrackIdentity {
        return MediaTrackIdentity(
            title = trackTitle,
            artist = artist,
            album = album,
            durationMs = durationMs,
            packageName = packageName,
            mediaId = mediaId
        )
    }

    interface StateChangeListener {
        fun onMetadataChanged(title: String, artist: String) {}
        fun onTrackIdentityChanged(identity: MediaTrackIdentity) {}
        fun onPlaybackStateChanged(isPlaying: Boolean) {}
    }
    
    private val listeners = mutableListOf<StateChangeListener>()
    
    fun addListener(listener: StateChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: StateChangeListener) {
        listeners.remove(listener)
    }
    
    fun updateMetadata(
        newTitle: String,
        newArtist: String,
        newAlbum: String = "",
        newDurationMs: Long = 0L,
        newPackageName: String = "",
        newMediaId: String = "",
        source: String = "",
        art: Bitmap? = null
    ) {
        val hasChanged = trackTitle != newTitle ||
                artist != newArtist ||
                album != newAlbum ||
                durationMs != newDurationMs

        if (hasChanged) {
            trackTitle = newTitle
            artist = newArtist
            album = newAlbum
            durationMs = newDurationMs
            packageName = newPackageName
            mediaId = newMediaId
            albumArt = art
            activeSource = source
            manualOverrideLyrics = null
            manualOverrideTrackName = ""

            val identity = currentIdentity()
            val listenersCopy = synchronized(listeners) { listeners.toList() }
            listenersCopy.forEach {
                it.onMetadataChanged(trackTitle, artist)
                it.onTrackIdentityChanged(identity)
            }
        }
    }
    
    fun updatePlaybackState(state: PlaybackState?) {
        if (state != null) {
            val newIsPlaying = state.state == PlaybackState.STATE_PLAYING
            position = state.position
            lastUpdateTime = state.lastPositionUpdateTime
            playbackSpeed = state.playbackSpeed
            
            if (isPlaying != newIsPlaying) {
                isPlaying = newIsPlaying
                val listenersCopy = synchronized(listeners) { listeners.toList() }
                listenersCopy.forEach { it.onPlaybackStateChanged(isPlaying) }
            }
        }
    }
}
