package com.oct.sigspoof

import android.app.Application

class SigSpoofApp : Application() {

    companion object {
        @JvmStatic
        var isHooked: Boolean = false
            internal set
    }

    override fun onCreate() {
        super.onCreate()
    }
}
