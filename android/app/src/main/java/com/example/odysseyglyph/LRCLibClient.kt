package com.example.odysseyglyph

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LrcTrack(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Double,
    val instrumental: Boolean,
    val syncedLyrics: String?,
    val plainLyrics: String? = null
)

object LRCLibClient {
    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "GlyphOdyssey/2.1.0 (https://github.com/tezz-e/OdysseyGlyph)"
    private const val TIMEOUT_MS = 6000

    private fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("User-Agent", USER_AGENT)
        return conn
    }

    private fun parseTrackJson(obj: JSONObject): LrcTrack {
        val synced = if (obj.isNull("syncedLyrics")) null else obj.optString("syncedLyrics")
        val plain = if (obj.isNull("plainLyrics")) null else obj.optString("plainLyrics")
        val name = when {
            obj.has("trackName") && !obj.isNull("trackName") -> obj.optString("trackName")
            obj.has("name") && !obj.isNull("name") -> obj.optString("name")
            else -> ""
        }
        val artist = obj.optString("artistName", "")
        val album = obj.optString("albumName", "")
        val duration = obj.optDouble("duration", 0.0)
        val instrumental = obj.optBoolean("instrumental", false)

        return LrcTrack(
            id = obj.optLong("id", 0L),
            trackName = name,
            artistName = artist,
            albumName = album,
            duration = duration,
            instrumental = instrumental,
            syncedLyrics = if (synced.isNullOrBlank()) null else synced,
            plainLyrics = if (plain.isNullOrBlank()) null else plain
        )
    }

    private fun executeGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(urlString)
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                null
            }
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTrackArray(response: String): List<LrcTrack> {
        val results = mutableListOf<LrcTrack>()
        val jsonArray = JSONArray(response)
        for (i in 0 until jsonArray.length()) {
            results.add(parseTrackJson(jsonArray.getJSONObject(i)))
        }
        return results
    }

    /**
     * Exact lookup via /api/get
     */
    fun getExactLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null
    ): LrcTrack? {
        try {
            val params = StringBuilder()
            params.append("track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
            params.append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
            if (!albumName.isNullOrBlank()) {
                params.append("&album_name=").append(URLEncoder.encode(albumName, "UTF-8"))
            }
            if (durationSeconds != null && durationSeconds > 0) {
                params.append("&duration=").append(durationSeconds)
            }

            val response = executeGet("$BASE_URL/get?$params") ?: return null
            return parseTrackJson(JSONObject(response))
        } catch (e: Exception) {
            // Silently proceed to candidate search on failure / 404
        }
        return null
    }

    /**
     * Candidate search with track_name and artist_name parameters.
     */
    fun searchCandidates(trackName: String, artistName: String): List<LrcTrack> {
        try {
            val params = StringBuilder()
            params.append("track_name=").append(URLEncoder.encode(trackName, "UTF-8"))
            if (artistName.isNotBlank()) {
                params.append("&artist_name=").append(URLEncoder.encode(artistName, "UTF-8"))
            }

            val response = executeGet("$BASE_URL/search?$params") ?: return emptyList()
            return parseTrackArray(response)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    /**
     * General freeform search query (q=...).
     */
    fun searchLyrics(query: String): List<LrcTrack> {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val response = executeGet("$BASE_URL/search?q=$encodedQuery") ?: return emptyList()
            return parseTrackArray(response)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
}
