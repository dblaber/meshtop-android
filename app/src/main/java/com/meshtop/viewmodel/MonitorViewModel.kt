package com.meshtop.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meshtop.MeshtopApp
import com.meshtop.MonitorService
import com.meshtop.data.ConnectionSettings
import com.meshtop.data.MonitorUiState
import com.meshtop.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val monitor = (application as MeshtopApp).monitor
    private val settingsStore = SettingsStore(application)

    val state: StateFlow<MonitorUiState> = monitor.state

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<ConnectionSettings> = _settings.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    init {
        val s = _settings.value
        // Load node names from DB if configured
        if (s.dbEnabled) {
            monitor.loadNodeNamesFromDb(s)
        }
        // Connect MQTT
        viewModelScope.launch {
            monitor.connect(s)
        }
        // Start foreground service
        startService()
    }

    private fun startService() {
        val app = getApplication<Application>()
        val intent = Intent(app, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun updateSettings(newSettings: ConnectionSettings) {
        _settings.value = newSettings
        settingsStore.save(newSettings)
    }

    fun reconnect() {
        monitor.disconnect()
        val s = _settings.value
        if (s.dbEnabled) {
            monitor.loadNodeNamesFromDb(s)
        }
        viewModelScope.launch {
            monitor.connect(s)
        }
    }

    fun toggleSettings() {
        _showSettings.value = !_showSettings.value
    }

    fun hideSettings() {
        _showSettings.value = false
    }

    fun getRelayNodeName(relayNode: Int): String = monitor.getRelayNodeName(relayNode)

    override fun onCleared() {
        // Don't destroy monitor - it lives in the Application singleton
        // The MonitorService will keep it alive
    }
}
