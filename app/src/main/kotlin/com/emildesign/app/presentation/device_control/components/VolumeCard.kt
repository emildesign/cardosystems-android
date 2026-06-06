package com.emildesign.app.presentation.device_control.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emildesign.sdk.domain.model.DeviceData

@Composable
fun VolumeCard(
    deviceData: DeviceData?,
    isConnected: Boolean,
    onVolumeChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Volume", style = MaterialTheme.typography.titleMedium)
                Text("app + device originated", style = MaterialTheme.typography.labelSmall)
            }
            val volume = deviceData?.volume ?: 0
            Text("$volume /10", style = MaterialTheme.typography.displaySmall)
            Slider(
                value = volume.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
                enabled = isConnected
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0", style = MaterialTheme.typography.labelSmall)
                Text("10", style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { if (volume > 0) onVolumeChange(volume - 1) },
                    enabled = isConnected && volume > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("-") }

                Button(
                    onClick = { onVolumeChange(0) },
                    enabled = isConnected,
                    modifier = Modifier.weight(1f)
                ) { Text("Mute") }

                OutlinedButton(
                    onClick = { if (volume < 10) onVolumeChange(volume + 1) },
                    enabled = isConnected && volume < 10,
                    modifier = Modifier.weight(1f)
                ) { Text("+") }
            }
        }
    }
}
