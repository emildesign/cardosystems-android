package com.connectivity.sdk.domain.model

/**
 * Represents reasons why a device might be disconnected.
 */
sealed class DisconnectReason {
    data object Timeout : DisconnectReason()
    data object UnexpectedDisconnect : DisconnectReason()
    data object ConsumerDisconnected : DisconnectReason()
}
