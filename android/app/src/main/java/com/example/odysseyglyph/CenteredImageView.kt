package com.example.odysseyglyph

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.util.AttributeSet
import com.otaliastudios.zoom.ZoomImageView

class CenteredImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ZoomImageView(context, attrs) {

    init {
        setMinZoom(0.2f)
        setMaxZoom(5.0f)
        setOverScrollHorizontal(true)
        setOverScrollVertical(true)
        setOverPinchable(true)
        setTransformation(
            com.otaliastudios.zoom.ZoomApi.TRANSFORMATION_CENTER_CROP,
            com.otaliastudios.zoom.ZoomApi.TRANSFORMATION_GRAVITY_AUTO
        )
    }

    fun setImageURIWithAnim(uri: Uri) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = if (uri.scheme == "file" && uri.path != null) {
                    ImageDecoder.createSource(java.io.File(uri.path!!))
                } else {
                    ImageDecoder.createSource(context.contentResolver, uri)
                }
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                    // Prevent GPU texture size limit crashes or OutOfMemoryErrors on 50MP images
                    var sampleSize = 1
                    while (info.size.width / sampleSize > 2000 || info.size.height / sampleSize > 2000) {
                        sampleSize *= 2
                    }
                    if (sampleSize > 1) {
                        decoder.setTargetSampleSize(sampleSize)
                    }
                }
                setImageDrawable(drawable)
                if (drawable is AnimatedImageDrawable) {
                    drawable.start()
                }
            } else {
                setImageURI(uri)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // Fallback
            try {
                setImageURI(uri)
            } catch (fallbackEx: Throwable) {
                fallbackEx.printStackTrace()
            }
        }
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_POINTER_DOWN,
            android.view.MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}
