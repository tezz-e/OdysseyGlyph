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

    /**
     * Resolves the best candidate using user-first seamless matching:
     * 1. Hard Gate: Disqualifies candidates from unrelated artists (eliminates false token collisions like Emis Killa vs Karan Aujla).
     * 2. Heavy Lifting Auto-Sync: If candidate matches clean title and artist, auto-syncs immediately without friction.
     * 3. Same-Song Consensus: Multiple releases/submissions of the same song (e.g. Single vs Album) do NOT compete or cause ambiguity.
     * 4. Ambiguity Detection: Only flags AMBIGUOUS if there are genuinely distinct, competing songs/versions within close probability.
     * 5. No Match: If no usable synced lyrics exist on LRCLib, returns NO_MATCH to cleanly run the fallback visualizer.
     */
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

        val eligibleSynced = scored.filter { it.hasSynced && it.totalScore >= 45 }
        if (eligibleSynced.isEmpty()) {
            return LyricResolutionResult(LyricConfidenceLevel.NO_MATCH, null, scored)
        }

        val top = eligibleSynced.first()

        // Find competing candidates that are GENUINELY DIFFERENT songs/versions.
        // Multiple entries for the same song (album vs single) are treated as consensus, not competition.
        val distinctRivals = eligibleSynced.drop(1).filter { rival ->
            !isSameSong(top.candidate, rival.candidate)
        }
        val topRival = distinctRivals.firstOrNull()

        // Close possibility / ambiguity detection:
        // A rival causes ambiguity only if it is a genuinely different track/version that is within 12 points of the top match,
        // and neither has a decisive duration match to break the tie.
        val isCloseTie = if (topRival != null) {
            val scoreDiff = top.totalScore - topRival.totalScore
            if (scoreDiff <= 12) {
                // If top has an exact duration match (<= 4s) and rival does not, top wins decisively
                val topDurationDecisive = top.durationDeltaSec != null && top.durationDeltaSec <= 4.0
                val rivalDurationMismatch = topRival.durationDeltaSec == null || topRival.durationDeltaSec > 10.0
                !(topDurationDecisive && rivalDurationMismatch)
            } else {
                false
            }
        } else {
            false
        }

        val status = when {
            isCloseTie -> LyricConfidenceLevel.AMBIGUOUS
            top.totalScore >= 65 -> LyricConfidenceLevel.HIGH_CONFIDENCE
            top.totalScore >= 45 -> LyricConfidenceLevel.AMBIGUOUS
            else -> LyricConfidenceLevel.NO_MATCH
        }

        return LyricResolutionResult(status, top, scored)
    }

    private fun scoreSingle(identity: MediaTrackIdentity, candidate: LrcTrack): ScoredCandidate {
        val hasSynced = !candidate.syncedLyrics.isNullOrBlank()

        val normTrackTitle = MediaTrackIdentity.normalize(identity.title)
        val normCleanTitle = MediaTrackIdentity.normalize(identity.cleanTitle())
        val candCleanTitle = MediaTrackIdentity.cleanTitle(candidate.trackName)
        val normCandCleanTitle = MediaTrackIdentity.normalize(candCleanTitle)
        val normCandTitle = MediaTrackIdentity.normalize(candidate.trackName)

        val normArtist = MediaTrackIdentity.normalize(identity.artist)
        val normPrimaryArtist = MediaTrackIdentity.normalize(identity.primaryArtist())
        val candPrimaryArtist = MediaTrackIdentity.primaryArtist(candidate.artistName)
        val normCandPrimary = MediaTrackIdentity.normalize(candPrimaryArtist)
        val normCandArtist = MediaTrackIdentity.normalize(candidate.artistName)

        // 1. Hard Gate: Artist Correlation
        // Disqualify unrelated artists immediately (fixes Karan Aujla vs Emis Killa false collision)
        val artistMatches = normCandArtist == normArtist ||
                normCandPrimary == normPrimaryArtist ||
                (normCandArtist.contains(normPrimaryArtist) && normPrimaryArtist.isNotEmpty()) ||
                (normPrimaryArtist.contains(normCandPrimary) && normCandPrimary.isNotEmpty()) ||
                similarityRatio(normCandArtist, normArtist) >= 0.55 ||
                similarityRatio(normCandPrimary, normPrimaryArtist) >= 0.55

        if (!artistMatches) {
            return ScoredCandidate(
                candidate = candidate,
                totalScore = 0,
                titleScore = 0,
                artistScore = 0,
                albumScore = 0,
                durationDeltaSec = null,
                hasSynced = hasSynced
            )
        }

        // Artist Score (0 to 30)
        val artistScore = when {
            normCandArtist == normArtist || normCandPrimary == normPrimaryArtist -> 30
            normCandArtist.contains(normPrimaryArtist) || normPrimaryArtist.contains(normCandPrimary) -> 26
            else -> (similarityRatio(normCandPrimary, normPrimaryArtist) * 30.0).toInt().coerceIn(15, 30)
        }

        // 2. Title Score (0 to 50)
        val isExactTitle = normCleanTitle == normCandCleanTitle || normTrackTitle == normCandTitle
        val titleSimClean = similarityRatio(normCleanTitle, normCandCleanTitle)
        val titleSimRaw = similarityRatio(normTrackTitle, normCandTitle)
        val bestTitleSim = max(titleSimClean, titleSimRaw)

        val titleScore = when {
            isExactTitle -> 50
            normCandCleanTitle.contains(normCleanTitle) || normCleanTitle.contains(normCandCleanTitle) -> 42
            else -> (bestTitleSim * 50.0).toInt().coerceIn(0, 50)
        }

        // 3. Duration Score / Confirmation (-30 to +15)
        var durationDeltaSec: Double? = null
        var durationMod = 10 // Default neutral bonus when duration is unavailable from player
        if (identity.durationMs > 0 && candidate.duration > 0) {
            val trackSec = identity.durationMs / 1000.0
            val delta = abs(trackSec - candidate.duration)
            durationDeltaSec = delta
            durationMod = when {
                delta <= 4.0 -> 15  // Near exact duration
                delta <= 10.0 -> 10 // Very close duration
                delta <= 20.0 -> 0  // Acceptable delta
                delta <= 35.0 -> -15 // Discrepancy
                else -> -30         // Major mismatch (e.g. 5m vs 2m)
            }
        }

        // 4. Album Score (0 to 5)
        var albumScore = 0
        if (identity.album.isNotBlank() && candidate.albumName.isNotBlank()) {
            val normAlbum = MediaTrackIdentity.normalize(identity.album)
            val normCandAlbum = MediaTrackIdentity.normalize(candidate.albumName)
            if (normAlbum == normCandAlbum) {
                albumScore = 5
            } else if (normAlbum.contains(normCandAlbum) || normCandAlbum.contains(normAlbum)) {
                albumScore = 3
            }
        }

        var total = titleScore + artistScore + durationMod + albumScore
        if (!hasSynced) {
            total = min(total, 30)
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
     * Determines whether two candidates represent the same core song/composition
     * (e.g. single release vs album release vs remastered release).
     */
    fun isSameSong(a: LrcTrack, b: LrcTrack): Boolean {
        val cleanA = MediaTrackIdentity.normalize(MediaTrackIdentity.cleanTitle(a.trackName))
        val cleanB = MediaTrackIdentity.normalize(MediaTrackIdentity.cleanTitle(b.trackName))
        if (cleanA != cleanB) return false

        val artistA = MediaTrackIdentity.normalize(MediaTrackIdentity.primaryArtist(a.artistName))
        val artistB = MediaTrackIdentity.normalize(MediaTrackIdentity.primaryArtist(b.artistName))
        return artistA == artistB || artistA.contains(artistB) || artistB.contains(artistA)
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
