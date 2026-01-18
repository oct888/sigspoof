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
        if (BuildConfig.DEBUG) {
            if (isXposed) {
                xposedLog("[$TAG][D] $message")
            } else {
                Log.d(TAG, message)
            }
        }
    }

    fun i(message: String) {
        if (isXposed) {
            xposedLog("[$TAG][I] $message")
        } else {
            Log.i(TAG, message)
        }
    }

    fun w(message: String) {
        if (isXposed) {
            xposedLog("[$TAG][W] $message")
        } else {
            Log.w(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isXposed) {
            xposedLog("[$TAG][E] $message")
            throwable?.let { xposedLog(it) }
        } else {
            if (throwable != null) {
                Log.e(TAG, message, throwable)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private fun xposedLog(message: String) {
        try {
            de.robv.android.xposed.XposedBridge.log(message)
        } catch (t: Throwable) {
            // Fallback if something goes wrong
            Log.i(TAG, message)
        }
    }

    private fun xposedLog(throwable: Throwable) {
        try {
            de.robv.android.xposed.XposedBridge.log(throwable)
        } catch (t: Throwable) {
            // Fallback
            Log.e(TAG, "Error", throwable)
        }
    }
}
