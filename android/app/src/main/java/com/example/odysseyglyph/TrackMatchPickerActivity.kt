package com.example.odysseyglyph

import android.animation.LayoutTransition
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.concurrent.thread

class TrackMatchPickerActivity : AppCompatActivity() {

    private lateinit var identity: MediaTrackIdentity
    private lateinit var repo: SavedLyricsRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var tvNowPlayingTitle: TextView
    private lateinit var tvNowPlayingArtist: TextView
    private lateinit var tvNowPlayingMeta: TextView
    private lateinit var tvMatchStatusBadge: TextView
    private lateinit var btnForgetMatch: MaterialButton

    private lateinit var loadingCandidates: ProgressBar
    private lateinit var tvEmptyCandidates: TextView
    private lateinit var candidatesContainer: LinearLayout

    private lateinit var etSearchQuery: TextInputEditText
    private lateinit var btnSearchAgain: MaterialButton
    private lateinit var btnImportLrc: MaterialButton
    private lateinit var btnKeepFallback: MaterialButton

    private val importLrcLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            importLrcFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsUtil.setupEdgeToEdge(this)
        setContentView(R.layout.activity_track_match_picker)

        val appBar = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        WindowInsetsUtil.applySystemBarsPadding(appBar, applyTop = true)
        val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollView)
        WindowInsetsUtil.applySystemBarsPadding(scrollView, applyBottom = true)

        repo = SavedLyricsRepository.getInstance(this)

        val title = intent.getStringExtra("track_title") ?: MusicPlaybackState.trackTitle
        val artist = intent.getStringExtra("artist") ?: MusicPlaybackState.artist
        val album = intent.getStringExtra("album") ?: MusicPlaybackState.album
        val durationMs = intent.getLongExtra("duration_ms", MusicPlaybackState.durationMs)
        val pkg = intent.getStringExtra("package_name") ?: MusicPlaybackState.packageName

        identity = MediaTrackIdentity(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            packageName = pkg
        )

        bindViews()
        renderNowPlayingInfo()
        loadExistingMatchOrCandidates()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val title = intent?.getStringExtra("track_title") ?: MusicPlaybackState.trackTitle
        val artist = intent?.getStringExtra("artist") ?: MusicPlaybackState.artist
        val album = intent?.getStringExtra("album") ?: MusicPlaybackState.album
        val durationMs = intent?.getLongExtra("duration_ms", MusicPlaybackState.durationMs) ?: MusicPlaybackState.durationMs
        val pkg = intent?.getStringExtra("package_name") ?: MusicPlaybackState.packageName

        identity = MediaTrackIdentity(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            packageName = pkg
        )
        renderNowPlayingInfo()
        loadExistingMatchOrCandidates()
    }

    private fun bindViews() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            NavigationHelper.finishWithPop(this)
        }

        tvNowPlayingTitle = findViewById(R.id.tvNowPlayingTitle)
        tvNowPlayingArtist = findViewById(R.id.tvNowPlayingArtist)
        tvNowPlayingMeta = findViewById(R.id.tvNowPlayingMeta)
        tvMatchStatusBadge = findViewById(R.id.tvMatchStatusBadge)
        btnForgetMatch = findViewById(R.id.btnForgetMatch)

        loadingCandidates = findViewById(R.id.loadingCandidates)
        tvEmptyCandidates = findViewById(R.id.tvEmptyCandidates)
        candidatesContainer = findViewById(R.id.candidatesContainer)
        candidatesContainer.layoutTransition = LayoutTransition()

        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnSearchAgain = findViewById(R.id.btnSearchAgain)
        btnImportLrc = findViewById(R.id.btnImportLrc)
        btnKeepFallback = findViewById(R.id.btnKeepFallback)

        TouchSpringAnimator.attach(btnSearchAgain)
        TouchSpringAnimator.attach(btnImportLrc)
        TouchSpringAnimator.attach(btnForgetMatch)

        btnSearchAgain.setOnClickListener {
            val q = etSearchQuery.text?.toString()?.trim() ?: ""
            if (q.isNotEmpty()) {
                performCustomSearch(q)
            }
        }

        btnImportLrc.setOnClickListener {
            importLrcLauncher.launch("*/*")
        }

        btnKeepFallback.setOnClickListener {
            NavigationHelper.finishWithPop(this)
        }

        btnForgetMatch.setOnClickListener {
            repo.forgetMatch(identity.normalizedKey())
            MusicPlaybackState.manualOverrideLyrics = null
            MusicPlaybackState.manualOverrideTrackName = ""
            Snackbar.make(findViewById(android.R.id.content), "Removed saved match.", Snackbar.LENGTH_SHORT).applyNothingStyle().show()
            renderNowPlayingInfo()
            loadExistingMatchOrCandidates()
        }
    }

    private fun renderNowPlayingInfo() {
        tvNowPlayingTitle.text = if (identity.title.isNotBlank()) identity.title else "Unknown Track"
        tvNowPlayingArtist.text = if (identity.artist.isNotBlank()) identity.artist else "Unknown Artist"

        val min = (identity.durationMs / 1000) / 60
        val sec = (identity.durationMs / 1000) % 60
        val durationFormatted = if (identity.durationMs > 0) String.format(Locale.ROOT, "%d:%02d", min, sec) else ""
        val metaParts = listOfNotNull(
            if (identity.album.isNotBlank()) identity.album else null,
            if (durationFormatted.isNotBlank()) durationFormatted else null,
            if (identity.packageName.isNotBlank()) identity.packageName.substringAfterLast('.') else null
        )
        tvNowPlayingMeta.text = if (metaParts.isNotEmpty()) metaParts.joinToString(" • ") else "Media Session Active"

        etSearchQuery.setText("${identity.cleanTitle()} ${identity.primaryArtist()}".trim())

        val saved = repo.findMatch(identity)
        if (saved != null) {
            tvMatchStatusBadge.text = if (saved.isUserChoice) "SAVED BY YOU" else "VERIFIED LOCAL"
            tvMatchStatusBadge.setTextColor(Color.parseColor("#44FF88"))
            btnForgetMatch.visibility = View.VISIBLE
        } else {
            tvMatchStatusBadge.text = "NEEDS CONFIRMATION"
            tvMatchStatusBadge.setTextColor(Color.parseColor("#EA3323"))
            btnForgetMatch.visibility = View.GONE
        }
    }

    private fun loadExistingMatchOrCandidates() {
        loadingCandidates.visibility = View.VISIBLE
        tvEmptyCandidates.visibility = View.GONE
        candidatesContainer.removeAllViews()

        thread {
            val titleQuery = identity.cleanTitle()
            val artistQuery = identity.primaryArtist()

            var candidates = if (titleQuery.isNotEmpty()) {
                LRCLibClient.searchCandidates(titleQuery, artistQuery)
            } else {
                emptyList()
            }

            if (candidates.isEmpty() && titleQuery.isNotEmpty()) {
                candidates = LRCLibClient.searchLyrics("$titleQuery $artistQuery".trim())
            }

            val resolution = LyricMatchScorer.scoreCandidates(identity, candidates)

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                loadingCandidates.visibility = View.GONE
                displayCandidates(resolution.candidates)
            }
        }
    }

    private fun performCustomSearch(query: String) {
        loadingCandidates.visibility = View.VISIBLE
        tvEmptyCandidates.visibility = View.GONE
        candidatesContainer.removeAllViews()

        thread {
            val candidates = LRCLibClient.searchLyrics(query)
            val resolution = LyricMatchScorer.scoreCandidates(identity, candidates)

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                loadingCandidates.visibility = View.GONE
                displayCandidates(resolution.candidates)
            }
        }
    }

    private fun displayCandidates(candidates: List<ScoredCandidate>) {
        candidatesContainer.removeAllViews()
        if (candidates.isEmpty()) {
            tvEmptyCandidates.visibility = View.VISIBLE
            return
        }

        tvEmptyCandidates.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for (scored in candidates) {
            val item = scored.candidate
            val itemView = inflater.inflate(R.layout.item_track_match_candidate, candidatesContainer, false)

            val tvTitle = itemView.findViewById<TextView>(R.id.tvCandidateTitle)
            val tvArtist = itemView.findViewById<TextView>(R.id.tvCandidateArtist)
            val tvMeta = itemView.findViewById<TextView>(R.id.tvCandidateMeta)
            val tvScore = itemView.findViewById<TextView>(R.id.tvCandidateScore)
            val tvBadge = itemView.findViewById<TextView>(R.id.tvCandidateBadge)

            tvTitle.text = item.trackName
            tvArtist.text = item.artistName

            val min = (item.duration.toInt()) / 60
            val sec = (item.duration.toInt()) % 60
            val durStr = String.format(Locale.ROOT, "%d:%02d", min, sec)
            val albumStr = if (item.albumName.isNotBlank()) item.albumName else "Single"
            tvMeta.text = "$albumStr • $durStr"

            tvScore.text = "${scored.totalScore}%"

            if (scored.hasSynced) {
                tvBadge.text = "SYNCED"
                tvBadge.setTextColor(Color.parseColor("#EA3323"))
                tvScore.setTextColor(Color.parseColor("#EA3323"))
            } else {
                tvBadge.text = "PLAIN"
                tvBadge.setTextColor(Color.parseColor("#777777"))
                tvScore.setTextColor(Color.parseColor("#777777"))
            }

            TouchSpringAnimator.attach(itemView)
            itemView.setOnClickListener {
                onCandidateSelected(scored)
            }

            candidatesContainer.addView(itemView)
        }
    }

    private fun onCandidateSelected(scored: ScoredCandidate) {
        val candidate = scored.candidate
        val lyrics = candidate.syncedLyrics

        if (lyrics.isNullOrBlank()) {
            Snackbar.make(findViewById(android.R.id.content), "Selected track has no synced timestamped lyrics.", Snackbar.LENGTH_LONG)
                .applyNothingStyle()
                .show()
            return
        }

        val parsed = LrcUtils.parseLrc(lyrics)
        if (parsed.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Unable to parse LRC data.", Snackbar.LENGTH_SHORT)
                .applyNothingStyle()
                .show()
            return
        }

        repo.saveUserSelectedMatch(identity, candidate, lyrics, scored.totalScore)
        MusicPlaybackState.manualOverrideLyrics = parsed
        MusicPlaybackState.manualOverrideTrackName = candidate.trackName

        // Broadcast to LiveLyricsService to refresh notification and streaming
        val updateIntent = Intent(this, LiveLyricsService::class.java).apply {
            action = "REFRESH_MATCH"
        }
        try {
            startService(updateIntent)
        } catch (_: Exception) {}

        Snackbar.make(findViewById(android.R.id.content), "Using lyrics: ${candidate.trackName}", Snackbar.LENGTH_SHORT)
            .applyNothingStyle()
            .show()

        mainHandler.postDelayed({
            NavigationHelper.finishWithPop(this)
        }, 350)
    }

    private fun importLrcFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val reader = BufferedReader(InputStreamReader(inputStream))
            val content = reader.readText()
            reader.close()

            val parsed = LrcUtils.parseLrc(content)
            if (parsed.isEmpty()) {
                Snackbar.make(findViewById(android.R.id.content), "Selected file did not contain valid synced LRC lines.", Snackbar.LENGTH_LONG)
                    .applyNothingStyle()
                    .show()
                return
            }

            repo.saveImportedLrc(identity, content)
            MusicPlaybackState.manualOverrideLyrics = parsed
            MusicPlaybackState.manualOverrideTrackName = "${identity.title} (Imported LRC)"

            val updateIntent = Intent(this, LiveLyricsService::class.java).apply {
                action = "REFRESH_MATCH"
            }
            try {
                startService(updateIntent)
            } catch (_: Exception) {}

            Snackbar.make(findViewById(android.R.id.content), "Imported LRC successfully linked to track.", Snackbar.LENGTH_SHORT)
                .applyNothingStyle()
                .show()

            mainHandler.postDelayed({
                NavigationHelper.finishWithPop(this)
            }, 350)
        } catch (e: Exception) {
            Snackbar.make(findViewById(android.R.id.content), "Failed to import LRC file: ${e.message}", Snackbar.LENGTH_LONG)
                .applyNothingStyle()
                .show()
        }
    }
}
