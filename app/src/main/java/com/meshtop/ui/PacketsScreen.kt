package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MonitorUiState
import com.meshtop.data.PacketInfo
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun PacketsScreen(
    state: MonitorUiState,
    getRelayName: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val packets = state.recentPackets
    val scrollState = rememberScrollState()

    // Find first hearers (lowest hop count per packet_id)
    val firstHearers = remember(packets) {
        val groups = mutableMapOf<Int, MutableList<PacketInfo>>()
        for (pkt in packets) {
            groups.getOrPut(pkt.packetId) { mutableListOf() }.add(pkt)
        }
        val result = mutableMapOf<Int, String>()
        for ((pktId, group) in groups) {
            if (group.size > 1) {
                val first = group.minByOrNull {
                    if (it.hopStart > 0) it.hopStart - it.hopLimit else 999
                }
                if (first != null) result[pktId] = first.gatewayId
            }
        }
        result
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionDescription(
            title = "Recent Packets",
            description = "Live packet stream. Green=direct (0 hops), Cyan \u2605=first gateway to hear. RHex=relay node last byte."
        )

        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Header
            Row(
                modifier = Modifier
                    .background(SurfaceCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                HeaderCell("Time", 68.dp)
                HeaderCell("From", 60.dp)
                HeaderCell("Type", 72.dp)
                HeaderCell("RHex", 40.dp)
                HeaderCell("Relay", 58.dp)
                HeaderCell("RSSI", 45.dp)
                HeaderCell("SNR", 45.dp)
                HeaderCell("Hop", 32.dp)
                HeaderCell("Gateway", 80.dp)
                HeaderCell("1st", 24.dp)
            }

            HorizontalDivider(color = Color(0xFF333355))

            if (packets.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Waiting for packets...",
                        color = TextDim,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(packets.take(80), key = { "${it.packetId}_${it.gatewayId}" }) { pkt ->
                        PacketRow(pkt, firstHearers, getRelayName)
                        HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PacketRow(
    pkt: PacketInfo,
    firstHearers: Map<Int, String>,
    getRelayName: (Int) -> String,
) {
    val hopCount = if (pkt.hopStart > 0) pkt.hopStart - pkt.hopLimit else -1
    val isDirect = hopCount == 0
    val isFirstHearer = firstHearers[pkt.packetId] == pkt.gatewayId

    val textColor = when {
        isDirect -> DirectGreen
        isFirstHearer -> FirstHearerCyan
        else -> Color(0xFFE0E0E0)
    }
    val fontWeight = if (isDirect || isFirstHearer) FontWeight.Bold else FontWeight.Normal

    val timeStr = timeFormatter.format(pkt.timestamp)
    val rssiStr = pkt.rssi?.let { "%.0f".format(it) } ?: "-"
    val snrStr = pkt.snr?.let { "%.1f".format(it) } ?: "-"
    val hopStr = if (hopCount >= 0) "$hopCount" else "-"
    val relayHex = if (pkt.relayNode > 0) String.format("%02x", pkt.relayNode) else "-"
    val relayName = if (pkt.relayNode > 0) getRelayName(pkt.relayNode).take(6) else "-"
    val gateway = pkt.gatewayName
    val firstMarker = if (isFirstHearer) "\u2605" else ""

    val index = 0 // alternating will be handled by container
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        DataCell(timeStr, 68.dp, textColor, fontWeight)
        DataCell(pkt.fromName.take(6), 60.dp, textColor, fontWeight)
        DataCell(pkt.portnumName.take(9), 72.dp, textColor, fontWeight)
        DataCell(relayHex, 40.dp, textColor, fontWeight)
        DataCell(relayName, 58.dp, textColor, fontWeight)
        DataCell(rssiStr, 45.dp, textColor, fontWeight)
        DataCell(snrStr, 45.dp, textColor, fontWeight)
        DataCell(hopStr, 32.dp, textColor, fontWeight)
        DataCell(gateway, 80.dp, textColor, fontWeight)
        DataCell(firstMarker, 24.dp, WarningYellow, FontWeight.Bold)
    }
}
