package com.connectivity.app.presentation.device_control.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.connectivity.sdk.domain.model.ConnectionState

@Composable
fun StateChip(state: ConnectionState) {
    val (label, color) = when (state) {
        is ConnectionState.Idle -> "Idle" to Color.Gray
        is ConnectionState.Connecting -> "Connecting…" to Color(0xFFFFA500)
        is ConnectionState.Connected -> "Connected" to Color(0xFF4CAF50)
        is ConnectionState.Disconnecting -> "Disconnecting…" to Color(0xFFFFA500)
        is ConnectionState.Failed -> "Failed: ${state.reason::class.simpleName}" to Color.Red
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
