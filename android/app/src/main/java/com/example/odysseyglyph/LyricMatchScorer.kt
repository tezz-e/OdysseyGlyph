package com.example.odysseyglyph

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class LyricConfidenceLevel {
    HIGH_CONFIDENCE,
    AMBIGUOUS,
    NO_MATCH
}

data class ScoredCandidate(
    val candidate: LrcTrack,
    val totalScore: Int,
    val titleScore: Int,
    val artistScore: Int,
    val albumScore: Int,
    val durationDeltaSec: Double?,
    val hasSynced: Boolean
)

data class LyricResolutionResult(
    val status: LyricConfidenceLevel,
    val topMatch: ScoredCandidate?,
    val candidates: List<ScoredCandidate>
)

object LyricMatchScorer {

    fun scoreCandidates(
        identity: MediaTrackIdentity,
        candidates: List<LrcTrack>
    ): LyricResolutionResult {
        if (candidates.isEmpty()) {
            return LyricResolutionResult(LyricConfidenceLevel.NO_MATCH, null, emptyList())
        }

        val scored = candidates.map { candidate ->
            scoreSingle(identity, candidate)
        }.sortedByDescending { it.totalScore }

        val eligibleSynced = scored.filter { it.hasSynced }
        if (eligibleSynced.isEmpty()) {
            return LyricResolutionResult(LyricConfidenceLevel.NO_MATCH, null, scored)
        }

        val top = eligibleSynced.first()
        val runnerUp = eligibleSynced.getOrNull(1)

        val durationOk = top.durationDeltaSec == null || top.durationDeltaSec <= 15.0
        val artistOk = top.artistScore >= 20

        val isHighConfidence = when {
            top.totalScore >= 85 && artistOk && durationOk -> true
            top.totalScore >= 75 && artistOk && durationOk && (runnerUp == null || (top.totalScore - runnerUp.totalScore) >= 14) -> true
            else -> false
        }

        val status = when {
            isHighConfidence -> LyricConfidenceLevel.HIGH_CONFIDENCE
            top.totalScore >= 40 -> LyricConfidenceLevel.AMBIGUOUS
            else -> LyricConfidenceLevel.NO_MATCH
        }

        return LyricResolutionResult(status, top, scored)
    }

    private fun scoreSingle(identity: MediaTrackIdentity, candidate: LrcTrack): ScoredCandidate {
        val hasSynced = !candidate.syncedLyrics.isNullOrBlank()

        // 1. Title Similarity (0 to 40 pts)
        val normTrackTitle = MediaTrackIdentity.normalize(identity.title)
        val normCleanTitle = MediaTrackIdentity.normalize(identity.cleanTitle())
        val normCandTitle = MediaTrackIdentity.normalize(candidate.trackName)

        val titleSimExact = similarityRatio(normTrackTitle, normCandTitle)
        val titleSimClean = similarityRatio(normCleanTitle, normCandTitle)
        val bestTitleSim = max(titleSimExact, titleSimClean)
        val titleScore = (bestTitleSim * 40.0).toInt().coerceIn(0, 40)

        // 2. Artist Similarity (0 to 35 pts)
        val normArtist = MediaTrackIdentity.normalize(identity.artist)
        val normPrimaryArtist = MediaTrackIdentity.normalize(identity.primaryArtist())
        val normCandArtist = MediaTrackIdentity.normalize(candidate.artistName)

        val artistSimExact = similarityRatio(normArtist, normCandArtist)
        val artistSimPrimary = similarityRatio(normPrimaryArtist, normCandArtist)
        val bestArtistSim = max(artistSimExact, artistSimPrimary)

        val artistScore = when {
            normCandArtist == normArtist || normCandArtist == normPrimaryArtist -> 35
            normCandArtist.contains(normPrimaryArtist) && normPrimaryArtist.isNotEmpty() -> 30
            normPrimaryArtist.contains(normCandArtist) && normCandArtist.isNotEmpty() -> 25
            else -> (bestArtistSim * 35.0).toInt().coerceIn(0, 35)
        }

        // 3. Album Agreement (0 to 10 pts)
        var albumScore = 0
        if (identity.album.isNotBlank() && candidate.albumName.isNotBlank()) {
            val normAlbum = MediaTrackIdentity.normalize(identity.album)
            val normCandAlbum = MediaTrackIdentity.normalize(candidate.albumName)
            if (normAlbum == normCandAlbum) {
                albumScore = 10
            } else if (normAlbum.contains(normCandAlbum) || normCandAlbum.contains(normAlbum)) {
                albumScore = 6
            }
        }

        // 4. Duration Difference (bonus or penalty)
        var durationDeltaSec: Double? = null
        var durationScoreMod = 0
        if (identity.durationMs > 0 && candidate.duration > 0) {
            val trackSec = identity.durationMs / 1000.0
            val delta = abs(trackSec - candidate.duration)
            durationDeltaSec = delta
            when {
                delta <= 3.0 -> durationScoreMod = 15
                delta <= 7.0 -> durationScoreMod = 10
                delta <= 14.0 -> durationScoreMod = 0
                delta <= 25.0 -> durationScoreMod = -15
                else -> durationScoreMod = -40 // Severe penalty for duration mismatch
            }
        }

        var total = titleScore + artistScore + albumScore + durationScoreMod
        if (!hasSynced) {
            total = min(total, 35) // Plain lyrics can't exceed ambiguous threshold
        }
        val clampedTotal = total.coerceIn(0, 100)

        return ScoredCandidate(
            candidate = candidate,
            totalScore = clampedTotal,
            titleScore = titleScore,
            artistScore = artistScore,
            albumScore = albumScore,
            durationDeltaSec = durationDeltaSec,
            hasSynced = hasSynced
        )
    }

    /**
     * Bigram/Dice coefficient similarity ratio between 0.0 and 1.0.
     */
    fun similarityRatio(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        if (s1.length < 2 || s2.length < 2) {
            return if (s1.contains(s2) || s2.contains(s1)) 0.7 else 0.0
        }

        val bigrams1 = mutableMapOf<String, Int>()
        for (i in 0 until s1.length - 1) {
            val bg = s1.substring(i, i + 2)
            bigrams1[bg] = bigrams1.getOrDefault(bg, 0) + 1
        }

        var intersection = 0
        for (i in 0 until s2.length - 1) {
            val bg = s2.substring(i, i + 2)
            val count = bigrams1.getOrDefault(bg, 0)
            if (count > 0) {
                bigrams1[bg] = count - 1
                intersection++
            }
        }

        val totalBigrams = (s1.length - 1) + (s2.length - 1)
        return (2.0 * intersection) / totalBigrams
    }
}
