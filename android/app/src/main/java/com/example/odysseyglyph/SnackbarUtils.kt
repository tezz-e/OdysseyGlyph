package com.example.odysseyglyph

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar

fun Snackbar.applyNothingStyle(): Snackbar {
    val layout = this.view as? ViewGroup ?: return this
    val defaultText = layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString() ?: ""

    val density = layout.context.resources.displayMetrics.density
    val sideMarginPx = (16f * density).toInt()
    val baseBottomMarginPx = (16f * density).toInt()

    // Clear default styling and views
    layout.removeAllViews()
    layout.setBackgroundColor(Color.TRANSPARENT)
    layout.setPadding(0, 0, 0, 0)
    layout.elevation = 16f * density

    // Inflate custom Nothing OS layout
    val customView = LayoutInflater.from(layout.context).inflate(R.layout.nothing_snackbar, layout, false)
    val tv = customView.findViewById<TextView>(R.id.tvSnackbarText)
    tv.text = defaultText
    layout.addView(customView)

    fun applyMargins(insets: WindowInsetsCompat?) {
        val params = layout.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val sysBars = insets?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )?.bottom ?: 0
        val ime = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        val dockedBarHeight = layout.rootView?.findViewById<View>(R.id.bottomActionBar)?.let { bar ->
            if (bar.visibility == View.VISIBLE && bar.height > 0) bar.height else 0
        } ?: 0

        val bottomClearance = maxOf(sysBars, ime, dockedBarHeight)
        params.setMargins(sideMarginPx, params.topMargin, sideMarginPx, baseBottomMarginPx + bottomClearance)
        layout.layoutParams = params
    }

    ViewCompat.setOnApplyWindowInsetsListener(layout) { _, windowInsets ->
        applyMargins(windowInsets)
        windowInsets
    }

    if (layout.isAttachedToWindow) {
        applyMargins(ViewCompat.getRootWindowInsets(layout))
        layout.requestApplyInsets()
    } else {
        applyMargins(ViewCompat.getRootWindowInsets(layout))
        layout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                applyMargins(ViewCompat.getRootWindowInsets(v))
                v.requestApplyInsets()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })
    }

    return this
}
