package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.GatewayStats
import com.meshtop.data.MonitorUiState
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun GatewaysScreen(state: MonitorUiState, modifier: Modifier = Modifier) {
    val gateways = state.gateways
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Section description
        SectionDescription(
            title = "Gateway Metrics",
            description = "MQTT gateways ranked by clean packet count. Clean = excluding gateway-originated and MY_NODES-relayed."
        )

        // Scrollable table area
        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Header row
            Row(
                modifier = Modifier
                    .background(SurfaceCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                HeaderCell("Gateway", 80.dp)
                HeaderCell("Byte", 40.dp)
                HeaderCell("Tot", 48.dp)
                HeaderCell("TDir", 48.dp)
                HeaderCell("TRly", 48.dp)
                HeaderCell("CDir", 48.dp)
                HeaderCell("CRly", 48.dp)
                HeaderCell("TCln", 48.dp)
                HeaderCell("RSSI", 48.dp)
                HeaderCell("SNR", 45.dp)
                HeaderCell("Nodes", 48.dp)
                HeaderCell("Last", 50.dp)
            }

            HorizontalDivider(color = Color(0xFF333355))

            if (gateways.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Waiting for gateway data...",
                        color = TextDim,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(gateways.take(25)) { index, gw ->
                        GatewayRow(gw, state.hexToNodeId, index)
                        HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GatewayRow(gw: GatewayStats, hexToNodeId: Map<String, Int>, index: Int) {
    val name = gw.shortName.ifEmpty { gw.gatewayId.take(10) }
    val lastByte = hexToNodeId[gw.gatewayId]?.let { String.format("%02x", it and 0xFF) } ?: "-"
    val rssiStr = gw.avgRssi?.let { "%.0f".format(it) } ?: "-"
    val snrStr = gw.avgSnr?.let { "%.1f".format(it) } ?: "-"
    val lastSeen = gw.lastSeen?.let { timeFormatter.format(it) } ?: "-"
    val nodeCount = gw.uniqueNodes.size.toString()

    val rowColor = if (index % 2 == 0) Color(0xFF12122A) else Color.Transparent

    Row(
        modifier = Modifier
            .background(rowColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        DataCell(name, 80.dp, FirstHearerCyan, FontWeight.Bold)
        DataCell(lastByte, 40.dp)
        DataCell("${gw.packetCount}", 48.dp)
        DataCell("${gw.totalDirectCount}", 48.dp, DirectGreen)
        DataCell("${gw.totalRelayedCount}", 48.dp, RelayOrange)
        DataCell("${gw.cleanDirectCount}", 48.dp)
        DataCell("${gw.cleanRelayedCount}", 48.dp)
        DataCell("${gw.totalClean}", 48.dp, FirstHearerCyan, FontWeight.Bold)
        DataCell(rssiStr, 48.dp)
        DataCell(snrStr, 45.dp)
        DataCell(nodeCount, 48.dp, StatMagenta)
        DataCell(lastSeen, 50.dp, TextDim)
    }
}

// Shared composables for all table screens

@Composable
fun SectionDescription(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = HeaderBlue,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            color = TextDim,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
    }
    HorizontalDivider(color = Color(0xFF333355))
}

@Composable
fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        color = StatMagenta,
        fontWeight = FontWeight.Bold,
        fontFamily = Mono,
        fontSize = 11.sp,
        modifier = Modifier.width(width),
    )
}

@Composable
fun DataCell(
    text: String,
    width: Dp,
    color: Color = Color(0xFFE0E0E0),
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        color = color,
        fontWeight = fontWeight,
        fontFamily = Mono,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}
