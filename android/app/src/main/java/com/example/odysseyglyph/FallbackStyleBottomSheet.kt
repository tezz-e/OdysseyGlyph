package com.example.odysseyglyph

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class FallbackStyleBottomSheet : BottomSheetDialogFragment() {

    private var selectedStyle = 0
    private var onStyleSelected: ((Int) -> Unit)? = null

    data class PatternMeta(val id: Int, val name: String, val badge: String, val isAudio: Boolean)

    private val styles = listOf(
        PatternMeta(0, "NOTE", "STATIC", false),
        PatternMeta(4, "VINYL", "MATH", false),
        PatternMeta(1, "SINE WAVE", "MATH", false),
        PatternMeta(6, "DIGITAL RAIN", "MATH", false),
        PatternMeta(7, "RADAR", "MATH", false),
        PatternMeta(2, "AUDIO WAVE", "AUDIO", true),
        PatternMeta(3, "SUBWOOFER", "AUDIO", true),
        PatternMeta(5, "PULSE RING", "AUDIO", true),
        PatternMeta(8, "BEAT WAVE", "AUDIO", true)
    )

    private val bitmaps = mutableMapOf<Int, Bitmap>()

    fun setOnStyleSelectedListener(listener: (Int) -> Unit) {
        onStyleSelected = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_fallback_style, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundColor(Color.TRANSPARENT)
            com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).apply {
                state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.sheetScrollView)?.let { scrollView ->
            WindowInsetsUtil.applySystemBarsPadding(
                scrollView,
                applyTop = false,
                applyBottom = true,
                includeIme = false
            )
        }

        val prefs = requireContext().getSharedPreferences("OdysseyPrefs", android.content.Context.MODE_PRIVATE)
        selectedStyle = prefs.getInt("fallback_style", 0)

        // Pre-generate bitmaps for snappy UI
        val engine = GlyphFontEngine
        styles.forEach { meta ->
            bitmaps[meta.id] = engine.generatePreviewBitmap(meta.id)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvFallbackStyles)
        rv.layoutManager = GridLayoutManager(requireContext(), 3)
        val adapter = StyleAdapter()
        rv.adapter = adapter

        val btnApply = view.findViewById<MaterialButton>(R.id.btnApplyFallback)
        btnApply.applyLiquidGlass(pressedScale = 0.96f)
        btnApply.setOnClickListener {
            prefs.edit().putInt("fallback_style", selectedStyle).apply()
            onStyleSelected?.invoke(selectedStyle)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bitmaps.values.forEach { it.recycle() }
        bitmaps.clear()
    }

    private inner class StyleAdapter : RecyclerView.Adapter<StyleAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardContainer: View = view.findViewById(R.id.cardContainer)
            val tvBadge: TextView = view.findViewById(R.id.tvBadge)
            val viewLedIndicator: View = view.findViewById(R.id.viewLedIndicator)
            val ivPreview: ImageView = view.findViewById(R.id.ivPreview)
            val tvName: TextView = view.findViewById(R.id.tvName)

            init {
                cardContainer.applyLiquidGlass(pressedScale = 0.94f)
                cardContainer.setOnClickListener {
                    val prevSelected = selectedStyle
                    selectedStyle = styles[adapterPosition].id

                    val prevIndex = styles.indexOfFirst { it.id == prevSelected }
                    if (prevIndex != -1) notifyItemChanged(prevIndex)
                    notifyItemChanged(adapterPosition)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fallback_style, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val meta = styles[position]
            holder.tvName.text = meta.name
            holder.tvBadge.text = meta.badge
            holder.ivPreview.setImageBitmap(bitmaps[meta.id])

            // Badge color
            if (meta.isAudio) {
                holder.tvBadge.setTextColor(Color.parseColor("#EA3323")) // Red for mic-reactive
            } else if (meta.badge == "MATH") {
                holder.tvBadge.setTextColor(Color.parseColor("#AAAAAA"))
            } else {
                holder.tvBadge.setTextColor(Color.parseColor("#666666"))
            }

            // Selection state
            val isSelected = (meta.id == selectedStyle)
            if (isSelected) {
                holder.cardContainer.setBackgroundResource(R.drawable.bg_pattern_card_selected)
                holder.tvName.setTextColor(Color.WHITE)

                val ledDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EA3323"))
                }
                holder.viewLedIndicator.background = ledDrawable
            } else {
                holder.cardContainer.setBackgroundResource(R.drawable.bg_pattern_card)
                holder.tvName.setTextColor(Color.parseColor("#888888"))

                val ledDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222226"))
                }
                holder.viewLedIndicator.background = ledDrawable
            }
        }

        override fun getItemCount() = styles.size
    }
}
