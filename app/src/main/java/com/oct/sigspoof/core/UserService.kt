package com.oct.sigspoof.core

import android.content.AttributionSource
import android.content.pm.IPackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.ServiceManager
import android.os.SystemClock
import com.oct.sigspoof.common.Constants
import com.oct.sigspoof.utils.Log
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.adapter.UidObserverAdapter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// Constants from ActivityManager (hidden API)
private const val UID_OBSERVER_PROCSTATE = 1
private const val UID_OBSERVER_GONE = 2
private const val UID_OBSERVER_IDLE = 4
private const val UID_OBSERVER_ACTIVE = 8
private const val UID_OBSERVER_FLAGS = UID_OBSERVER_PROCSTATE or UID_OBSERVER_GONE or UID_OBSERVER_ACTIVE

private const val PROCESS_STATE_UNKNOWN = -1
private const val PROCESS_STATE_TOP = 2  // Foreground activity

object UserService {

    @Volatile
    private var appUid = 0
    @Volatile
    private var pms: IPackageManager? = null
    @Volatile
    private var signatureVerified = false

    private val isRegistered = AtomicBoolean(false)
    private val isSending = AtomicBoolean(false)
    @Volatile
    private var lastSentTime = 0L

    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) {
            if (uid == appUid) {
                Log.d("onUidActive uid=$uid")
                sendBinderToApp()
            }
        }

        override fun onUidStateChanged(uid: Int, procState: Int, procStateSeq: Long, capability: Int) {
            if (uid != appUid) return

            // Send binder when app enters foreground (TOP state)
            if (procState == PROCESS_STATE_TOP) {
                Log.d("onUidStateChanged TOP state for uid=$uid")
                sendBinderToApp()
            }
        }

        override fun onUidGone(uid: Int, disabled: Boolean) {
            if (uid == appUid) {
                Log.d("onUidGone uid=$uid")
            }
        }
    }

    private fun sendBinderToApp() {
        val now = SystemClock.uptimeMillis()
        if (now - lastSentTime < 2000L) {
            Log.d("Binder sent recently, skipping redundant push")
            return
        }

        if (!isSending.compareAndSet(false, true)) {
            return
        }

        thread(name = "SigSpoof-BinderSender", isDaemon = true) {
            try {
                doSendBinderToApp()
            } finally {
                isSending.set(false)
            }
        }
    }

    private fun doSendBinderToApp() {
        if (!signatureVerified) {
            val verified = verifyAppSignature()
            if (!verified) {
                Log.e("App signature verification failed, refusing to send binder")
                return
            }
            signatureVerified = true
            Log.i("App signature verified")
        }

        val spooferBinder = SpooferService.instance
        if (spooferBinder == null) {
            Log.e("SpooferService.instance is null, cannot send binder")
            return
        }

        var success = false
        for (attempt in 1..5) {
            try {
                val provider = ActivityManagerApis.getContentProviderExternal(
                    Constants.PROVIDER_AUTHORITY, 0, null, null
                )
                if (provider != null) {
                    val extras = Bundle()
                    extras.putBinder("binder", spooferBinder)

                    val reply = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val attr = AttributionSource.Builder(Process.SYSTEM_UID).setPackageName("android").build()
                        provider.call(attr, Constants.PROVIDER_AUTHORITY, "", null, extras)
                    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                        provider.call("android", null, Constants.PROVIDER_AUTHORITY, "", null, extras)
                    } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        provider.call("android", Constants.PROVIDER_AUTHORITY, "", null, extras)
                    } else {
                        provider.call("android", "", null, extras)
                    }

                    if (reply != null) {
                        Log.i("Successfully sent binder to app on attempt $attempt")
                        success = true
                        lastSentTime = SystemClock.uptimeMillis()
                        break
                    } else {
                        Log.w("provider.call returned null on attempt $attempt")
                    }
                } else {
                    Log.w("getContentProviderExternal returned null on attempt $attempt")
                }
            } catch (e: Throwable) {
                Log.w("sendBinderToApp attempt $attempt failed: ${e.message}")
            }

            try {
                Thread.sleep(300)
            } catch (_: InterruptedException) {
                break
            }
        }

        try {
            ActivityManagerApis.removeContentProviderExternal(Constants.PROVIDER_AUTHORITY, null)
        } catch (_: Throwable) {
        }

        if (!success) {
            Log.e("Failed to send binder to app after retries")
            lastSentTime = 0L
        }
    }

    private fun verifyAppSignature(): Boolean {
        val currentPms = pms ?: return false
        try {
            val packageInfo = getPackageInfoCompat(currentPms, Constants.APP_PACKAGE_NAME, 0, 0)
            val apkPath = packageInfo?.applicationInfo?.sourceDir
            if (apkPath == null) {
                Log.e("Could not get APK path for verification")
                return false
            }
            return SignatureVerifier.verifyAppSignature(apkPath)
        } catch (e: Throwable) {
            Log.e("Signature verification exception: ${e.message}", e)
            return false
        }
    }

    fun register(pms: IPackageManager, appUid: Int) {
        this.pms = pms
        this.appUid = appUid
        Log.i("Registering for UID $appUid")

        if (isRegistered.compareAndSet(false, true)) {
            waitSystemService("activity")
            try {
                ActivityManagerApis.registerUidObserver(
                    uidObserver,
                    UID_OBSERVER_FLAGS,
                    PROCESS_STATE_UNKNOWN,
                    null
                )
                Log.i("Successfully registered UID observer (flags=$UID_OBSERVER_FLAGS)")
            } catch (e: Throwable) {
                Log.e("Failed to register UID observer: ${e.message}", e)
                isRegistered.set(false)
            }
        } else {
            Log.d("UID observer already registered, updated appUid to $appUid")
        }

        // Also attempt sending binder immediately in case app is already active/running
        sendBinderToApp()
    }

    private fun waitSystemService(name: String) {
        while (ServiceManager.getService(name) == null) {
            Thread.sleep(500)
        }
    }

    private fun getPackageInfoCompat(pms: IPackageManager, packageName: String, flags: Long, userId: Int): android.content.pm.PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pms.getPackageInfo(packageName, flags, userId)
            } else {
                pms.getPackageInfo(packageName, flags.toInt(), userId)
            }
        } catch (e: Throwable) {
            null
        }
    }
}
