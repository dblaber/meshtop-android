package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MonitorUiState
import com.meshtop.ui.theme.*

@Composable
fun StatsHeader(
    state: MonitorUiState,
    hideGateways: Boolean = false,
    hideMyNodes: Boolean = false,
    onHideGatewaysChange: (Boolean) -> Unit = {},
    onHideMyNodesChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uptimeSeconds = state.uptimeMillis / 1000
    val hours = uptimeSeconds / 3600
    val minutes = (uptimeSeconds % 3600) / 60
    val secs = uptimeSeconds % 60
    val uptimeStr = "${hours}h ${minutes}m ${secs}s"
    val rate = if (uptimeSeconds > 0) state.totalPackets.toDouble() / uptimeSeconds else 0.0

    val isReconnecting = state.connectionError == "Reconnecting..."
    val mqttColor = when {
        state.connected -> DirectGreen
        state.connecting || isReconnecting -> WarningYellow
        state.connectionError != null -> ErrorRed
        else -> WarningYellow
    }
    val mqttText = when {
        state.connected -> "MQTT Connected"
        state.connecting -> "MQTT Connecting..."
        isReconnecting -> "MQTT Reconnecting..."
        state.connectionError != null -> "MQTT: ${state.connectionError?.take(20)}"
        else -> "MQTT Disconnected"
    }

    val dbColor = when {
        state.dbLoaded -> DirectGreen
        state.dbConnecting -> WarningYellow
        state.dbError != null -> ErrorRed
        !state.dbConfigured -> TextDim
        else -> TextDim
    }
    val dbText = when {
        state.dbLoaded -> "DB: ${state.dbNodeCount} nodes"
        state.dbConnecting -> "DB Connecting..."
        state.dbError != null -> "DB: ${state.dbError?.take(20)}"
        !state.dbConfigured -> "DB: Not configured"
        else -> "DB: Off"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard),
    ) {
        // Row 1: Connection status indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(mqttText, mqttColor)
            StatusDot(dbText, dbColor)
        }

        // Row 2: Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip("Pkts", "${state.totalPackets}", FirstHearerCyan)
            StatChip("GWs", "${state.gateways.size}", WarningYellow)
            StatChip("${"%.1f".format(rate)}/s", null, StatMagenta)
            StatChip(uptimeStr, null, HeaderBlue)
        }

        // Row 3: Filter toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterToggle("Hide GWs", hideGateways, onHideGatewaysChange, Modifier.weight(1f))
            FilterToggle("Hide Nodes", hideMyNodes, onHideMyNodesChange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusDot(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun FilterToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 11.sp,
            color = if (checked) FirstHearerCyan else TextDim,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FirstHearerCyan,
                checkedTrackColor = FirstHearerCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = SurfaceDark,
                uncheckedBorderColor = TextDim.copy(alpha = 0.3f),
            ),
            modifier = Modifier.height(24.dp),
        )
    }
}

@Composable
private fun StatChip(label: String, value: String?, color: androidx.compose.ui.graphics.Color) {
    if (value != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$label:", color = TextDim, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = value, color = color, fontWeight = FontWeight.Bold, fontFamily = Mono, fontSize = 12.sp)
        }
    } else {
        Text(text = label, color = color, fontWeight = FontWeight.Bold, fontFamily = Mono, fontSize = 12.sp)
    }
}
