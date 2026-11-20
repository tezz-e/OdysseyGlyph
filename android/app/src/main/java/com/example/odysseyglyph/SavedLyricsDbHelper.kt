package com.example.odysseyglyph

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SavedLyricsDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TRACK_KEY TEXT UNIQUE NOT NULL,
                $COL_TRACK_TITLE TEXT NOT NULL,
                $COL_ARTIST_NAME TEXT NOT NULL,
                $COL_ALBUM_NAME TEXT,
                $COL_DURATION_MS INTEGER DEFAULT 0,
                $COL_SYNCED_LYRICS TEXT NOT NULL,
                $COL_MATCH_TYPE INTEGER NOT NULL,
                $COL_MATCHED_TRACK_NAME TEXT,
                $COL_MATCHED_ARTIST_NAME TEXT,
                $COL_MATCHED_ALBUM_NAME TEXT,
                $COL_CONFIDENCE_SCORE INTEGER DEFAULT 0,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_track_key ON $TABLE_NAME ($COL_TRACK_KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "glyph_saved_lyrics.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "saved_lyrics"
        const val COL_ID = "id"
        const val COL_TRACK_KEY = "track_key"
        const val COL_TRACK_TITLE = "track_title"
        const val COL_ARTIST_NAME = "artist_name"
        const val COL_ALBUM_NAME = "album_name"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_SYNCED_LYRICS = "synced_lyrics"
        const val COL_MATCH_TYPE = "match_type"
        const val COL_MATCHED_TRACK_NAME = "matched_track_name"
        const val COL_MATCHED_ARTIST_NAME = "matched_artist_name"
        const val COL_MATCHED_ALBUM_NAME = "matched_album_name"
        const val COL_CONFIDENCE_SCORE = "confidence_score"
        const val COL_TIMESTAMP = "timestamp"

        const val MATCH_TYPE_USER_SELECTED = 1
        const val MATCH_TYPE_IMPORTED_LRC = 2
        const val MATCH_TYPE_AUTO_VERIFIED = 3
    }
}
