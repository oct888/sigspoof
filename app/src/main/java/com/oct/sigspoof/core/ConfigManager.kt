package com.oct.sigspoof.core

import android.content.pm.Signature
import android.os.FileObserver
import android.util.Base64
import com.oct.sigspoof.common.Constants
import com.oct.sigspoof.utils.Log
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object ConfigManager {

    private val configRef = AtomicReference<Map<String, SpoofEntry>>(emptyMap())
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "SpooferConfig") }
    private val started = AtomicBoolean(false)

    private val configDir = File(Constants.CONFIG_DIR)
    private val configFile = File(configDir, Constants.CONFIG_FILE)

    private val fileObserver = object : FileObserver(
        configDir.path,
        CREATE or CLOSE_WRITE or MOVED_TO or DELETE or MOVED_FROM
    ) {
        override fun onEvent(event: Int, path: String?) {
            if (path != Constants.CONFIG_FILE) return
            when {
                event and (CREATE or CLOSE_WRITE or MOVED_TO) != 0 -> scheduleReload()
                event and (DELETE or MOVED_FROM) != 0 -> scheduleReload()
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        ensureConfigDir()
        loadConfigNow()
        fileObserver.startWatching()
        Log.d("Started, watching ${configDir.path}")
    }

    fun hasConfig(): Boolean = configRef.get().isNotEmpty()

    fun getEntry(packageName: String): SpoofEntry? = configRef.get()[packageName]

    private fun scheduleReload() {
        executor.execute { loadConfigNow() }
    }

    private fun loadConfigNow() {
        val newConfig = runCatching { readConfigFile() }.getOrElse { emptyMap() }
        val previous = configRef.getAndSet(newConfig)
        if (newConfig != previous) {
            if (newConfig.isEmpty()) {
                Log.d("Config cleared")
            } else {
                Log.i("Loaded ${newConfig.size} package spoof(s)")
            }
        }
    }

    private fun readConfigFile(): Map<String, SpoofEntry> {
        if (!configFile.exists()) return emptyMap()
        val contents = runCatching { configFile.readText() }.getOrNull()?.trim() ?: return emptyMap()
        if (contents.isBlank()) return emptyMap()
        return parseConfig(contents)
    }

    private fun parseConfig(raw: String): Map<String, SpoofEntry> {
        val map = mutableMapOf<String, SpoofEntry>()
        val json = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            Log.e("Failed to parse config: ${t.message}", t)
            return emptyMap()
        }
        val keys = json.keys()
        while (keys.hasNext()) {
            val packageName = keys.next().trim()
            if (packageName.isEmpty()) continue
            
            val rawValue = json.optString(packageName, "").trim()
            if (rawValue.isEmpty()) continue
            
            val entry = createEntry(rawValue)
            if (entry != null) {
                map[packageName] = entry
                Log.d("Loaded entry for $packageName")
            }
        }
        return map
    }

    private fun createEntry(rawValue: String): SpoofEntry? {
        // Handle various forms of newlines and whitespace in base64
        // Remove: literal newlines, escaped \n, double-escaped \\n, any whitespace
        val base64 = rawValue.replace(Regex("\\s+"), "")  // Whitespace
        if (base64.isEmpty()) return null
        val decoded = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrElse {
            Log.e("Failed to decode base64: ${it.message}")
            return null
        }
        val signature = runCatching { Signature(decoded) }.getOrElse {
            Log.e("Failed to build Signature: ${it.message}")
            return null
        }
        val publicKey = runCatching {
            val factory = CertificateFactory.getInstance("X.509")
            val cert = factory.generateCertificate(ByteArrayInputStream(decoded))
            cert.publicKey
        }.getOrNull()
        return SpoofEntry(signature, publicKey)
    }

    private fun ensureConfigDir() {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
    }
}

internal data class SpoofEntry(
    val signature: Signature,
    val publicKey: PublicKey?
)
