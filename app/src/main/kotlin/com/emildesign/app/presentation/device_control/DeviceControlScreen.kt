package com.emildesign.app.presentation.device_control

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emildesign.sdk.domain.model.ConnectionState
import com.emildesign.app.presentation.device_control.components.BatteryCard
import com.emildesign.app.presentation.device_control.components.ConnectionCard
import com.emildesign.app.presentation.device_control.components.VolumeCard

@Composable
fun DeviceControlScreen(viewModel: DeviceControlViewModel = viewModel()) {
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceData by viewModel.deviceData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Device Control", style = MaterialTheme.typography.headlineMedium)
        Text("Connectivity SDK — sample app", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = viewModel.deviceIdInput,
            onValueChange = viewModel::updateDeviceIdInput,
            label = { Text("Device ID") },
            modifier = Modifier.fillMaxWidth(),
            enabled = connectionState is ConnectionState.Idle || connectionState is ConnectionState.Failed
        )

        ConnectionCard(
            deviceId = viewModel.deviceIdInput,
            state = connectionState,
            onConnect = { viewModel.connect(viewModel.deviceIdInput) },
            onDisconnect = { viewModel.disconnect() }
        )

        BatteryCard(deviceData = deviceData)

        VolumeCard(
            deviceData = deviceData,
            isConnected = connectionState is ConnectionState.Connected,
            onVolumeChange = viewModel::setVolume
        )
    }
}
