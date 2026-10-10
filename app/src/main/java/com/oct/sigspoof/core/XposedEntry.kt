package com.oct.sigspoof.core

import android.content.pm.IPackageManager
import android.os.Build
import android.os.ServiceManager
import com.oct.sigspoof.common.Constants
import com.oct.sigspoof.utils.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

@Suppress("unused")
class XposedEntry : IXposedHookZygoteInit, IXposedHookLoadPackage {

    private val systemServerHooked = AtomicBoolean(false)
    private val serviceInitialized = AtomicBoolean(false)

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // Nothing needed in zygote for now
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Constants.APP_PACKAGE_NAME) {
            hookAppModule(lpparam)
        } else if (lpparam.packageName == "android" || lpparam.packageName == "system" || lpparam.processName == "system_server") {
            hookSystemServer(lpparam)
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
        if (!systemServerHooked.compareAndSet(false, true)) {
            Log.d("system_server already hooked, skipping duplicate")
            return
        }
        Log.i("Hooking system_server (pkg=${lpparam.packageName}, process=${lpparam.processName})")

        // 1. Initialize core services immediately (independent of PMS)
        try {
            SpooferService()
            ConfigManager.start()
            Log.i("Core SpooferService and ConfigManager started")
        } catch (t: Throwable) {
            Log.e("Failed to initialize core services", t)
        }

        // 2. Install signature spoofing hooks immediately
        try {
            SignatureSpooferHooks.install(lpparam.classLoader)
            Log.i("SignatureSpooferHooks installed")
        } catch (t: Throwable) {
            Log.e("Failed to install signature hooks", t)
        }

        // 3. Resolve IPackageManager and setup UserService
        resolvePmsAndInitUserService(lpparam.classLoader)
    }

    private fun resolvePmsAndInitUserService(classLoader: ClassLoader) {
        // Fast path: Check if "package" service is already available
        try {
            val binder = ServiceManager.getService("package")
            if (binder != null) {
                val pms = IPackageManager.Stub.asInterface(binder)
                if (pms != null) {
                    Log.i("PMS already registered in ServiceManager, initializing UserService immediately")
                    startServiceInit(pms)
                    return
                }
            }
        } catch (t: Throwable) {
            Log.w("Initial getService('package') check failed: ${t.message}")
        }

        // Hook ServiceManager.addService to intercept early boot registration
        try {
            val serviceManagerClass = XposedHelpers.findClass("android.os.ServiceManager", classLoader)
            val serviceManagerHooks = mutableSetOf<XC_MethodHook.Unhook>()
            serviceManagerHooks += XposedBridge.hookAllMethods(
                serviceManagerClass,
                "addService",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args.isEmpty() || param.args[0] != "package") {
                            return
                        }
                        val pms = param.args.getOrNull(1) as? IPackageManager ?: return
                        serviceManagerHooks.forEach { it.unhook() }
                        serviceManagerHooks.clear()
                        Log.i("Captured PMS via ServiceManager.addService")
                        startServiceInit(pms)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w("Failed to hook ServiceManager.addService: ${t.message}")
        }

        // Background poller fallback: in case addService hook was missed or custom framework bypassed it
        thread(name = "SigSpoof-PmsPoller", isDaemon = true) {
            var waitedMs = 0
            while (!serviceInitialized.get() && waitedMs < 60_000) {
                try {
                    val binder = ServiceManager.getService("package")
                    if (binder != null) {
                        val pms = IPackageManager.Stub.asInterface(binder)
                        if (pms != null) {
                            Log.i("Captured PMS via background poller after ${waitedMs}ms")
                            startServiceInit(pms)
                            break
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("Error during PMS polling: ${t.message}")
                }
                Thread.sleep(300)
                waitedMs += 300
            }
        }
    }

    private fun startServiceInit(pms: IPackageManager) {
        if (!serviceInitialized.compareAndSet(false, true)) {
            return
        }
        thread(name = "SigSpoof-InitUser", isDaemon = true) {
            runCatching {
                initializeUserService(pms)
            }.onFailure {
                Log.e("Failed to initialize user service", it)
            }
        }
    }

    private fun initializeUserService(pms: IPackageManager) {
        Log.i("Resolving UID for ${Constants.APP_PACKAGE_NAME}...")

        // Wait for PMS to finish scanning packages if booting
        var appUid = -1
        var attempts = 0
        while (attempts < 60) {
            appUid = getPackageUidCompat(pms, Constants.APP_PACKAGE_NAME, 0, 0)
            if (appUid > 0) {
                break
            }
            attempts++
            Thread.sleep(1000)
        }

        if (appUid > 0) {
            Log.i("Found app UID: $appUid (after ${attempts}s), registering UserService")
            UserService.register(pms, appUid)
        } else {
            Log.w("App ${Constants.APP_PACKAGE_NAME} not installed or not scanned after 60s, will monitor in background")
            // Launch background watcher to catch app install later
            thread(name = "SigSpoof-InstallWatcher", isDaemon = true) {
                while (appUid <= 0) {
                    try {
                        Thread.sleep(10_000)
                        appUid = getPackageUidCompat(pms, Constants.APP_PACKAGE_NAME, 0, 0)
                        if (appUid > 0) {
                            Log.i("App detected installed later! UID: $appUid, registering UserService")
                            UserService.register(pms, appUid)
                            break
                        }
                    } catch (t: Throwable) {
                        Log.e("Error checking app UID", t)
                    }
                }
            }
        }
    }

    private fun getPackageUidCompat(pms: IPackageManager, packageName: String, flags: Long, userId: Int): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pms.getPackageUid(packageName, flags, userId)
            } else {
                pms.getPackageUid(packageName, flags.toInt(), userId)
            }
        } catch (t: Throwable) {
            -1
        }
    }
}
