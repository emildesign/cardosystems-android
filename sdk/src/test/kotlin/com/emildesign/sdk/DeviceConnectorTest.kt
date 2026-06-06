package com.emildesign.sdk

import app.cash.turbine.test
import com.emildesign.sdk.api.DeviceConnector
import com.emildesign.sdk.domain.model.ConnectionState
import com.emildesign.sdk.domain.model.DeviceData
import com.emildesign.sdk.domain.model.DisconnectReason
import com.emildesign.sdk.data.transport.model.TransportEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for [DeviceConnector].
 * Uses [FakeTransport] to simulate transport-layer behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConnectorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var transport: FakeTransport
    private lateinit var connector: DeviceConnector

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        transport = FakeTransport()
        connector = DeviceConnector.createWithTransport(transport)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Lifecycle Tests ----

    @Test
    fun `givenNewConnector_thenInitialStateIsIdle`() = runTest {
        assertEquals(ConnectionState.Idle, connector.connectionState.value)
    }

    @Test
    fun `givenIdle_whenConnectCalled_thenTransitionsToConnected`() = runTest {
        connector.connectionState.test {
            assertEquals(ConnectionState.Idle, awaitItem())

            connector.connect("device-01")
            assertEquals(ConnectionState.Connecting, awaitItem())

            transport.eventsFlow.emit(TransportEvent.Connected)
            val connected = awaitItem()
            assertTrue(connected is ConnectionState.Connected)
            assertEquals("device-01", (connected as ConnectionState.Connected).deviceId)
        }
    }

    @Test
    fun `givenConnecting_whenTimeoutReceived_thenTransitionsToFailedWithTimeout`() = runTest {
        connector.connectionState.test {
            awaitItem() // Idle
            connector.connect("device-01")
            awaitItem() // Connecting

            transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.Timeout))
            val failed = awaitItem()
            assertTrue(failed is ConnectionState.Failed)
            assertEquals(DisconnectReason.Timeout, (failed as ConnectionState.Failed).reason)
            assertNull(connector.deviceData.value)
        }
    }

    @Test
    fun `givenConnected_whenUnexpectedDisconnectReceived_thenTransitionsToFailedAndClearsData`() = runTest {
        connector.connectionState.test {
            awaitItem() // Idle
            connector.connect("device-01")
            transport.eventsFlow.emit(TransportEvent.Connected)
            assertTrue(awaitItem() is ConnectionState.Connecting)
            assertTrue(awaitItem() is ConnectionState.Connected)

            transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.UnexpectedDisconnect))
            val failed = awaitItem()
            assertTrue(failed is ConnectionState.Failed)
            assertEquals(DisconnectReason.UnexpectedDisconnect, (failed as ConnectionState.Failed).reason)
            assertNull(connector.deviceData.value)
        }
    }

    @Test
    fun `givenConnected_whenDisconnectCalled_thenTransitionsToIdleAfterTransportEvent`() = runTest {
        connector.connectionState.test {
            awaitItem() // Idle
            connector.connect("device-01")
            transport.eventsFlow.emit(TransportEvent.Connected)
            skipItems(2) // Connecting, Connected

            connector.disconnect()
            assertEquals(ConnectionState.Disconnecting, awaitItem())

            transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.ConsumerDisconnected))
            yield()
            assertEquals(ConnectionState.Idle, awaitItem())
            assertNull(connector.deviceData.value)
        }
    }

    // ---- Data & Commands Tests ----

    @Test
    fun `givenConnected_whenDataUpdateReceived_thenDeviceDataIsUpdated`() = runTest {
        connector.deviceData.test {
            assertNull(awaitItem()) // initial null

            connector.connect("device-01")
            transport.eventsFlow.emit(TransportEvent.Connected)
            yield()

            transport.eventsFlow.emit(TransportEvent.DataUpdate(DeviceData(volume = 7, battery = 80)))
            assertEquals(DeviceData(volume = 7, battery = 80), awaitItem())
        }
    }

    @Test
    fun `givenIdle_whenSetVolumeCalled_thenReturnsFailure`() = runTest {
        val result = connector.setVolume(5)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `givenConnected_whenMultipleVolumeCommandsSentRapidly_thenProcessedSequentially`() = runTest {
        // Setup: Connect first
        connector.connect("device-01")
        transport.eventsFlow.emit(TransportEvent.Connected)
        advanceUntilIdle()

        // Action: Launch 5 volume commands simultaneously
        val volumes = listOf(1, 2, 3, 4, 5)
        val jobs = volumes.map { vol ->
            async {
                connector.setVolume(vol)
            }
        }
        
        jobs.awaitAll()
        advanceUntilIdle()

        // Verification: Transport should have received all calls. 
        assertEquals(5, transport.sentCommands.size)
        val lastCommand = transport.lastCommand as com.emildesign.sdk.data.transport.model.DeviceCommand.SetVolume
        assertEquals(5, lastCommand.level)
    }

    @Test
    fun `givenConnecting_whenConnectCalledAgain_thenSecondCallIsNoOp`() = runTest {
        connector.connect("device-01")
        val firstState = connector.connectionState.value

        connector.connect("device-02") // should be ignored
        assertEquals(firstState, connector.connectionState.value)
        
        // Verify only one connect call reached the transport
        assertEquals(1, transport.connectCallCount)
    }

    @Test
    fun `givenConnector_whenReleased_thenInternalScopeIsCancelled`() = runTest {
        connector.connect("device-01")
        transport.eventsFlow.emit(TransportEvent.Connected)
        advanceUntilIdle()
        
        connector.release()
        advanceUntilIdle()
        
        transport.eventsFlow.emit(TransportEvent.DataUpdate(DeviceData(volume = 10, battery = 100)))
        advanceUntilIdle()
        
        assertNull(connector.deviceData.value)
    }

    @Test
    fun `givenFailedState_whenConnectCalled_thenCanReconnectSuccessfully`() = runTest {
        // 1. Fail first
        connector.connect("device-01")
        transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.Timeout))
        advanceUntilIdle()
        assertTrue(connector.connectionState.value is ConnectionState.Failed)

        // 2. Try again
        connector.connect("device-01")
        transport.eventsFlow.emit(TransportEvent.Connected)
        advanceUntilIdle()
        
        assertTrue(connector.connectionState.value is ConnectionState.Connected)
    }
}
