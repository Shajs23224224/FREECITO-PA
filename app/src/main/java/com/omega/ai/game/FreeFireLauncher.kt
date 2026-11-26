package com.omega.ai.game

import android.app.Activity
import android.content.Context

/** Public Android launcher only. No observation, input automation or client modification. */
object FreeFireLauncher {
    const val PACKAGE="com.dts.freefireth"
    fun available(context:Context):Boolean = runCatching {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)!=null
    }.getOrDefault(false)
    fun open(activity:Activity):Boolean = runCatching {
        val intent=activity.packageManager.getLaunchIntentForPackage(PACKAGE)?:return false
        activity.startActivity(intent)
        true
    }.getOrDefault(false)
}
