package com.oct.sigspoof.core

import android.os.RemoteException
import android.system.Os
import android.system.OsConstants
import com.oct.sigspoof.ISpooferService
import com.oct.sigspoof.common.Constants
import com.oct.sigspoof.utils.Log
import java.io.File

class SpooferService : ISpooferService.Stub() {

    companion object {
        @Volatile
        var instance: SpooferService? = null
            private set
        
        // Permission constants (octal)
        private const val MODE_700 = 448  // 0700 in octal = 7*64 = 448
        private const val MODE_600 = 384  // 0600 in octal = 6*64 = 384
    }

    private val configLock = Any()
    private val dataDir = File(Constants.CONFIG_DIR)
    private val configFile = File(dataDir, Constants.CONFIG_FILE)

    init {
        instance = this
        ensureDataDir()
        Log.i("Service initialized, config dir: ${dataDir.absolutePath}")
    }

    private fun ensureDataDir() {
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        // Set directory permissions to 700
        try {
            Os.chmod(dataDir.absolutePath, MODE_700)
            Log.d("Set directory permissions to 0700")
        } catch (e: Exception) {
            Log.e("Failed to set directory permissions: ${e.message}", e)
        }
        
        // Set file permissions to 600 if it exists
        if (configFile.exists()) {
            try {
                Os.chmod(configFile.absolutePath, MODE_600)
            } catch (e: Exception) {
                Log.e("Failed to set file permissions: ${e.message}", e)
            }
        }
    }

    @Throws(RemoteException::class)
    override fun getServiceVersion(): Int = Constants.SERVICE_VERSION

    @Throws(RemoteException::class)
    override fun getConfig(): String? {
        return synchronized(configLock) {
            if (configFile.exists()) {
                runCatching { configFile.readText() }.getOrNull()
            } else {
                null
            }
        }
    }

    @Throws(RemoteException::class)
    override fun setConfig(json: String?) {
        if (json == null) return
        synchronized(configLock) {
            ensureDataDir()
            runCatching {
                configFile.writeText(json)
                // Set permissions to 600
                Os.chmod(configFile.absolutePath, MODE_600)
                Log.d("Config saved (${json.length} bytes) with mode 0600")
            }.onFailure { e ->
                Log.e("Failed to save config: ${e.message}", e)
            }
        }
        // Notify ConfigManager to reload (it uses FileObserver, so write triggers it)
    }

    @Throws(RemoteException::class)
    override fun deleteConfig() {
        synchronized(configLock) {
            if (configFile.exists()) {
                configFile.delete()
                Log.d("Config deleted")
            }
        }
    }

    fun stop() {
        instance = null
    }
}
