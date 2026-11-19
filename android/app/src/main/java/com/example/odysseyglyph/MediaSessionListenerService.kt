package com.example.odysseyglyph

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

class MediaSessionListenerService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var isConnected = false
    private var activeSource = ""
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    /**
     * Active position re-sync: Spotify/YT Music only send PlaybackState callbacks on
     * state CHANGES (play, pause, skip). Between those events, our position estimate
     * drifts because we're extrapolating from stale data. This runnable actively polls 
     * the MediaController every 500ms to get fresh position data, preventing drift.
     */
    private val positionSyncRunnable = object : Runnable {
        override fun run() {
            if (!isConnected) return
            activeController?.playbackState?.let { state ->
                MusicPlaybackState.updatePlaybackState(state)
            }
            handler.postDelayed(this, 500L)
        }
    }
    
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            
            // Some players send an empty metadata bundle initially; don't wipe existing valid data if so
            if (metadata == null) return
            
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) 
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) 
                ?: ""
                
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) 
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) 
                ?: ""
                
            val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                ?: ""

            val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
            val pkg = activeController?.packageName ?: ""

            val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                
            Log.d("MusicListener", "Metadata changed: $title - $artist [$album, ${durationMs}ms]")
            
            // Only update if we actually got a title, or if we were previously unknown.
            if (title.isNotEmpty() || MusicPlaybackState.trackTitle.isEmpty()) {
                MusicPlaybackState.updateMetadata(
                    newTitle = title,
                    newArtist = artist,
                    newAlbum = album,
                    newDurationMs = durationMs,
                    newPackageName = pkg,
                    newMediaId = mediaId,
                    source = activeSource,
                    art = art
                )
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            Log.d("MusicListener", "Playback state changed: ${state?.state}")
            MusicPlaybackState.updatePlaybackState(state)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        
        try {
            val sessions = mediaSessionManager?.getActiveSessions(componentName)
            sessions?.let { updateActiveSessions(it) }
            
            mediaSessionManager?.addOnActiveSessionsChangedListener({ newSessions ->
                updateActiveSessions(newSessions)
            }, componentName)
            
        } catch (e: SecurityException) {
            Log.e("MusicListener", "Missing Notification Access permission", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        handler.removeCallbacks(positionSyncRunnable)
        
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        MusicPlaybackState.hasActiveSession = false
    }

    private fun updateActiveSessions(controllers: List<MediaController>?) {
        if (!isConnected || controllers.isNullOrEmpty()) {
            if (activeController != null) {
                activeController?.unregisterCallback(controllerCallback)
                handler.removeCallbacks(positionSyncRunnable)
                activeController = null
                MusicPlaybackState.hasActiveSession = false
                Log.d("MusicListener", "Music session ended")
            }
            return
        }
        
        val preferredPlaying = controllers.find { 
            (it.packageName == "com.spotify.music" || it.packageName == "com.google.android.apps.youtube.music") && it.playbackState?.state == PlaybackState.STATE_PLAYING 
        }
        
        val anyPlaying = controllers.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        
        val preferredAny = controllers.find { 
            it.packageName == "com.spotify.music" || it.packageName == "com.google.android.apps.youtube.music" 
        }
        
        val musicController = preferredPlaying ?: anyPlaying ?: preferredAny ?: controllers.firstOrNull()
        
        if (musicController != null) {
            if (activeController?.sessionToken != musicController.sessionToken) {
                activeController?.unregisterCallback(controllerCallback)
                handler.removeCallbacks(positionSyncRunnable)
                
                activeController = musicController
                
                val pm = packageManager
                activeSource = try {
                    val info = pm.getApplicationInfo(musicController.packageName, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    musicController.packageName
                }
                
                activeController?.registerCallback(controllerCallback)
                
                MusicPlaybackState.hasActiveSession = true
                Log.d("MusicListener", "Found active session: $activeSource")
                
                // Initial state dump
                controllerCallback.onMetadataChanged(musicController.metadata)
                controllerCallback.onPlaybackStateChanged(musicController.playbackState)
                
                // Start active position polling to prevent drift
                handler.post(positionSyncRunnable)
            }
        }
    }
}
