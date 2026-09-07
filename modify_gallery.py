import sys

with open('android/app/src/main/java/com/example/odysseyglyph/GalleryActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

btn_logic = """
        var isPlayingGlyph = false
        val btnPlayOnGlyph = dialogView.findViewById<MaterialButton>(R.id.btnPlayOnGlyph)
        btnPlayOnGlyph?.setOnClickListener {
            if (!isPlayingGlyph) {
                isPlayingGlyph = true
                btnPlayOnGlyph.text = "STOP GLYPH PLAYBACK"
                assignToSlot(preset, 1) // Quick preview uses Slot 1
                
                // Stop any other services first
                val stopLyrics = Intent(this, LiveLyricsService::class.java).apply { action = "STOP_LIVE_LYRICS" }
                val stopVisualizer = Intent(this, VisualizerService::class.java).apply { action = "STOP_VISUALIZER" }
                try { startService(stopLyrics) } catch (e: Exception) {}
                try { startService(stopVisualizer) } catch (e: Exception) {}

                val playIntent = Intent(this, OdysseyToyServiceSlot1::class.java).apply {
                    action = "com.example.odysseyglyph.ACTION_START_STANDALONE"
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    androidx.core.content.ContextCompat.startForegroundService(this, playIntent)
                } else {
                    startService(playIntent)
                }
            } else {
                isPlayingGlyph = false
                btnPlayOnGlyph.text = "PLAY ON GLYPH"
                val stopIntent = Intent(this, OdysseyToyServiceSlot1::class.java).apply {
                    action = "com.example.odysseyglyph.ACTION_STOP_STANDALONE"
                }
                startService(stopIntent)
            }
        }
"""

content = content.replace('dialogView.findViewById<MaterialButton>(R.id.btnSlot1)', btn_logic + '\n        dialogView.findViewById<MaterialButton>(R.id.btnSlot1)')

with open('android/app/src/main/java/com/example/odysseyglyph/GalleryActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("GalleryActivity modified")
