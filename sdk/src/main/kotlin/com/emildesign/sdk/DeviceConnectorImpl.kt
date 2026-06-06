package com.emildesign.sdk

import com.emildesign.sdk.api.DeviceConnector
import com.emildesign.sdk.domain.model.ConnectionState
import com.emildesign.sdk.domain.model.DeviceData
import com.emildesign.sdk.domain.model.DisconnectReason
import com.emildesign.sdk.data.transport.DeviceTransport
import com.emildesign.sdk.data.transport.MockDeviceTransport
import com.emildesign.sdk.data.transport.model.DeviceCommand
import com.emildesign.sdk.data.transport.model.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Core SDK implementation.
 */
internal class DeviceConnectorImpl(
    private val transport: DeviceTransport = MockDeviceTransport(),
    externalScope: CoroutineScope? = null
) : DeviceConnector {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val volumeMutex = Mutex()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceData = MutableStateFlow<DeviceData?>(null)
    override val deviceData: StateFlow<DeviceData?> = _deviceData.asStateFlow()

    private var eventListenerJob: Job? = null
    private var storedDeviceId: String? = null

    // ---- Public API ----

    override suspend fun connect(deviceId: String) {
        val current = _connectionState.value
        if (current is ConnectionState.Connecting || current is ConnectionState.Connected) return

        storedDeviceId = deviceId
        _connectionState.value = ConnectionState.Connecting
        startListeningToTransport()

        withContext(Dispatchers.IO) {
            transport.connect(deviceId)
        }
    }

    override suspend fun disconnect() {
        val current = _connectionState.value
        if (current is ConnectionState.Idle || current is ConnectionState.Failed) return

        _connectionState.value = ConnectionState.Disconnecting

        withContext(Dispatchers.IO) {
            transport.disconnect()
        }
    }

    override suspend fun setVolume(level: Int): Result<Unit> = runCatching {
        check(_connectionState.value is ConnectionState.Connected) {
            "setVolume called while not Connected. Current state: ${_connectionState.value}"
        }
        volumeMutex.withLock {
            withContext(Dispatchers.IO) {
                transport.sendCommand(DeviceCommand.SetVolume(level))
            }
        }
    }

    override fun release() {
        scope.launch {
            try {
                disconnect()
            } finally {
                eventListenerJob?.cancel()
                scope.cancel()
            }
        }
    }

    // ---- Private ----

    private fun startListeningToTransport() {
        eventListenerJob?.cancel()
        eventListenerJob = scope.launch {
            transport.events.collect { event ->
                handleTransportEvent(event)
            }
        }
    }

    private fun handleTransportEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.Connected -> {
                _connectionState.value = ConnectionState.Connected(storedDeviceId ?: "unknown")
            }

            is TransportEvent.Disconnected -> {
                when (event.reason) {
                    DisconnectReason.ConsumerDisconnected -> {
                        _connectionState.value = ConnectionState.Idle
                        _deviceData.value = null
                        eventListenerJob?.cancel()
                    }
                    else -> {
                        _connectionState.value = ConnectionState.Failed(event.reason)
                        _deviceData.value = null
                        eventListenerJob?.cancel()
                    }
                }
            }

            is TransportEvent.DataUpdate -> {
                if (_connectionState.value is ConnectionState.Connected) {
                    _deviceData.value = event.data
                }
            }
        }
    }
}
