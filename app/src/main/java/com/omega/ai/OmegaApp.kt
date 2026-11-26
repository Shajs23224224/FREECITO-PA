package com.omega.ai
import android.app.Application
class OmegaApp : Application() {
    override fun onCreate() { super.onCreate(); RuntimeSession.initialize(this) }
}
