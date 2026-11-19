package com.example.odysseyglyph

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import java.util.concurrent.Executors

data class SavedLyricMatch(
    val id: Long,
    val trackKey: String,
    val trackTitle: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Long,
    val syncedLyrics: String,
    val matchType: Int, // 1=USER_SELECTED, 2=IMPORTED_LRC, 3=AUTO_VERIFIED
    val matchedTrackName: String,
    val matchedArtistName: String,
    val matchedAlbumName: String,
    val confidenceScore: Int,
    val timestamp: Long
) {
    val isUserChoice: Boolean get() = matchType == SavedLyricsDbHelper.MATCH_TYPE_USER_SELECTED || matchType == SavedLyricsDbHelper.MATCH_TYPE_IMPORTED_LRC
}

class SavedLyricsRepository private constructor(context: Context) {

    private val dbHelper = SavedLyricsDbHelper(context.applicationContext)
    private val executor = Executors.newSingleThreadExecutor()

    fun findMatch(identity: MediaTrackIdentity): SavedLyricMatch? {
        val key = identity.normalizedKey()
        if (key.isBlank() || key == "::") return null

        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            SavedLyricsDbHelper.TABLE_NAME,
            null,
            "${SavedLyricsDbHelper.COL_TRACK_KEY} = ?",
            arrayOf(key),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                return parseCursor(it)
            }
        }
        return null
    }

    fun saveUserSelectedMatch(
        identity: MediaTrackIdentity,
        candidate: LrcTrack,
        lyrics: String,
        score: Int
    ) {
        saveRecord(
            trackKey = identity.normalizedKey(),
            trackTitle = identity.title,
            artistName = identity.artist,
            albumName = identity.album,
            durationMs = identity.durationMs,
            syncedLyrics = lyrics,
            matchType = SavedLyricsDbHelper.MATCH_TYPE_USER_SELECTED,
            matchedTrackName = candidate.trackName,
            matchedArtistName = candidate.artistName,
            matchedAlbumName = candidate.albumName,
            confidenceScore = score
        )
    }

    fun saveImportedLrc(identity: MediaTrackIdentity, lrcContent: String) {
        saveRecord(
            trackKey = identity.normalizedKey(),
            trackTitle = identity.title,
            artistName = identity.artist,
            albumName = identity.album,
            durationMs = identity.durationMs,
            syncedLyrics = lrcContent,
            matchType = SavedLyricsDbHelper.MATCH_TYPE_IMPORTED_LRC,
            matchedTrackName = identity.title,
            matchedArtistName = identity.artist,
            matchedAlbumName = identity.album,
            confidenceScore = 100
        )
    }

    fun saveAutoVerifiedMatch(
        identity: MediaTrackIdentity,
        candidate: LrcTrack,
        lyrics: String,
        score: Int
    ) {
        val existing = findMatch(identity)
        // Never overwrite user-chosen or imported lyrics with an automatic match
        if (existing != null && existing.isUserChoice) {
            return
        }

        saveRecord(
            trackKey = identity.normalizedKey(),
            trackTitle = identity.title,
            artistName = identity.artist,
            albumName = identity.album,
            durationMs = identity.durationMs,
            syncedLyrics = lyrics,
            matchType = SavedLyricsDbHelper.MATCH_TYPE_AUTO_VERIFIED,
            matchedTrackName = candidate.trackName,
            matchedArtistName = candidate.artistName,
            matchedAlbumName = candidate.albumName,
            confidenceScore = score
        )
    }

    private fun saveRecord(
        trackKey: String,
        trackTitle: String,
        artistName: String,
        albumName: String,
        durationMs: Long,
        syncedLyrics: String,
        matchType: Int,
        matchedTrackName: String,
        matchedArtistName: String,
        matchedAlbumName: String,
        confidenceScore: Int
    ) {
        val values = ContentValues().apply {
            put(SavedLyricsDbHelper.COL_TRACK_KEY, trackKey)
            put(SavedLyricsDbHelper.COL_TRACK_TITLE, trackTitle)
            put(SavedLyricsDbHelper.COL_ARTIST_NAME, artistName)
            put(SavedLyricsDbHelper.COL_ALBUM_NAME, albumName)
            put(SavedLyricsDbHelper.COL_DURATION_MS, durationMs)
            put(SavedLyricsDbHelper.COL_SYNCED_LYRICS, syncedLyrics)
            put(SavedLyricsDbHelper.COL_MATCH_TYPE, matchType)
            put(SavedLyricsDbHelper.COL_MATCHED_TRACK_NAME, matchedTrackName)
            put(SavedLyricsDbHelper.COL_MATCHED_ARTIST_NAME, matchedArtistName)
            put(SavedLyricsDbHelper.COL_MATCHED_ALBUM_NAME, matchedAlbumName)
            put(SavedLyricsDbHelper.COL_CONFIDENCE_SCORE, confidenceScore)
            put(SavedLyricsDbHelper.COL_TIMESTAMP, System.currentTimeMillis())
        }

        executor.execute {
            try {
                val db = dbHelper.writableDatabase
                db.insertWithOnConflict(
                    SavedLyricsDbHelper.TABLE_NAME,
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun forgetMatch(trackKey: String): Boolean {
        return try {
            val db = dbHelper.writableDatabase
            val rows = db.delete(
                SavedLyricsDbHelper.TABLE_NAME,
                "${SavedLyricsDbHelper.COL_TRACK_KEY} = ?",
                arrayOf(trackKey)
            )
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAllMatches(): List<SavedLyricMatch> {
        val list = mutableListOf<SavedLyricMatch>()
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                SavedLyricsDbHelper.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                "${SavedLyricsDbHelper.COL_TIMESTAMP} DESC"
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(parseCursor(it))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseCursor(cursor: Cursor): SavedLyricMatch {
        return SavedLyricMatch(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_ID)),
            trackKey = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_TRACK_KEY)),
            trackTitle = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_TRACK_TITLE)),
            artistName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_ARTIST_NAME)),
            albumName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_ALBUM_NAME)) ?: "",
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_DURATION_MS)),
            syncedLyrics = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_SYNCED_LYRICS)),
            matchType = cursor.getInt(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_MATCH_TYPE)),
            matchedTrackName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_MATCHED_TRACK_NAME)) ?: "",
            matchedArtistName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_MATCHED_ARTIST_NAME)) ?: "",
            matchedAlbumName = cursor.getString(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_MATCHED_ALBUM_NAME)) ?: "",
            confidenceScore = cursor.getInt(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_CONFIDENCE_SCORE)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(SavedLyricsDbHelper.COL_TIMESTAMP))
        )
    }

    companion object {
        @Volatile
        private var instance: SavedLyricsRepository? = null

        fun getInstance(context: Context): SavedLyricsRepository {
            return instance ?: synchronized(this) {
                instance ?: SavedLyricsRepository(context).also { instance = it }
            }
        }
    }
}
