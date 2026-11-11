package com.example.odysseyglyph

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import java.util.Random

/**
 * CipherTextView
 * Renders an alphanumeric cipher scramble effect on text transitions.
 * Inspired by ReactBits DecryptedText and Teenage Engineering hardware displays.
 */
class CipherTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val cipherRunes = charArrayOf('█', '░', '▒', '▓', '0', '1', 'X', '#', '*', '+', '-', '/')
    private val random = Random()
    private var animator: ValueAnimator? = null
    private var finalTargetString: String = text.toString()
    private var displayBuffer = CharArray(0)

    fun animateDecryption(newText: String, durationMs: Long = 450) {
        animator?.cancel()
        finalTargetString = newText
        val len = newText.length
        if (len == 0) {
            text = ""
            return
        }

        if (displayBuffer.size != len) {
            displayBuffer = CharArray(len)
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val progress = va.animatedFraction
                val resolvedCharsCount = (len * progress).toInt()

                for (i in 0 until len) {
                    val targetChar = finalTargetString[i]
                    if (targetChar == ' ' || targetChar == '\n') {
                        displayBuffer[i] = targetChar
                    } else if (i < resolvedCharsCount) {
                        displayBuffer[i] = targetChar
                    } else {
                        displayBuffer[i] = cipherRunes[random.nextInt(cipherRunes.size)]
                    }
                }
                text = String(displayBuffer)
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
