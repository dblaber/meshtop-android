package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.GatewayStats
import com.meshtop.data.MonitorUiState
import com.meshtop.data.PORTNUM_NAMES
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun GatewaysScreen(
    state: MonitorUiState,
    hideGateways: Boolean = false,
    hideMyNodes: Boolean = false,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
) {
    val gateways = remember(state.gateways, state.hexToNodeId, hideGateways, hideMyNodes, searchQuery) {
        state.gateways
            .filter { gw ->
                searchQuery.isEmpty() ||
                gw.shortName.contains(searchQuery, ignoreCase = true) ||
                gw.longName.contains(searchQuery, ignoreCase = true) ||
                gw.gatewayId.contains(searchQuery, ignoreCase = true) ||
                (state.hexToNodeId[gw.gatewayId]
                    ?.let { String.format("%02x", it and 0xFF) }
                    ?.contains(searchQuery, ignoreCase = true) == true)
            }
            .sortedByDescending { it.filteredCount(hideGateways, hideMyNodes) }
    }
    val scrollState = rememberScrollState()
    var selectedGateway by remember { mutableStateOf<GatewayStats?>(null) }

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
                HeaderCell("Filt", 48.dp)
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
                        GatewayRow(gw, state.hexToNodeId, index, hideGateways, hideMyNodes, onTap = { selectedGateway = gw })
                        HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    selectedGateway?.let { gw ->
        GatewayDetailDialog(gw, state) { selectedGateway = null }
    }
}

@Composable
private fun GatewayRow(
    gw: GatewayStats,
    hexToNodeId: Map<String, Int>,
    index: Int,
    hideGw: Boolean,
    hideMyNode: Boolean,
    onTap: () -> Unit,
) {
    val name = gw.shortName.ifEmpty { gw.gatewayId.take(10) }
    val lastByte = hexToNodeId[gw.gatewayId]?.let { String.format("%02x", it and 0xFF) } ?: "-"
    val rssiStr = gw.avgRssi(hideGw, hideMyNode)?.let { "%.0f".format(it) } ?: "-"
    val snrStr = gw.avgSnr(hideGw, hideMyNode)?.let { "%.1f".format(it) } ?: "-"
    val lastSeen = gw.lastSeen?.let { timeFormatter.format(it) } ?: "-"
    val nodeCount = gw.uniqueNodes.size.toString()
    val filteredCount = gw.filteredCount(hideGw, hideMyNode)

    val rowColor = if (index % 2 == 0) Color(0xFF12122A) else Color.Transparent

    Row(
        modifier = Modifier
            .clickable(onClick = onTap)
            .background(rowColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        DataCell(name, 80.dp, FirstHearerCyan, FontWeight.Bold)
        DataCell(lastByte, 40.dp)
        DataCell("${gw.packetCount}", 48.dp)
        DataCell("${gw.totalDirectCount}", 48.dp, DirectGreen)
        DataCell("${gw.totalRelayedCount}", 48.dp, RelayOrange)
        DataCell("$filteredCount", 48.dp, FirstHearerCyan, FontWeight.Bold)
        DataCell(rssiStr, 48.dp)
        DataCell(snrStr, 45.dp)
        DataCell(nodeCount, 48.dp, StatMagenta)
        DataCell(lastSeen, 50.dp, TextDim)
    }
}

@Composable
private fun GatewayDetailDialog(
    gw: GatewayStats,
    state: MonitorUiState,
    onDismiss: () -> Unit,
) {
    val fullTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    val hexNodeId = state.hexToNodeId[gw.gatewayId]?.let { String.format("!%08x", it) } ?: "-"

    // Compute analytics from recent packets filtered by this gateway
    val gwPackets = remember(state.recentPackets, gw.gatewayId) {
        state.recentPackets.filter { it.gatewayId == gw.gatewayId }
    }
    val gwMessages = remember(state.recentMessages, gw.gatewayId) {
        state.recentMessages.filter { it.gatewayId == gw.gatewayId }
    }

    // Port type breakdown
    val portBreakdown = remember(gwPackets) {
        gwPackets.groupBy { it.portnumName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
    }

    // Hop distribution
    val hopDistribution = remember(gwPackets) {
        val hops = mutableMapOf<Int, Int>()
        for (pkt in gwPackets) {
            val h = if (pkt.hopStart > 0) pkt.hopStart - pkt.hopLimit else -1
            if (h >= 0) hops[h] = (hops[h] ?: 0) + 1
        }
        hops.entries.sortedBy { it.key }
    }

    // Top nodes
    val topNodes = remember(gwPackets) {
        gwPackets.groupBy { it.fromId }
            .mapValues { entry -> entry.value.size to entry.value.first().fromName }
            .entries.sortedByDescending { it.value.first }
            .take(10)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Gateway Details",
                color = HeaderBlue,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // --- Header section ---
                GwDetailRow("Name", "${gw.shortName.ifEmpty { "-" }} / ${gw.longName.ifEmpty { "-" }}")
                GwDetailRow("Gateway ID", gw.gatewayId)
                GwDetailRow("Node ID", hexNodeId)
                GwDetailRow("Last Seen", gw.lastSeen?.let { fullTimeFormatter.format(it) } ?: "-")

                GwSectionHeader("Packet Stats")
                GwDetailRow("Total", "${gw.packetCount}")
                GwDetailRow("Messages", "${gw.messageCount}")
                GwDetailRow("Total Dir", "${gw.totalDirectCount}")
                GwDetailRow("Total Rly", "${gw.totalRelayedCount}")
                GwDetailRow("Filtered", "${gw.filteredCount(false, false)}")
                GwDetailRow("Avg RSSI", gw.avgRssi()?.let { "%.1f dBm".format(it) } ?: "-")
                GwDetailRow("Avg SNR", gw.avgSnr()?.let { "%.1f dB".format(it) } ?: "-")
                GwDetailRow("Uniq Nodes", "${gw.uniqueNodes.size}")

                if (portBreakdown.isNotEmpty()) {
                    GwSectionHeader("Packet Types")
                    portBreakdown.forEach { (port, count) ->
                        GwDetailRow(port, "$count")
                    }
                }

                if (hopDistribution.isNotEmpty()) {
                    GwSectionHeader("Hop Distribution")
                    hopDistribution.forEach { (hops, count) ->
                        val color = when (hops) {
                            0 -> DirectGreen
                            1 -> Color(0xFFE0E0E0)
                            else -> RelayOrange
                        }
                        GwDetailRow("$hops hops", "$count", valueColor = color)
                    }
                }

                if (topNodes.isNotEmpty()) {
                    GwSectionHeader("Top Nodes")
                    topNodes.forEach { entry ->
                        val (count, name) = entry.value
                        GwDetailRow(name.take(12), "$count pkts")
                    }
                }

                if (gwMessages.isNotEmpty()) {
                    GwSectionHeader("Recent Messages")
                    gwMessages.take(10).forEach { msg ->
                        val time = shortTimeFormatter.format(msg.timestamp)
                        val text = msg.text.replace('\n', ' ').take(40)
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = msg.fromName.take(8),
                                    color = FirstHearerCyan,
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                )
                                Text(
                                    text = time,
                                    color = TextDim,
                                    fontFamily = Mono,
                                    fontSize = 11.sp,
                                )
                            }
                            Text(
                                text = text,
                                color = Color(0xFFE0E0E0),
                                fontFamily = Mono,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
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
private fun GwSectionHeader(title: String) {
    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(color = Color(0xFF333355))
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = title,
        color = HeaderBlue,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun GwDetailRow(label: String, value: String, valueColor: Color = Color(0xFFE0E0E0)) {
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
            color = valueColor,
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
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
