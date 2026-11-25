package com.example.odysseyglyph

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
     * It also verifies that the active session is still valid and not orphaned.
     */
    private val positionSyncRunnable = object : Runnable {
        override fun run() {
            if (!isConnected) return
            val controller = activeController
            val state = controller?.playbackState
            val s = state?.state
            
            if (controller == null || state == null || 
                s == PlaybackState.STATE_NONE || 
                s == PlaybackState.STATE_STOPPED || 
                s == PlaybackState.STATE_ERROR ||
                (s == PlaybackState.STATE_PAUSED && !hasActiveMediaNotification(controller.packageName))) {
                disconnectActiveSession()
                refreshSessions()
                return
            }

            MusicPlaybackState.updatePlaybackState(state)
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
            val s = state?.state
            if (s == null || s == PlaybackState.STATE_NONE || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_ERROR) {
                disconnectActiveSession()
                refreshSessions()
                return
            }
            if (s == PlaybackState.STATE_PAUSED) {
                val pkg = activeController?.packageName
                if (!hasActiveMediaNotification(pkg)) {
                    disconnectActiveSession()
                    refreshSessions()
                    return
                }
            }
            MusicPlaybackState.updatePlaybackState(state)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "REFRESH_SESSIONS") {
            refreshSessions()
        }
        return START_NOT_STICKY
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { newSessions ->
        updateActiveSessions(newSessions)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        
        try {
            refreshSessions()
            
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
            mediaSessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            
        } catch (e: SecurityException) {
            Log.e("MusicListener", "Missing Notification Access permission", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (_: Exception) {}
        disconnectActiveSession()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        isConnected = false
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (_: Exception) {}
        disconnectActiveSession()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        if (sbn.packageName == activeController?.packageName) {
            val isStillPlaying = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (!isStillPlaying && !hasActiveMediaNotification(sbn.packageName)) {
                disconnectActiveSession()
                refreshSessions()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (isMediaNotification(sbn)) {
            refreshSessions()
        }
    }

    private fun hasActiveMediaNotification(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        return try {
            val notifs = activeNotifications ?: return false
            notifs.any { it.packageName == packageName && isMediaNotification(it) }
        } catch (e: Exception) {
            false
        }
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val notif = sbn.notification ?: return false
        if (notif.category == Notification.CATEGORY_TRANSPORT) return true
        
        val extras = notif.extras
        if (extras != null) {
            if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
            val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
            if (template.contains("MediaStyle")) return true
        }
        
        val actions = notif.actions
        if (actions != null && actions.isNotEmpty()) {
            val isOngoing = (notif.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR)) != 0
            if (isOngoing) return true
        }
        
        return false
    }

    private fun isSessionValid(controller: MediaController): Boolean {
        val state = controller.playbackState?.state ?: return false
        
        if (state == PlaybackState.STATE_PLAYING || 
            state == PlaybackState.STATE_BUFFERING ||
            state == PlaybackState.STATE_FAST_FORWARDING ||
            state == PlaybackState.STATE_REWINDING) {
            return true
        }
        
        if (state == PlaybackState.STATE_PAUSED) {
            val title = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: ""
            return title.isNotEmpty() && hasActiveMediaNotification(controller.packageName)
        }
        
        return false
    }

    private fun disconnectActiveSession() {
        if (activeController != null) {
            try {
                activeController?.unregisterCallback(controllerCallback)
            } catch (e: Exception) {}
            handler.removeCallbacks(positionSyncRunnable)
            activeController = null
            activeSource = ""
            MusicPlaybackState.clearSession()
            Log.d("MusicListener", "Music session cleared")
        }
    }

    private fun refreshSessions() {
        if (!isConnected) return
        val componentName = ComponentName(this, MediaSessionListenerService::class.java)
        try {
            val sessions = mediaSessionManager?.getActiveSessions(componentName)
            updateActiveSessions(sessions)
        } catch (e: Exception) {
            Log.e("MusicListener", "Error refreshing sessions", e)
        }
    }

    private fun updateActiveSessions(controllers: List<MediaController>?) {
        if (!isConnected || controllers.isNullOrEmpty()) {
            disconnectActiveSession()
            return
        }
        
        val validControllers = controllers.filter { isSessionValid(it) }
        
        if (validControllers.isEmpty()) {
            disconnectActiveSession()
            return
        }
        
        val preferredPlaying = validControllers.find { 
            (it.packageName == "com.spotify.music" || it.packageName == "com.google.android.apps.youtube.music") && 
            it.playbackState?.state == PlaybackState.STATE_PLAYING 
        }
        
        val anyPlaying = validControllers.find { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        
        val preferredAny = validControllers.find { 
            it.packageName == "com.spotify.music" || it.packageName == "com.google.android.apps.youtube.music" 
        }
        
        val musicController = preferredPlaying ?: anyPlaying ?: preferredAny ?: validControllers.firstOrNull()
        
        if (musicController != null) {
            if (activeController?.sessionToken != musicController.sessionToken) {
                disconnectActiveSession()
                
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
        } else {
            disconnectActiveSession()
        }
    }
}
