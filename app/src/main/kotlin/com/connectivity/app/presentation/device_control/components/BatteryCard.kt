package com.connectivity.app.presentation.device_control.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.connectivity.sdk.domain.model.DeviceData

@Composable
fun BatteryCard(deviceData: DeviceData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Battery", style = MaterialTheme.typography.titleMedium)
                Text("device-originated", style = MaterialTheme.typography.labelSmall)
            }
            val battery = deviceData?.battery ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { battery / 100f },
                    modifier = Modifier.weight(1f),
                    color = if (battery > 20) Color(0xFF4CAF50) else Color.Red
                )
                Text("$battery%", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
