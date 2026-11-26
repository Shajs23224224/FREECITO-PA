package com.omega.ai.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.omega.core.world.MemoryKind
import com.omega.core.world.MemoryStore
import com.omega.core.world.Recall

/** Private, versioned SQLite store; all eight kinds use identical durable transaction semantics. */
class CognitiveStore(context:Context):SQLiteOpenHelper(context.applicationContext,"omega_cognitive.db",null,1),MemoryStore {
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE recall (scope TEXT NOT NULL, kind TEXT NOT NULL, record_key TEXT NOT NULL, value TEXT NOT NULL, confidence REAL NOT NULL CHECK(confidence BETWEEN 0 AND 1), at_ms INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(scope,kind,record_key))")
        db.execSQL("CREATE INDEX recall_recent ON recall(scope,kind,at_ms DESC)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("Missing cognitive schema migration")}
    @Synchronized override fun putBatch(scope:String,records:List<Recall>){
        require(scope.length in 1..200 && records.size<=128)
        val db=writableDatabase
        db.beginTransaction()
        try {
            for(r in records){
                val previous=db.rawQuery("SELECT at_ms FROM recall WHERE scope=? AND kind=? AND record_key=?",arrayOf(scope,r.kind.name,r.key)).use{if(it.moveToFirst())it.getLong(0) else -1L}
                if(previous>r.atMs)continue
                val values=ContentValues().apply{
                    put("scope",scope);put("kind",r.kind.name);put("record_key",r.key);put("value",r.value)
                    put("confidence",r.confidence);put("at_ms",r.atMs);put("source",r.source)
                }
                check(db.insertWithOnConflict("recall",null,values,SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
            }
            for(kind in records.map{it.kind}.distinct()){
                db.execSQL("DELETE FROM recall WHERE scope=? AND kind=? AND rowid NOT IN (SELECT rowid FROM recall WHERE scope=? AND kind=? ORDER BY at_ms DESC,rowid DESC LIMIT 256)",arrayOf(scope,kind.name,scope,kind.name))
            }
            db.execSQL("DELETE FROM recall WHERE rowid NOT IN (SELECT rowid FROM recall ORDER BY at_ms DESC,rowid DESC LIMIT 4096)")
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
    @Synchronized override fun recent(scope:String,kind:MemoryKind,limit:Int):List<Recall>{
        require(scope.length in 1..200 && limit in 1..256)
        return readableDatabase.rawQuery("SELECT record_key,value,confidence,at_ms,source FROM recall WHERE scope=? AND kind=? ORDER BY at_ms DESC,rowid DESC LIMIT ?",arrayOf(scope,kind.name,limit.toString())).use{c->
            buildList{while(c.moveToNext())add(Recall(kind,c.getString(0),c.getString(1),c.getFloat(2),c.getLong(3),c.getString(4)))}
        }
    }
    @Synchronized override fun close(){super.close()}
}
