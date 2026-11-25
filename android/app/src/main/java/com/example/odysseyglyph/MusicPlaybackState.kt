package com.example.odysseyglyph

import android.graphics.Bitmap
import android.media.session.PlaybackState

object MusicPlaybackState {
    @Volatile var trackTitle: String = ""
    @Volatile var artist: String = ""
    @Volatile var album: String = ""
    @Volatile var durationMs: Long = 0L
    @Volatile var packageName: String = ""
    @Volatile var mediaId: String = ""
    @Volatile var albumArt: Bitmap? = null
    
    @Volatile var isPlaying: Boolean = false
    @Volatile var position: Long = 0
    @Volatile var lastUpdateTime: Long = 0
    @Volatile var playbackSpeed: Float = 1.0f
    
    @Volatile var hasActiveSession: Boolean = false
    @Volatile var manualOverrideLyrics: List<Pair<Long, String>>? = null
    @Volatile var manualOverrideTrackName: String = ""
    @Volatile var activeSource: String = "" // e.g. "Spotify" or "YT Music"
    
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
    
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<StateChangeListener>()
    
    fun addListener(listener: StateChangeListener) {
        listeners.addIfAbsent(listener)
    }
    
    fun removeListener(listener: StateChangeListener) {
        listeners.remove(listener)
    }
    
    fun clearSession() {
        val wasActive = hasActiveSession || trackTitle.isNotEmpty() || isPlaying
        hasActiveSession = false
        isPlaying = false
        trackTitle = ""
        artist = ""
        album = ""
        durationMs = 0L
        packageName = ""
        mediaId = ""
        albumArt = null
        activeSource = ""
        manualOverrideLyrics = null
        manualOverrideTrackName = ""
        position = 0L
        lastUpdateTime = 0L
        
        if (wasActive) {
            val identity = currentIdentity()
            val listenersCopy = synchronized(listeners) { listeners.toList() }
            listenersCopy.forEach {
                it.onMetadataChanged("", "")
                it.onTrackIdentityChanged(identity)
                it.onPlaybackStateChanged(false)
            }
        }
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
        if (state == null || 
            state.state == PlaybackState.STATE_NONE || 
            state.state == PlaybackState.STATE_STOPPED || 
            state.state == PlaybackState.STATE_ERROR) {
            if (hasActiveSession || isPlaying || trackTitle.isNotEmpty()) {
                clearSession()
            }
            return
        }

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
