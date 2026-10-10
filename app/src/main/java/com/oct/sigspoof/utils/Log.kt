package com.oct.sigspoof.utils

import android.util.Log
import com.oct.sigspoof.BuildConfig

object Log {
    private const val TAG = "SigSpoof"
    private var isXposed: Boolean = false

    init {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            isXposed = true
        } catch (e: ClassNotFoundException) {
            isXposed = false
        }
    }

    fun d(message: String) {
        Log.d(TAG, message)
        if (isXposed) {
            xposedLog("[$TAG][D] $message")
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
        if (isXposed) {
            xposedLog("[$TAG][I] $message")
        }
    }

    fun w(message: String) {
        Log.w(TAG, message)
        if (isXposed) {
            xposedLog("[$TAG][W] $message")
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        if (isXposed) {
            xposedLog("[$TAG][E] $message")
            throwable?.let { xposedLog(it) }
        }
    }

    private fun xposedLog(message: String) {
        try {
            de.robv.android.xposed.XposedBridge.log(message)
        } catch (_: Throwable) {
        }
    }

    private fun xposedLog(throwable: Throwable) {
        try {
            de.robv.android.xposed.XposedBridge.log(throwable)
        } catch (_: Throwable) {
        }
    }
}
