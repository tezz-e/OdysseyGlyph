import sys

with open('android/app/src/main/java/com/example/odysseyglyph/OdysseyToyService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

imports = """import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.os.Build
"""
content = content.replace('import java.io.InputStream\n', 'import java.io.InputStream\n' + imports)

# We need to add the following to BaseToyService:
# 1. override fun onStartCommand
# 2. override fun getFramesFileName to support dynamic override, but wait, it's abstract.
# Let's change `abstract fun getFramesFileName(): String` to `open fun getFramesFileName(): String = "frames.bin"` in BaseToyService?
# Or just store a variable `standaloneFileName` and modify the subclasses?
# Wait, if we start OdysseyToyServiceSlot1, its `getFramesFileName` returns "frames_1.bin". 
# So if the user taps "Play" on Slot 1 in Gallery, we start OdysseyToyServiceSlot1, and it naturally loads "frames_1.bin". We don't need to override it!

onstart = """
    companion object {
        const val ACTION_START_STANDALONE = "com.example.odysseyglyph.ACTION_START_STANDALONE"
        const val ACTION_STOP_STANDALONE = "com.example.odysseyglyph.ACTION_STOP_STANDALONE"
    }

    private var isStandalone = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_STANDALONE) {
            isStandalone = true
            createNotificationChannel()
            val stopIntent = Intent(this, this::class.java).apply {
                action = ACTION_STOP_STANDALONE
            }
            val stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, "toy_playback")
                .setContentTitle("Playing Glyph Toy")
                .setContentText("Playing \${getFramesFileName()}")
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
                .setOngoing(true)
                .build()
                
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(2, notification)
            }
            
            init()
            return START_STICKY
        } else if (intent?.action == ACTION_STOP_STANDALONE) {
            stopStandalone()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "toy_playback",
                "Toy Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopStandalone() {
        if (isStandalone) {
            isStandalone = false
            mainHandler.removeCallbacks(playbackRunnable)
            glyphManager?.turnOff()
            glyphManager?.unInit()
            glyphManager = null
            mediaPlayer?.release()
            mediaPlayer = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
"""

# Insert onstart after abstract fun getFramesFileName(): String
content = content.replace('abstract fun getFramesFileName(): String\n', 'abstract fun getFramesFileName(): String\n' + onstart)

# Modify onUnbind to only unInit if not standalone?
# Actually, if it's bound by the system Toy interface, it's not standalone.
# If it's started by our app, onUnbind is never called.
# So we don't need to change onUnbind.

with open('android/app/src/main/java/com/example/odysseyglyph/OdysseyToyService.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("OdysseyToyService modified")
