package com.emildesign.sdk

import com.emildesign.sdk.data.transport.DeviceTransport
import com.emildesign.sdk.data.transport.model.DeviceCommand
import com.emildesign.sdk.data.transport.model.TransportEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeTransport : DeviceTransport {
    val eventsFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)
    override val events: Flow<TransportEvent> = eventsFlow

    var connectCallCount = 0
    var disconnectCallCount = 0
    val sentCommands = mutableListOf<DeviceCommand>()
    val lastCommand: DeviceCommand? get() = sentCommands.lastOrNull()

    override suspend fun connect(deviceId: String) {
        connectCallCount++
    }

    override suspend fun disconnect() {
        disconnectCallCount++
    }

    override suspend fun sendCommand(command: DeviceCommand) {
        sentCommands.add(command)
    }
}
