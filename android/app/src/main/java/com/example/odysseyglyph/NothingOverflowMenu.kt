package com.example.odysseyglyph

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import android.widget.LinearLayout

/**
 * NothingOverflowMenu
 *
 * Custom Nothing OS dark glass popup menu anchored to the header 3-dot button.
 * Features dark obsidian frosted glass (#141418), subtle specular border,
 * and Nothing typography.
 */
class NothingOverflowMenu(
    private val context: Context,
    private val onTourClick: () -> Unit,
    private val onTileClick: () -> Unit,
    private val onWidgetClick: () -> Unit,
    private val onGithubClick: () -> Unit,
    private val onAboutClick: () -> Unit,
    private val onPrivacyClick: () -> Unit = {}
) {

    private val popupWindow: PopupWindow
    val contentView: View

    init {
        val inflater = LayoutInflater.from(context)
        contentView = inflater.inflate(R.layout.layout_nothing_overflow_menu, null)

        popupWindow = PopupWindow(
            contentView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 24f * context.resources.displayMetrics.density
            animationStyle = android.R.style.Animation_Dialog
        }

        contentView.findViewById<View>(R.id.itemMenuTour)?.setOnClickListener {
            popupWindow.dismiss()
            onTourClick()
        }

        contentView.findViewById<View>(R.id.itemMenuTile)?.setOnClickListener {
            popupWindow.dismiss()
            onTileClick()
        }

        contentView.findViewById<View>(R.id.itemMenuWidget)?.setOnClickListener {
            popupWindow.dismiss()
            onWidgetClick()
        }

        contentView.findViewById<View>(R.id.itemMenuGithub)?.setOnClickListener {
            popupWindow.dismiss()
            onGithubClick()
        }

        contentView.findViewById<View>(R.id.itemMenuAbout)?.setOnClickListener {
            popupWindow.dismiss()
            onAboutClick()
        }

        contentView.findViewById<View>(R.id.itemMenuPrivacy)?.setOnClickListener {
            popupWindow.dismiss()
            onPrivacyClick()
        }
    }

    val isShowing: Boolean
        get() = popupWindow.isShowing

    fun show(anchorView: View, focusable: Boolean = true) {
        popupWindow.isFocusable = focusable
        val density = context.resources.displayMetrics.density
        // Show below anchor, offset left so right edge aligns neatly
        val xOff = (-200 * density).toInt()
        val yOff = (4 * density).toInt()
        if (!popupWindow.isShowing) {
            popupWindow.showAsDropDown(anchorView, xOff, yOff)
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
