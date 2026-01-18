package com.oct.sigspoof.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.io.File
import java.security.MessageDigest

object SignatureUtils {

    fun getSignatureFromPackage(context: Context, packageName: String): String? {
        return try {
            val packageManager = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            
            val packageInfo = packageManager.getPackageInfo(packageName, flags)
            
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.firstOrNull()?.toCharsString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Helper to get formatted byte array (deprecated approach often returns hex or chars string directly)
    // The user example shows a Base64 encoded ASN.1 structure probably.
    // "MIICUjCCAbsCBEk0..." looks like Base64.
    // android.content.pm.Signature.toCharsString() returns hex string.
    // android.content.pm.Signature.toByteArray() returns raw bytes.
    // We should check what format the user expects.
    // User sample:
    // "com.google.android.youtube": "MIICUjCCAbsCBEk..."
    // This looks like Base64 encoded PEM/DER.
    // Signature.toByteArray() is the raw DER bytes.
    
    fun getSignatureString(context: Context, packageName: String): String? {
         return try {
            val packageManager = context.packageManager
            @Suppress("DEPRECATION")
            val flags = PackageManager.GET_SIGNATURES
            
            val packageInfo = packageManager.getPackageInfo(packageName, flags)
            
            @Suppress("DEPRECATION")
            val signatures = packageInfo.signatures
            
            val sig = signatures?.firstOrNull() ?: return null
            Base64.encodeToString(sig.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun sha256(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "error" }
    }

    fun getSignatureFromApk(context: Context, apkPath: String): String? {
        return try {
             val packageManager = context.packageManager
             @Suppress("DEPRECATION")
             val flags = PackageManager.GET_SIGNATURES
            
            val packageInfo = packageManager.getPackageArchiveInfo(apkPath, flags) ?: return null
            
            @Suppress("DEPRECATION")
            val signatures = packageInfo.signatures
            
            val sig = signatures?.firstOrNull() ?: return null
            Base64.encodeToString(sig.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
