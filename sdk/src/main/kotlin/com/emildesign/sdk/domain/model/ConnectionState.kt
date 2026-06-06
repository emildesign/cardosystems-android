package com.emildesign.sdk.domain.model

/**
 * Represents the current state of the device connection.
 */
sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceId: String) : ConnectionState()
    data object Disconnecting : ConnectionState()
    data class Failed(val reason: DisconnectReason) : ConnectionState()
}
