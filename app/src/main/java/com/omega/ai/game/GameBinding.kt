package com.omega.ai.game

import android.app.Activity
import com.omega.vision.VisionSnapshot
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
    /** Package of a specifically authorized private test target; null disables touch transport. */
    val accessibilityTestPackage:String? get()=null
    fun accessibilityTestScopeConfirmed():Boolean=false
    fun launch(activity: Activity)
    /** True only while the permitted game content is confirmed within capture scope. */
    fun captureScopeConfirmed(): Boolean
}
object GameBinding {
    @Volatile internal var training:FreeFireTrainingAdapter?=null
        private set
    val adapter:AuthorizedGameAdapter? get()=training
    internal fun beginTraining(){training?.stop();training=FreeFireTrainingAdapter()}
    internal fun endTraining(){training?.stop();training=null}
    internal fun ingest(snapshot:VisionSnapshot){training?.ingest(snapshot)}
    fun connection():Connection = runCatching {
        val a=adapter
        SessionGate.connection(a?.memoryScope,a?.commandPort?.scope)
    }.getOrDefault(Connection.INVALID)
    fun canCapture():Boolean = runCatching {
        SessionGate.captureAllowed(connection(),adapter?.captureScopeConfirmed()==true)
    }.getOrDefault(false)
    fun accessibilityTarget():String? = runCatching {
        val a=adapter ?: return null
        a.accessibilityTestPackage?.takeIf{it.isNotBlank() && a.accessibilityTestScopeConfirmed() && canCapture()}
    }.getOrNull()
    fun canCommand(enabled:Boolean,visualNs:Long?,nowNs:Long):Boolean = runCatching {
        SessionGate.commandAllowed(connection(),adapter?.accessibilityTestScopeConfirmed()==true,enabled,visualNs,nowNs)
    }.getOrDefault(false)
}
