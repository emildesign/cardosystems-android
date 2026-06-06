package com.emildesign.sdk.data.transport.model

import com.emildesign.sdk.domain.model.DeviceData
import com.emildesign.sdk.domain.model.DisconnectReason

/**
 * Internal events emitted by the [DeviceTransport].
 */
internal sealed class TransportEvent {
    data object Connected : TransportEvent()
    data class Disconnected(val reason: DisconnectReason) : TransportEvent()
    data class DataUpdate(val data: DeviceData) : TransportEvent()
}
