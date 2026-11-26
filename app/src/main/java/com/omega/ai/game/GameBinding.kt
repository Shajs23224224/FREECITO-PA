package com.omega.ai.game

import android.app.Activity
import com.omega.core.executive.GamePort
import com.omega.core.release.Connection
import com.omega.core.release.SessionGate

/** Must be implemented using an explicitly permitted game integration. A package name alone is insufficient. */
interface AuthorizedGameAdapter {
    val title: String
    /** Stable authorized game identifier plus game version; never a user/account identifier. */
    val memoryScope: String
    /** Owner-provided authorized command port, or null for read-only integration. */
    val commandPort: GamePort? get() = null
    fun launch(activity: Activity)
    /** True only while the permitted game content is confirmed within capture scope. */
    fun captureScopeConfirmed(): Boolean
}
object GameBinding {
    // No game or authorization was supplied. Deliberately fail closed; a concrete authorized title and API are required.
    val adapter: AuthorizedGameAdapter? = null
    fun connection():Connection = runCatching {
        val a=adapter
        SessionGate.connection(a?.memoryScope,a?.commandPort?.scope)
    }.getOrDefault(Connection.INVALID)
    fun canCapture():Boolean = runCatching {
        SessionGate.captureAllowed(connection(),adapter?.captureScopeConfirmed()==true)
    }.getOrDefault(false)
    fun canCommand(enabled:Boolean,visualNs:Long?,nowNs:Long):Boolean = runCatching {
        SessionGate.commandAllowed(connection(),adapter?.captureScopeConfirmed()==true,enabled,visualNs,nowNs)
    }.getOrDefault(false)
}
