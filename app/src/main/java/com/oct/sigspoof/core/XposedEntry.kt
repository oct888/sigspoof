package com.oct.sigspoof.core

import android.content.pm.IPackageManager
import android.os.Build
import com.oct.sigspoof.common.Constants
import com.oct.sigspoof.utils.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.concurrent.thread

@Suppress("unused")
class XposedEntry : IXposedHookZygoteInit, IXposedHookLoadPackage {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // Nothing needed in zygote for now
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            Constants.APP_PACKAGE_NAME -> {
                hookAppModule(lpparam)
            }
            "android" -> {
                hookSystemServer(lpparam)
            }
        }
    }

    private fun hookAppModule(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookConstructor(
                "${Constants.APP_PACKAGE_NAME}.SigSpoofApp",
                lpparam.classLoader,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.setStaticBooleanField(
                                param.thisObject.javaClass,
                                "isHooked",
                                true
                            )
                            Log.d("Set isHooked flag in app")
                        } catch (t: Throwable) {
                            // Field might not exist, that's ok
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e("Failed to hook app class: ${t.message}")
        }
    }

    private fun hookSystemServer(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d("Hooking system_server")

        var serviceManagerHook: XC_MethodHook.Unhook? = null
        
        try {
            serviceManagerHook = XposedHelpers.findAndHookMethod(
                "android.os.ServiceManager",
                lpparam.classLoader,
                "addService",
                String::class.java,
                android.os.IBinder::class.java,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] == "package") {
                            serviceManagerHook?.unhook()
                            val pms = param.args[1] as IPackageManager
                            Log.d("Got PMS: $pms")
                            
                            thread {
                                runCatching {
                                    initializeService(pms)
                                }.onFailure {
                                    Log.e("Failed to initialize service", it)
                                }
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e("Failed to hook ServiceManager.addService", t)
        }

        // Install signature spoofing hooks
        try {
            SignatureSpooferHooks.install(lpparam.classLoader)
        } catch (t: Throwable) {
            Log.e("Failed to install signature hooks", t)
        }
    }

    private fun initializeService(pms: IPackageManager) {
        Log.i("Initializing SpooferService")
        
        SpooferService()
        
        ConfigManager.start()
        
        // Get app UID for binder injection
        val appUid = getPackageUidCompat(pms, Constants.APP_PACKAGE_NAME, 0, 0)
        if (appUid <= 0) {
            Log.w("App not installed, skipping UID observer")
            return
        }
        
        Log.d("App UID: $appUid")
        
        // Wait for activity service and register UID observer
        UserService.register(pms, appUid)
        
        Log.i("Service initialized")
    }

    private fun getPackageUidCompat(pms: IPackageManager, packageName: String, flags: Long, userId: Int): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pms.getPackageUid(packageName, flags, userId)
            } else {
                pms.getPackageUid(packageName, flags.toInt(), userId)
            }
        } catch (t: Throwable) {
            Log.e("Failed to get UID for $packageName: ${t.message}")
            -1
        }
    }
}
