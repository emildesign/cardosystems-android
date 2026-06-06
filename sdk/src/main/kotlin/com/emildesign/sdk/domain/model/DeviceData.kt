package com.emildesign.sdk.domain.model

/**
 * Data received from the device.
 */
data class DeviceData(
    val volume: Int,   // 0–10
    val battery: Int   // 0–100
)
