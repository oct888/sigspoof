package com.oct.sigspoof.service

import android.os.IBinder
import com.oct.sigspoof.ISpooferService
import com.oct.sigspoof.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceClient : IBinder.DeathRecipient {

    private var service: ISpooferService? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun linkService(binder: IBinder) {
        synchronized(this) {
            try {
                service = ISpooferService.Stub.asInterface(binder)
                binder.linkToDeath(this, 0)
                _isConnected.value = true
                Log.i("Service linked")
            } catch (e: Throwable) {
                Log.e("Failed to link service: ${e.message}", e)
                service = null
                _isConnected.value = false
            }
        }
    }

    override fun binderDied() {
        synchronized(this) {
            Log.w("Binder died")
            service = null
            _isConnected.value = false
        }
    }

    fun getServiceVersion(): Int? {
        return synchronized(this) {
            try {
                service?.serviceVersion
            } catch (e: Throwable) {
                Log.e("getServiceVersion error: ${e.message}", e)
                null
            }
        }
    }

    fun getConfig(): String? {
        return synchronized(this) {
            try {
                service?.config
            } catch (e: Throwable) {
                Log.e("getConfig error: ${e.message}", e)
                null
            }
        }
    }

    fun setConfig(json: String) {
        synchronized(this) {
            try {
                service?.setConfig(json)
            } catch (e: Throwable) {
                Log.e("setConfig error: ${e.message}", e)
            }
        }
    }

    fun deleteConfig() {
        synchronized(this) {
            try {
                service?.deleteConfig()
            } catch (e: Throwable) {
                Log.e("deleteConfig error: ${e.message}", e)
            }
        }
    }
}
