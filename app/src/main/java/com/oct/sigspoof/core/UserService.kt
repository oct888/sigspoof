package com.oct.sigspoof.core

import android.content.AttributionSource
import android.content.pm.IPackageManager
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import com.oct.sigspoof.common.Constants
import com.oct.sigspoof.utils.Log
import rikka.hidden.compat.ActivityManagerApis
import rikka.hidden.compat.adapter.UidObserverAdapter

// Constants from ActivityManager (hidden API)
// UID_OBSERVER_PROCSTATE = 1, UID_OBSERVER_ACTIVE = 2
// Use 3 to get both onUidStateChanged and onUidActive callbacks
private const val UID_OBSERVER_FLAGS = 3
private const val PROCESS_STATE_UNKNOWN = -1
private const val PROCESS_STATE_TOP = 2  // Foreground activity

object UserService {

    private var appUid = 0
    private var binderSent = false
    private var pms: IPackageManager? = null
    private var signatureVerified = false
    
    private val uidObserver = object : UidObserverAdapter() {
        override fun onUidActive(uid: Int) {
            if (uid == appUid && !binderSent) {
                sendBinderToApp()
            }
        }
        
        override fun onUidStateChanged(uid: Int, procState: Int, procStateSeq: Long, capability: Int) {
            if (uid != appUid) return
            
            // Send binder when app goes to foreground (TOP state)
            if (procState == PROCESS_STATE_TOP && !binderSent) {
                sendBinderToApp()
            }
        }
        
        override fun onUidGone(uid: Int, disabled: Boolean) {
            if (uid == appUid) {
                Log.d("onUidGone uid=$uid, resetting binderSent")
                binderSent = false
            }
        }
    }
    
    private fun sendBinderToApp() {
        if (!signatureVerified) {
            val verified = verifyAppSignature()
            if (!verified) {
                Log.e("App signature verification failed, refusing to send binder")
                return
            }
            signatureVerified = true
            Log.i("App signature verified")
        }
        
        try {
            val provider = ActivityManagerApis.getContentProviderExternal(
                Constants.PROVIDER_AUTHORITY, 0, null, null
            )
            if (provider == null) {
                Log.e("Failed to get provider")
                return
            }

            val extras = Bundle()
            extras.putBinder("binder", SpooferService.instance)
            
            val reply = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val attr = AttributionSource.Builder(1000).setPackageName("android").build()
                provider.call(attr, Constants.PROVIDER_AUTHORITY, "", null, extras)
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                provider.call("android", null, Constants.PROVIDER_AUTHORITY, "", null, extras)
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                provider.call("android", Constants.PROVIDER_AUTHORITY, "", null, extras)
            } else {
                provider.call("android", "", null, extras)
            }
            
            if (reply == null) {
                Log.e("Failed to send binder to app")
                return
            }
            binderSent = true
            Log.i("Successfully sent binder to app")
        } catch (e: Throwable) {
            Log.e("sendBinderToApp error: ${e.message}", e)
        }
    }
    
    private fun verifyAppSignature(): Boolean {
        try {
            val packageInfo = getPackageInfoCompat(pms!!, Constants.APP_PACKAGE_NAME, 0, 0)
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
        
        waitSystemService("activity")
        try {
            // Register for both process state changes and uid active
            ActivityManagerApis.registerUidObserver(
                uidObserver,
                UID_OBSERVER_FLAGS,
                PROCESS_STATE_UNKNOWN,
                null
            )
        } catch (e: Throwable) {
            Log.e("Failed to register UID observer: ${e.message}", e)
        }
    }

    private fun waitSystemService(name: String) {
        while (ServiceManager.getService(name) == null) {
            Thread.sleep(1000)
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
