package com.connectivity.sdk.data.transport.model

/**
 * Internal commands sent to the [DeviceTransport].
 */
internal sealed class DeviceCommand {
    data class SetVolume(val level: Int) : DeviceCommand()
}
