package com.omega.ai.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.omega.core.EngineStats

class SessionStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "omega_sessions.db", null, PersistenceSchema.VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, started_ms INTEGER NOT NULL, ended_ms INTEGER, reason TEXT, accepted INTEGER NOT NULL DEFAULT 0, processed INTEGER NOT NULL DEFAULT 0, dropped INTEGER NOT NULL DEFAULT 0, rejected INTEGER NOT NULL DEFAULT 0, record_schema INTEGER NOT NULL DEFAULT 2)")
        db.execSQL(PersistenceSchema.SESSION_RECENT_INDEX)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        PersistenceSchema.upgrade(oldVersion,newVersion,listOf(PersistenceSchema.SESSION_VERSION,PersistenceSchema.SESSION_RECENT_INDEX)){db.execSQL(it)}
    }
    fun recover() { writableDatabase.execSQL("UPDATE sessions SET ended_ms=?, reason='process_interrupted' WHERE ended_ms IS NULL", arrayOf(System.currentTimeMillis())) }
    fun begin(id: String) {
        writableDatabase.insertOrThrow("sessions", null, ContentValues().apply { put("id", id); put("started_ms", System.currentTimeMillis()); put("record_schema", PersistenceSchema.VERSION) })
    }
    fun finish(id: String, reason: String, s: EngineStats) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("sessions", ContentValues().apply {
                put("ended_ms", System.currentTimeMillis()); put("reason", reason)
                put("accepted", s.accepted); put("processed", s.processed); put("dropped", s.dropped); put("rejected", s.rejected)
            }, "id=? AND ended_ms IS NULL", arrayOf(id))
            db.execSQL("DELETE FROM sessions WHERE ended_ms IS NOT NULL AND id NOT IN (SELECT id FROM sessions ORDER BY started_ms DESC LIMIT 1000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
