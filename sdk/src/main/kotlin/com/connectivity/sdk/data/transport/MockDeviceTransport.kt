package com.connectivity.sdk.data.transport

import com.connectivity.sdk.domain.model.DeviceData
import com.connectivity.sdk.domain.model.DisconnectReason
import com.connectivity.sdk.data.transport.model.DeviceCommand
import com.connectivity.sdk.data.transport.model.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Simulates a remote device. Think of it as a puppet that follows a script:
 * - connects after a short delay
 * - randomly changes battery/volume over time
 * - occasionally drops the connection unexpectedly
 *
 * Scenario is controllable via [MockScenario] for testing.
 */
internal class MockDeviceTransport(
    private val scenario: MockScenario = MockScenario.Success
) : DeviceTransport {

    enum class MockScenario { Success, Timeout, UnexpectedDisconnect }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)
    private var deviceJob: Job? = null
    private var currentVolume: Int = 5

    override val events: Flow<TransportEvent> = _events

    override suspend fun connect(deviceId: String) {
        when (scenario) {
            MockScenario.Timeout -> {
                delay(CONNECT_TIMEOUT_MS)
                _events.emit(TransportEvent.Disconnected(DisconnectReason.Timeout))
            }

            MockScenario.Success,
            MockScenario.UnexpectedDisconnect -> {
                delay(CONNECT_DELAY_MS)
                _events.emit(TransportEvent.Connected)
                startDeviceSimulation()
            }
        }
    }

    override suspend fun disconnect() {
        deviceJob?.cancel()
        deviceJob = null
        delay(DISCONNECT_DELAY_MS)
        _events.emit(TransportEvent.Disconnected(DisconnectReason.ConsumerDisconnected))
    }

    override suspend fun sendCommand(command: DeviceCommand) {
        when (command) {
            is DeviceCommand.SetVolume -> {
                currentVolume = command.level.coerceIn(0, 10)
                // Echo back as a device data update so the SDK state reflects the change
                _events.emit(
                    TransportEvent.DataUpdate(
                        DeviceData(volume = currentVolume, battery = lastBattery)
                    )
                )
            }
        }
    }

    // ---- Private ----

    private var lastBattery: Int = 80

    private fun startDeviceSimulation() {
        deviceJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                tickCount++

                // Simulate unexpected disconnect after N ticks in that scenario
                if (scenario == MockScenario.UnexpectedDisconnect && tickCount >= UNEXPECTED_DISCONNECT_AFTER_TICKS) {
                    _events.emit(TransportEvent.Disconnected(DisconnectReason.UnexpectedDisconnect))
                    break
                }

                // Device-originated: battery slowly drains
                lastBattery = (lastBattery - Random.nextInt(0, 3)).coerceAtLeast(0)

                // Device-originated: volume nudges occasionally
                if (tickCount % VOLUME_CHANGE_INTERVAL_TICKS == 0) {
                    currentVolume = (currentVolume + Random.nextInt(-1, 2)).coerceIn(0, 10)
                }

                _events.emit(
                    TransportEvent.DataUpdate(
                        DeviceData(volume = currentVolume, battery = lastBattery)
                    )
                )
            }
        }
    }

    companion object {
        private const val CONNECT_DELAY_MS = 1_000L
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val DISCONNECT_DELAY_MS = 300L
        private const val TICK_INTERVAL_MS = 2_000L
        private const val UNEXPECTED_DISCONNECT_AFTER_TICKS = 5
        private const val VOLUME_CHANGE_INTERVAL_TICKS = 3
    }
}
