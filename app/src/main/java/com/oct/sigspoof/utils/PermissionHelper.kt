package com.oct.sigspoof.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object PermissionHelper {

    /**
     * Opens the appropriate settings screen for granting "Get Installed Apps" / "Read App List" permission.
     * Tries manufacturer-specific intents first, then falls back to standard app info.
     */
    fun openAppListPermission(context: Context) {
        val packageName = context.packageName
        
        when {
            isMiui() -> openMiuiPermission(context, packageName)
            else -> openDefaultPermission(context, packageName)
        }
    }
    
    private fun isMiui(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
               Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
               getSystemProperty("ro.miui.ui.version.name")?.isNotBlank() == true
    }
    
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            process.inputStream.bufferedReader().readLine()?.trim()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun openMiuiPermission(context: Context, packageName: String) {
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", packageName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback if MIUI intent fails
            openDefaultPermission(context, packageName)
        }
    }
    
    private fun openDefaultPermission(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            context.startActivity(intent)
            
            Toast.makeText(
                context,
                "Please find 'Get Installed Apps' or 'Read App List' in Permissions and enable it.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open settings. Please manually grant the permission.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
