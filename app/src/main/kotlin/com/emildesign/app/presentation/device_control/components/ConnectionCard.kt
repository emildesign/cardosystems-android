package com.emildesign.app.presentation.device_control.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emildesign.sdk.domain.model.ConnectionState

@Composable
fun ConnectionCard(
    deviceId: String,
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Device ID", style = MaterialTheme.typography.labelSmall)
                    Text(deviceId, style = MaterialTheme.typography.bodyLarge)
                }
                StateChip(state = state)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = state is ConnectionState.Idle || state is ConnectionState.Failed,
                    modifier = Modifier.weight(1f)
                ) { Text("Connect") }

                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = state is ConnectionState.Connected,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Disconnect") }
            }
        }
    }
}
