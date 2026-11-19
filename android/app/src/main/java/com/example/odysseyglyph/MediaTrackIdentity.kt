package com.example.odysseyglyph

import java.util.Locale

data class MediaTrackIdentity(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val packageName: String = "",
    val mediaId: String = ""
) {
    fun isEmpty(): Boolean = title.isBlank() && artist.isBlank()

    /**
     * Normalized key used for caching and database lookups.
     * Stored as "normalized_artist::normalized_title".
     */
    fun normalizedKey(): String {
        val normArtist = normalize(primaryArtist())
        val normTitle = normalize(cleanTitle())
        return "$normArtist::$normTitle"
    }

    /**
     * Extracts the primary artist name before features/collaborations.
     * E.g. "Karan Aujla, Azaad 4L" -> "Karan Aujla"
     * "Drake feat. 21 Savage" -> "Drake"
     */
    fun primaryArtist(): String = primaryArtist(artist)

    /**
     * Removes parentheticals, track numbers, and audio tags while preserving title core.
     */
    fun cleanTitle(): String = cleanTitle(title)

    companion object {
        fun normalize(text: String): String {
            return text.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun primaryArtist(artist: String): String {
            if (artist.isBlank()) return ""
            val delimiterRegex = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|with|&|,|/|x)\\s+.*$")
            return artist.replace(delimiterRegex, "").trim()
        }

        fun cleanTitle(title: String): String {
            if (title.isBlank()) return ""
            var cleaned = title
            val dotIndex = cleaned.lastIndexOf('.')
            if (dotIndex > 0 && cleaned.substring(dotIndex).length <= 4) {
                cleaned = cleaned.substring(0, dotIndex)
            }
            cleaned = cleaned.replace(Regex("^\\d+\\s*[-.]?\\s*"), "")
            cleaned = cleaned.replace("_", " ")
            cleaned = cleaned.replace(Regex("(?i)\\s*[\\[(](?:official|audio|video|remastered|deluxe|bonus|edit|explicit|clean).*?[\\])]"), "")
            cleaned = cleaned.replace(Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring).*$"), "")
            return cleaned.replace(Regex("\\s+"), " ").trim()
        }
    }
}
