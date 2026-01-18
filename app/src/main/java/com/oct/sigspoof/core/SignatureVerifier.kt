package com.oct.sigspoof.core

import com.android.apksig.ApkVerifier
import com.oct.sigspoof.BuildConfig
import com.oct.sigspoof.SigningCert
import com.oct.sigspoof.utils.Log
import java.io.File
import java.security.MessageDigest

object SignatureVerifier {

    /**
     * Verify that the APK at the given path has a valid signature matching our expected certificate.
     */
    fun verifyAppSignature(apkPath: String): Boolean {
        return try {
            val verifier = ApkVerifier.Builder(File(apkPath))
                .setMinCheckedPlatformVersion(24)
                .build()
            val result = verifier.verify()
            
            if (!result.isVerified) {
                Log.e("APK signature verification failed")
                return false
            }
            
            val mainCert = result.signerCertificates.firstOrNull()
            if (mainCert == null) {
                Log.e("No signer certificate found")
                return false
            }
            
            val isValid = mainCert.encoded.contentEquals(SigningCert.CERT_BYTES)
            if (!isValid) {
                Log.e("Certificate mismatch - REJECTED")
            }
            isValid
        } catch (e: Throwable) {
            Log.e("Verification error: ${e.message}", e)
            false
        }
    }
    
    private fun sha256(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "error"
        }
    }
}
