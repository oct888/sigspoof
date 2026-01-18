package com.oct.sigspoof.core

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.util.ArraySet
import com.oct.sigspoof.utils.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

private const val SIGNING_BLOCK_V3 = 3

internal object SignatureSpooferHooks {

    private val installed = AtomicBoolean(false)

    fun install(classLoader: ClassLoader?) {
        if (!installed.compareAndSet(false, true)) return
        classLoader ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e("Android P or newer required")
            return
        }
        ConfigManager.start()
        hookGeneratePackageInfo(classLoader)
    }

    private fun hookGeneratePackageInfo(classLoader: ClassLoader) {
        val hookClass = findHookClass(classLoader) ?: run {
            Log.e("Target class not found, skipping")
            return
        }
        XposedBridge.hookAllMethods(hookClass, "generatePackageInfo", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val packageInfo = param.result as? PackageInfo
                if (packageInfo == null) return
                val packageName = packageInfo.packageName
                if (packageName == null) return
                
                val entry = ConfigManager.getEntry(packageName)
                if (entry == null) return
                
                applySpoof(packageInfo, entry)
                param.result = packageInfo
            }
        })
        Log.d("Hooked ${hookClass.name}#generatePackageInfo")
    }

    private fun applySpoof(packageInfo: PackageInfo, entry: SpoofEntry) {
        val fakeSignature = Signature(entry.signature.toByteArray())
        packageInfo.signatures = arrayOf(fakeSignature)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            createSigningInfo(entry)?.let { packageInfo.signingInfo = it }
        }
    }

    private fun createSigningInfo(entry: SpoofEntry): SigningInfo? {
        val fakeSignature = Signature(entry.signature.toByteArray())
        val keys = entry.publicKey?.let { key ->
            ArraySet<PublicKey>().apply { add(key) }
        }
        return try {
            // Use XposedHelpers to call hidden constructor
            de.robv.android.xposed.XposedHelpers.newInstance(
                SigningInfo::class.java,
                SIGNING_BLOCK_V3,
                listOf(fakeSignature),
                keys,
                null
            ) as SigningInfo
        } catch (t: Throwable) {
            Log.e("Failed to create SigningInfo: ${t.message}", t)
            null
        }
    }

    private fun findHookClass(classLoader: ClassLoader): Class<*>? {
        val candidates = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                "com.android.server.pm.ComputerEngine"
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                "com.android.server.pm.PackageManagerService\$ComputerEngine",
                "com.android.server.pm.ComputerEngine"
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH -> arrayOf(
                "com.android.server.pm.PackageManagerService"
            )
            else -> arrayOf("com.android.server.PackageManagerService")
        }
        candidates.forEach { name ->
            val clazz = runCatching { Class.forName(name, false, classLoader) }.getOrNull()
            if (clazz != null) {
                return clazz
            }
        }
        return null
    }
}
