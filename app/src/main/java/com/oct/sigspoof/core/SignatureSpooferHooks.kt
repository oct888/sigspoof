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

    private fun findFirstLoadableClass(vararg candidates: String): Class<*>? {
        for (name in candidates) {
            try {
                return Class.forName(name)
            } catch (e: ClassNotFoundException) {
                // try next
            }
        }
        return null
    }

    private fun createSigningInfo(entry: SpoofEntry): SigningInfo? {
        // The public SigningInfo(int, List<Signature>, ArraySet, List) constructor used
        // above does not exist pre-API 35. Build the internal SigningDetails object via
        // reflection instead (same approach FakeGApps uses), then wrap it in SigningInfo.
        val fakeSignature = Signature(entry.signature.toByteArray())
        val sigs = arrayOf(fakeSignature)
        val pks = ArraySet<PublicKey>()
        entry.publicKey?.let { pks.add(it) }

        return try {
            val signingDetailsClass = findFirstLoadableClass(
                "android.content.pm.SigningDetails",
                // Android 9-12 have SigningDetails embedded in PackageParser
                "android.content.pm.PackageParser\$SigningDetails"
            ) ?: run {
                Log.e("createSigningInfo: SigningDetails class not found")
                return null
            }

            val signingDetails: Any = try {
                // Android 10-12 constructor: (Signature[], int, ArraySet, Signature[])
                val ctor = signingDetailsClass.getDeclaredConstructor(
                    Array<Signature>::class.java,
                    Int::class.javaPrimitiveType,
                    ArraySet::class.java,
                    Array<Signature>::class.java
                )
                ctor.isAccessible = true
                ctor.newInstance(sigs, SIGNING_BLOCK_V3, pks, null)
            } catch (e: NoSuchMethodException) {
                // Android 9 has an extra pastSigningCertificatesFlags argument
                val ctor = signingDetailsClass.getDeclaredConstructor(
                    Array<Signature>::class.java,
                    Int::class.javaPrimitiveType,
                    ArraySet::class.java,
                    Array<Signature>::class.java,
                    IntArray::class.java
                )
                ctor.isAccessible = true
                ctor.newInstance(sigs, SIGNING_BLOCK_V3, pks, null, null)
            }

            val signingInfoCtor = SigningInfo::class.java.getDeclaredConstructor(signingDetailsClass)
            signingInfoCtor.isAccessible = true
            signingInfoCtor.newInstance(signingDetails) as SigningInfo
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
