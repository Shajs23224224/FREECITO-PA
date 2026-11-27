package com.omega.ai.persistence

/** SQL shared by fresh creation and the explicit, additive v1 -> v2 migration. */
internal object PersistenceSchema {
    const val VERSION = 2
    const val RECALL_VERSION = "ALTER TABLE recall ADD COLUMN record_schema INTEGER NOT NULL DEFAULT 1"
    const val RECALL_GLOBAL_INDEX = "CREATE INDEX IF NOT EXISTS recall_global_recent ON recall(at_ms DESC)"
    const val SESSION_VERSION = "ALTER TABLE sessions ADD COLUMN record_schema INTEGER NOT NULL DEFAULT 1"
    const val SESSION_RECENT_INDEX = "CREATE INDEX IF NOT EXISTS sessions_recent ON sessions(started_ms DESC)"
    fun upgrade(oldVersion:Int,newVersion:Int,steps:List<String>,exec:(String)->Unit) {
        require(oldVersion==1 && newVersion==VERSION) { "Unsupported database migration $oldVersion -> $newVersion" }
        steps.forEach(exec)
    }
}
