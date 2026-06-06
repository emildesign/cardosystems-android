package com.emildesign.sdk.data.transport

import com.emildesign.sdk.data.transport.model.DeviceCommand
import com.emildesign.sdk.data.transport.model.TransportEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the communication layer.
 * Swap this for a real BLE/Wi-Fi implementation without touching SDK public surface.
 */
internal interface DeviceTransport {
    val events: Flow<TransportEvent>
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    suspend fun sendCommand(command: DeviceCommand)
}
