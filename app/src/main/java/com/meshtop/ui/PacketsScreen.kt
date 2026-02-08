package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MonitorUiState
import com.meshtop.data.PacketInfo
import com.meshtop.data.RelayNodeStats
import com.meshtop.ui.theme.*
import java.time.Instant
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
    var selectedPacket by remember { mutableStateOf<PacketInfo?>(null) }

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
                        PacketRow(pkt, firstHearers, getRelayName, onTap = { selectedPacket = pkt })
                        HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    selectedPacket?.let { pkt ->
        PacketDetailDialog(pkt, getRelayName, state.relayNodeStats) { selectedPacket = null }
    }
}

@Composable
private fun PacketRow(
    pkt: PacketInfo,
    firstHearers: Map<Int, String>,
    getRelayName: (Int) -> String,
    onTap: () -> Unit,
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
    val fullRelayName = if (pkt.relayNode > 0) getRelayName(pkt.relayNode) else "-"
    val relayName = fullRelayName.substringBefore("/").take(6)
    val gateway = pkt.gatewayName
    val firstMarker = if (isFirstHearer) "\u2605" else ""

    Row(
        modifier = Modifier
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 4.dp),
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

@Composable
private fun PacketDetailDialog(
    pkt: PacketInfo,
    getRelayName: (Int) -> String,
    relayNodeStats: Map<Int, Map<Int, RelayNodeStats>>,
    onDismiss: () -> Unit,
) {
    val hopCount = if (pkt.hopStart > 0) pkt.hopStart - pkt.hopLimit else -1
    val relayNameFull = if (pkt.relayNode > 0) getRelayName(pkt.relayNode) else "-"
    val relayHex = if (pkt.relayNode > 0) String.format("0x%02x", pkt.relayNode) else "-"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Packet Details",
                color = HeaderBlue,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DetailRow("Packet ID", String.format("0x%08x", pkt.packetId))
                DetailRow("Time", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault()).format(pkt.timestamp))
                DetailRow("From", "${pkt.fromName} (${String.format("!%08x", pkt.fromId)})")
                DetailRow("To", String.format("!%08x", pkt.toId))
                DetailRow("Gateway", "${pkt.gatewayName} (${pkt.gatewayId})")
                DetailRow("Port", "${pkt.portnumName} (${pkt.portnum})")
                DetailRow("RSSI", pkt.rssi?.let { "%.0f dBm".format(it) } ?: "-")
                DetailRow("SNR", pkt.snr?.let { "%.1f dB".format(it) } ?: "-")
                DetailRow("Hop Start", if (pkt.hopStart > 0) "${pkt.hopStart}" else "-")
                DetailRow("Hop Limit", if (pkt.hopStart > 0) "${pkt.hopLimit}" else "-")
                DetailRow("Hop Count", if (hopCount >= 0) "$hopCount" else "-")
                DetailRow("Relay Hex", relayHex)
                DetailRow("Relay Node", relayNameFull)
                DetailRow("Want Resp", if (pkt.wantResponse) "Yes" else "No")

                if (pkt.relayNode > 0) {
                    RelayCandidatesSection(pkt.relayNode, relayNodeStats)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = FirstHearerCyan, fontFamily = Mono)
            }
        },
    )
}

@Composable
internal fun RelayCandidatesSection(
    relayByte: Int,
    relayNodeStats: Map<Int, Map<Int, RelayNodeStats>>,
) {
    val candidates = relayNodeStats[relayByte] ?: return
    if (candidates.isEmpty()) return

    val currentTime = remember { System.currentTimeMillis() }
    val sorted = remember(candidates) {
        candidates.values.sortedByDescending { it.score(currentTime) }
    }
    val bestNodeId = sorted.first().nodeId
    val fullTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = Color(0xFF333355))
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Relay Candidates (0x${String.format("%02x", relayByte)})",
        color = HeaderBlue,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
    Spacer(modifier = Modifier.height(6.dp))

    sorted.forEach { c ->
        val isBest = c.nodeId == bestNodeId
        val nameColor = if (isBest) DirectGreen else Color(0xFFE0E0E0)
        val score = c.score(currentTime)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Name + score line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${c.shortName} (${String.format("!%08x", c.nodeId)})" +
                            if (isBest) " \u2605" else "",
                    color = nameColor,
                    fontFamily = Mono,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                )
                Text(
                    text = "score: ${"%.0f".format(score)}",
                    color = if (isBest) DirectGreen else TextDim,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                )
            }
            // Stats line
            val avgHops = c.avgDirectHops?.let { "%.1f".format(it) } ?: "-"
            val dRssi = c.avgDirectRssi?.let { "%.0f".format(it) } ?: "-"
            val rRssi = c.avgRelayRssi?.let { "%.0f".format(it) } ?: "-"
            val rSnr = c.avgRelaySnr?.let { "%.1f".format(it) } ?: "-"
            Text(
                text = "dir:${c.directPacketCount} rly:${c.relayCount} hops:$avgHops dRSSI:$dRssi rRSSI:$rRssi rSNR:$rSnr",
                color = TextDim,
                fontFamily = Mono,
                fontSize = 10.sp,
            )
            // Timestamps
            val lastDirect = c.lastSeenDirect?.let { fullTimeFormatter.format(Instant.ofEpochMilli(it)) } ?: "-"
            val lastRelay = c.lastSeenRelay?.let { fullTimeFormatter.format(Instant.ofEpochMilli(it)) } ?: "-"
            Text(
                text = "last dir:$lastDirect  last rly:$lastRelay",
                color = TextDim,
                fontFamily = Mono,
                fontSize = 10.sp,
            )
        }
        if (c != sorted.last()) {
            HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = TextDim,
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            color = Color(0xFFE0E0E0),
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
