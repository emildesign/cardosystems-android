package com.connectivity.sdk

import app.cash.turbine.test
import com.connectivity.sdk.api.DeviceConnector
import com.connectivity.sdk.domain.model.ConnectionState
import com.connectivity.sdk.domain.model.DeviceData
import com.connectivity.sdk.domain.model.DisconnectReason
import com.connectivity.sdk.data.transport.MockDeviceTransport
import com.connectivity.sdk.data.transport.model.TransportEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Tests ----

    @Test
    fun `givenNewConnector_thenInitialStateIsIdle`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)
        assertEquals(ConnectionState.Idle, connector.connectionState.value)
    }

    @Test
    fun `givenIdle_whenConnectCalled_thenTransitionsToConnected`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

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
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

        connector.connectionState.test {
            awaitItem() // Idle
            connector.connect("device-01")
            awaitItem() // Connecting

            transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.Timeout))
            val failed = awaitItem()
            assertTrue(failed is ConnectionState.Failed)
            assertEquals(DisconnectReason.Timeout, (failed as ConnectionState.Failed).reason)
        }
    }

    @Test
    fun `givenConnected_whenUnexpectedDisconnectReceived_thenTransitionsToFailed`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

        connector.connectionState.test {
            assertEquals(ConnectionState.Idle, awaitItem())

            connector.connect("device-01")
            assertEquals(ConnectionState.Connecting, awaitItem())

            transport.eventsFlow.emit(TransportEvent.Connected)
            assertTrue(awaitItem() is ConnectionState.Connected)

            transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.UnexpectedDisconnect))
            val failed = awaitItem()
            assertTrue(failed is ConnectionState.Failed)
            assertEquals(DisconnectReason.UnexpectedDisconnect, (failed as ConnectionState.Failed).reason)
        }
    }

    @Test
    fun `givenConnected_whenDisconnectCalled_thenTransitionsToIdleAfterTransportEvent`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

        connector.connectionState.test {
            assertEquals(ConnectionState.Idle, awaitItem())

            connector.connect("device-01")
            assertEquals(ConnectionState.Connecting, awaitItem())

            transport.eventsFlow.emit(TransportEvent.Connected)
            assertTrue(awaitItem() is ConnectionState.Connected)

            connector.disconnect()
            assertEquals(ConnectionState.Disconnecting, awaitItem())

            transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.ConsumerDisconnected))
            yield() // Give the collector a chance to process the event
            assertEquals(ConnectionState.Idle, awaitItem())
        }
    }

    @Test
    fun `givenConnected_whenDataUpdateReceived_thenDeviceDataIsUpdated`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

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
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

        val result = connector.setVolume(5)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `givenConnecting_whenConnectCalledAgain_thenSecondCallIsNoOp`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

        connector.connect("device-01")
        val firstState = connector.connectionState.value

        connector.connect("device-02") // should be ignored
        assertEquals(firstState, connector.connectionState.value)
        
        // Verify only one connect call reached the transport
        assertEquals(1, transport.connectCallCount)
    }

    @Test
    fun `givenConnected_whenDisconnected_thenDeviceDataIsCleared`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)

        connector.connect("device-01")
        transport.eventsFlow.emit(TransportEvent.Connected)
        transport.eventsFlow.emit(TransportEvent.DataUpdate(DeviceData(volume = 5, battery = 70)))
        yield()

        connector.disconnect()
        transport.eventsFlow.emit(TransportEvent.Disconnected(DisconnectReason.ConsumerDisconnected))
        yield()

        assertNull(connector.deviceData.value)
    }

    @Test
    fun `givenConnector_whenReleased_thenInternalScopeIsCancelled`() = runTest {
        val transport = FakeTransport()
        val connector = DeviceConnector.createWithTransport(transport)
        
        connector.connect("device-01")
        transport.eventsFlow.emit(TransportEvent.Connected)
        advanceUntilIdle() // Ensure connect is processed
        
        connector.release()
        advanceUntilIdle() // Ensure release (which is a launch) is processed
        
        // After release, events from transport should not affect the connector
        transport.eventsFlow.emit(TransportEvent.DataUpdate(DeviceData(volume = 10, battery = 100)))
        advanceUntilIdle()
        
        // Data should not have updated if the monitoring job was cancelled
        assertNull(connector.deviceData.value)
    }

    @Test
    fun `givenMockTransportWithSuccessScenario_whenConnectCalled_thenTransitionsToConnected`() = runTest {
        val transport = MockDeviceTransport(MockDeviceTransport.MockScenario.Success)
        val connector = DeviceConnector.createWithTransport(transport)

        connector.connectionState.test {
            awaitItem() // Idle
            connector.connect("device-01")
            awaitItem() // Connecting
            val connected = awaitItem() // Connected (after 1s simulated delay)
            assertTrue(connected is ConnectionState.Connected)
        }
    }
}
