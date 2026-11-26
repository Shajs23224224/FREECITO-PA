package com.omega.ai.game

import android.app.Activity

/** Must be implemented using an explicitly permitted game integration. A package name alone is insufficient. */
interface AuthorizedGameAdapter {
    val title: String
    fun launch(activity: Activity)
    /** True only while the permitted game content is confirmed within capture scope. */
    fun captureScopeConfirmed(): Boolean
}
object GameBinding {
    // No game or authorization was supplied. Deliberately fail closed; phase 7 installs real adapter.
    val adapter: AuthorizedGameAdapter? = null
    fun canCapture(): Boolean = runCatching { adapter?.captureScopeConfirmed() == true }.getOrDefault(false)
}
