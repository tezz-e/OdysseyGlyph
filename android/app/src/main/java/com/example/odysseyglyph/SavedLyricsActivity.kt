package com.example.odysseyglyph

import android.animation.LayoutTransition
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class SavedLyricsActivity : AppCompatActivity() {

    private lateinit var repo: SavedLyricsRepository
    private lateinit var savedContainer: LinearLayout
    private lateinit var tvEmptySaved: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_saved_lyrics)

        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBar, applyTop = true, applyBottom = false)
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollView)
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyTop = false, applyBottom = true)

        repo = SavedLyricsRepository.getInstance(this)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        TouchSpringAnimator.attach(btnBack)
        btnBack.setOnClickListener {
            NavigationHelper.finishWithPop(this)
        }

        savedContainer = findViewById(R.id.savedContainer)
        savedContainer.layoutTransition = LayoutTransition()
        tvEmptySaved = findViewById(R.id.tvEmptySaved)

        loadSavedMatches()
    }

    private fun loadSavedMatches() {
        savedContainer.removeAllViews()
        val list = repo.getAllMatches()

        if (list.isEmpty()) {
            tvEmptySaved.visibility = View.VISIBLE
            return
        }

        tvEmptySaved.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for (match in list) {
            val itemView = inflater.inflate(R.layout.item_saved_lyric, savedContainer, false)

            val tvTitle = itemView.findViewById<TextView>(R.id.tvSavedTitle)
            val tvArtist = itemView.findViewById<TextView>(R.id.tvSavedArtist)
            val tvMeta = itemView.findViewById<TextView>(R.id.tvSavedMeta)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvSavedBadge)
            val btnDelete = itemView.findViewById<ImageView>(R.id.btnDeleteSaved)

            tvTitle.text = match.trackTitle
            tvArtist.text = match.artistName

            when (match.matchType) {
                SavedLyricsDbHelper.MATCH_TYPE_USER_SELECTED -> {
                    tvBadge.text = "USER MATCH"
                    tvBadge.setTextColor(Color.parseColor("#44FF88"))
                    tvMeta.text = "Matched to: ${match.matchedTrackName} by ${match.matchedArtistName}"
                }
                SavedLyricsDbHelper.MATCH_TYPE_IMPORTED_LRC -> {
                    tvBadge.text = "IMPORTED LRC"
                    tvBadge.setTextColor(Color.parseColor("#33BBFF"))
                    tvMeta.text = "Imported custom .lrc file"
                }
                else -> {
                    tvBadge.text = "VERIFIED LOCAL"
                    tvBadge.setTextColor(Color.parseColor("#888888"))
                    tvMeta.text = "Auto-verified: ${match.matchedTrackName}"
                }
            }

            TouchSpringAnimator.attach(btnDelete)
            btnDelete.setOnClickListener {
                repo.forgetMatch(match.trackKey)
                savedContainer.removeView(itemView)
                if (savedContainer.childCount == 0) {
                    tvEmptySaved.visibility = View.VISIBLE
                }
                Snackbar.make(findViewById(android.R.id.content), "Deleted saved match.", Snackbar.LENGTH_SHORT)
                    .applyNothingStyle()
                    .show()
            }

            savedContainer.addView(itemView)
        }
    }
}
