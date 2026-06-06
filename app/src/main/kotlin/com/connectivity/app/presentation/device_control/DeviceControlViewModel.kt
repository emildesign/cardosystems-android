package com.connectivity.app.presentation.device_control

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectivity.sdk.api.DeviceConnector
import com.connectivity.sdk.domain.model.ConnectionState
import com.connectivity.sdk.domain.model.DeviceData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DeviceControlViewModel(
    private val connector: DeviceConnector = DeviceConnector.create()
) : ViewModel() {

    var deviceIdInput by mutableStateOf("mock-device-01")
        private set

    fun updateDeviceIdInput(newValue: String) {
        deviceIdInput = newValue
    }

    val connectionState: StateFlow<ConnectionState> = connector.connectionState
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), ConnectionState.Idle)

    val deviceData: StateFlow<DeviceData?> = connector.deviceData
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), null)

    fun connect(deviceId: String) {
        viewModelScope.launch {
            connector.connect(deviceId)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connector.disconnect()
        }
    }

    fun setVolume(level: Int) {
        viewModelScope.launch {
            connector.setVolume(level)
                .onFailure { /* log or show UI error */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connector.release()
    }
}