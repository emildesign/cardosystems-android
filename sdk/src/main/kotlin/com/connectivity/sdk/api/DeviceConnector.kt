package com.connectivity.sdk.api

import com.connectivity.sdk.domain.model.ConnectionState
import com.connectivity.sdk.domain.model.DeviceData
import com.connectivity.sdk.DeviceConnectorImpl
import com.connectivity.sdk.data.transport.DeviceTransport
import kotlinx.coroutines.flow.StateFlow

/**
 * Public entry point for the Device Connectivity SDK.
 *
 * Usage:
 *   val connector = DeviceConnector.create()
 *   connector.connect("device-01")
 *   connector.connectionState.collect { ... }
 */
interface DeviceConnector {

    /** Current connection lifecycle state. Always has a value (starts as Idle). */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Latest device readings. Null until first data arrives after connecting.
     * Updates from both app-originated (setVolume) and device-originated events.
     */
    val deviceData: StateFlow<DeviceData?>

    /**
     * Initiates a connection to the device with the given ID.
     * No-op if already Connecting or Connected.
     * Suspends until the transport has processed the connect request.
     */
    suspend fun connect(deviceId: String)

    /**
     * Tears down the connection gracefully.
     * No-op if Idle or Failed.
     * Suspends until teardown is complete.
     */
    suspend fun disconnect()

    /**
     * Sends a volume command to the connected device.
     * @param level 0–10 inclusive
     * @return Result indicating success or failure (e.g. if not Connected)
     */
    suspend fun setVolume(level: Int): Result<Unit>

    /**
     * Releases all internal resources (cancels coroutine scopes, etc).
     * The connector instance should not be used after calling this.
     */
    fun release()

    companion object {
        /** Factory — production entry point */
        fun create(): DeviceConnector = DeviceConnectorImpl()

        /** Test/demo entry point — inject a custom transport */
        internal fun createWithTransport(transport: DeviceTransport): DeviceConnector =
            DeviceConnectorImpl(transport)
    }
}
