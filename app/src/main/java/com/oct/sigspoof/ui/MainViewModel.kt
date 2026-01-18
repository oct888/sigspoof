package com.oct.sigspoof.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oct.sigspoof.service.ServiceClient
import com.oct.sigspoof.utils.SignatureUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val apps: StateFlow<List<AppInfo>> = combine(
        _allApps,
        _searchQuery,
        _showSystemApps
    ) { apps, query, showSystem ->
        apps.filter { app ->
            (showSystem || !app.isSystem || app.isSpoofed) &&
            (query.isBlank() || app.label.contains(query, ignoreCase = true) || 
             app.packageName.contains(query, ignoreCase = true))
        }.sortedWith(
            compareByDescending<AppInfo> { it.isSpoofed } // Keep all spoofed (including pending remove) at top
                .thenBy { it.label.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _pendingRemovals = MutableStateFlow<Set<String>>(emptySet())
    
    val hasPendingChanges: StateFlow<Boolean> = _pendingRemovals.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _permissionRestricted = MutableStateFlow(false)
    val permissionRestricted: StateFlow<Boolean> = _permissionRestricted.asStateFlow()

    val isServiceConnected: StateFlow<Boolean> = ServiceClient.isConnected

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadData()
    }

    fun loadData(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val myPackage = getApplication<Application>().packageName
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                // Get config from service
                val configMap = parseConfig(ServiceClient.getConfig())
                
                val appInfoList = installedApps.mapNotNull { appInfo ->
                    try {
                        AppInfo(
                            packageName = appInfo.packageName,
                            label = pm.getApplicationLabel(appInfo).toString(),
                            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            isSpoofed = configMap.containsKey(appInfo.packageName),
                            spoofedSignature = configMap[appInfo.packageName],
                            pendingState = PendingState.NONE
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                _allApps.value = appInfoList
                
                // Detect Chinese ROM restriction: only system apps + this app visible
                val userApps = appInfoList.filter { !it.isSystem }
                _permissionRestricted.value = userApps.size == 1 && userApps.first().packageName == myPackage
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun refresh() {
        loadData(isRefresh = true)
    }

    private fun parseConfig(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onToggleSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    fun markForRemoval(packageName: String) {
        _pendingRemovals.value = _pendingRemovals.value + packageName
        updateAppPendingState(packageName, PendingState.PENDING_REMOVE)
    }

    fun cancelRemoval(packageName: String) {
        _pendingRemovals.value = _pendingRemovals.value - packageName
        updateAppPendingState(packageName, PendingState.NONE)
    }

    private fun updateAppPendingState(packageName: String, state: PendingState) {
        _allApps.value = _allApps.value.map { app ->
            if (app.packageName == packageName) app.copy(pendingState = state) else app
        }
    }

    fun updateSpoof(packageName: String, signature: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentConfig = ServiceClient.getConfig()
                val configMap = parseConfig(currentConfig).toMutableMap()
                configMap[packageName] = signature
                
                val gson = GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()
                
                ServiceClient.setConfig(gson.toJson(configMap) + "\n")
            }
            loadData()
        }
    }

    fun commitChanges() {
        viewModelScope.launch {
            val removals = _pendingRemovals.value
            if (removals.isEmpty()) return@launch
            
            withContext(Dispatchers.IO) {
                val currentConfig = ServiceClient.getConfig()
                val configMap = parseConfig(currentConfig).toMutableMap()
                
                // Remove pending removals from config
                removals.forEach { packageName ->
                    configMap.remove(packageName)
                }
                
                val gson = GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create()
                
                ServiceClient.setConfig(gson.toJson(configMap) + "\n")
            }
            
            _pendingRemovals.value = emptySet()
            loadData()
        }
    }

    fun getInstalledSignature(packageName: String): String? {
        return SignatureUtils.getSignatureString(getApplication(), packageName)
    }

    fun getApkSignature(apkPath: String): String? {
        return SignatureUtils.getSignatureFromApk(getApplication(), apkPath)
    }
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isSpoofed: Boolean,
    val spoofedSignature: String? = null,
    val pendingState: PendingState = PendingState.NONE
)

enum class PendingState {
    NONE,
    PENDING_REMOVE
}
