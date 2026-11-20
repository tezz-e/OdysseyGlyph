package com.example.odysseyglyph

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.random.Random

class SplashActivity : AppCompatActivity() {

    private lateinit var tvGlitchText: TextView
    private val handler = Handler(Looper.getMainLooper())
    
    private val targetText = "ODYSSEY"
    private val glitchChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*"
    private var glitchIterations = 0
    private val maxIterations = 15
    private val finalDelayMs = 600L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make it fully immersive / fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        setContentView(R.layout.activity_splash)
        
        tvGlitchText = findViewById(R.id.tvGlitchText)
        try {
            val ndotTypeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.ndot57)
            if (ndotTypeface != null) {
                tvGlitchText.typeface = ndotTypeface
            }
        } catch (_: Exception) {}
        
        // Start Glitch Animation
        handler.postDelayed(glitchRunnable, 100)
    }
    
    private val glitchRunnable = object : Runnable {
        override fun run() {
            if (glitchIterations < maxIterations) {
                // Generate random string of same length
                val sb = StringBuilder()
                for (i in targetText.indices) {
                    if (Random.nextFloat() > 0.4f) {
                        sb.append(glitchChars[Random.nextInt(glitchChars.length)])
                    } else {
                        sb.append(targetText[i])
                    }
                }
                tvGlitchText.text = sb.toString()
                
                // Randomly flash red
                if (Random.nextFloat() > 0.8f) {
                    tvGlitchText.setTextColor(Color.parseColor("#FF0000"))
                } else {
                    tvGlitchText.setTextColor(Color.WHITE)
                }
                
                glitchIterations++
                handler.postDelayed(this, 60L) // 60ms between glitches
            } else {
                // Settle on final text
                tvGlitchText.text = targetText
                tvGlitchText.setTextColor(Color.WHITE)
                
                // Wait a bit, then launch MainActivity
                handler.postDelayed({
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                    // Add fade transition
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }, finalDelayMs)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
