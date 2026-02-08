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
import com.meshtop.data.NodeStats
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun MyNodesScreen(state: MonitorUiState, modifier: Modifier = Modifier) {
    val nodes = state.myNodes.entries
        .sortedBy { it.key }
        .map { (key, node) -> key to node }
    val scrollState = rememberScrollState()
    var selectedNode by remember { mutableStateOf<Pair<String, NodeStats>?>(null) }

    val nodeNames = if (nodes.isNotEmpty()) {
        nodes.joinToString(", ") { it.second.shortName.ifEmpty { it.first } }
    } else "None configured"

    Column(modifier = modifier.fillMaxSize()) {
        SectionDescription(
            title = "My Nodes",
            description = "Tracked nodes: $nodeNames. Dir=direct, Mod=modern relay, Leg=legacy relay, RFor=relayed for others."
        )

        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            // Header
            Row(
                modifier = Modifier
                    .background(SurfaceCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                HeaderCell("Node", 50.dp)
                HeaderCell("Byte", 40.dp)
                HeaderCell("Tot", 45.dp)
                HeaderCell("Dir", 45.dp)
                HeaderCell("Mod", 45.dp)
                HeaderCell("Leg", 45.dp)
                HeaderCell("RFor", 45.dp)
                HeaderCell("aRSSI", 50.dp)
                HeaderCell("aSNR", 48.dp)
                HeaderCell("aHop", 45.dp)
                HeaderCell("GWs", 38.dp)
                HeaderCell("Last", 50.dp)
            }

            HorizontalDivider(color = Color(0xFF333355))

            if (nodes.isEmpty()) {
                Text(
                    text = "No nodes configured. Add node short names in Settings.",
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(nodes) { (key, node) ->
                        NodeRow(key, node, onTap = {
                            if (node != null && node.packetCount > 0) {
                                selectedNode = key to node
                            }
                        })
                        HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    selectedNode?.let { (key, node) ->
        NodeDetailDialog(key, node, state) { selectedNode = null }
    }
}

@Composable
private fun NodeRow(key: String, node: NodeStats?, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (node != null && node.packetCount > 0) {
            val lastByte = if (node.nodeId != 0) String.format("%02x", node.nodeId and 0xFF) else "-"
            val rssiStr = node.avgRssi?.let { "%.0f".format(it) } ?: "-"
            val snrStr = node.avgSnr?.let { "%.1f".format(it) } ?: "-"
            val hopsStr = node.avgHops?.let { "%.1f".format(it) } ?: "-"
            val lastSeen = node.lastSeen?.let { timeFormatter.format(it) } ?: "-"
            val gwCount = node.gatewaysHeardBy.size.toString()

            DataCell(node.shortName.ifEmpty { key }.take(4), 50.dp, DirectGreen, FontWeight.Bold)
            DataCell(lastByte, 40.dp)
            DataCell("${node.packetCount}", 45.dp)
            DataCell("${node.directCount}", 45.dp, DirectGreen)
            DataCell("${node.modernRelayCount}", 45.dp, RelayOrange)
            DataCell("${node.legacyRelayCount}", 45.dp, WarningYellow)
            DataCell("${node.relayForOthers}", 45.dp)
            DataCell(rssiStr, 50.dp)
            DataCell(snrStr, 48.dp)
            DataCell(hopsStr, 45.dp)
            DataCell(gwCount, 38.dp, FirstHearerCyan)
            DataCell(lastSeen, 50.dp, TextDim)
        } else {
            DataCell(key.take(4), 50.dp, TextDim)
            DataCell("-", 40.dp, TextDim)
            DataCell("0", 45.dp, TextDim)
            DataCell("0", 45.dp, TextDim)
            DataCell("0", 45.dp, TextDim)
            DataCell("0", 45.dp, TextDim)
            DataCell("0", 45.dp, TextDim)
            DataCell("-", 50.dp, TextDim)
            DataCell("-", 48.dp, TextDim)
            DataCell("-", 45.dp, TextDim)
            DataCell("0", 38.dp, TextDim)
            DataCell("-", 50.dp, TextDim)
        }
    }
}

@Composable
private fun NodeDetailDialog(
    key: String,
    node: NodeStats,
    state: MonitorUiState,
    onDismiss: () -> Unit,
) {
    val fullTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    val hexId = if (node.nodeId != 0) String.format("!%08x", node.nodeId) else "-"
    val lastByte = if (node.nodeId != 0) String.format("0x%02x", node.nodeId and 0xFF) else "-"

    // Filter recent packets/messages for this node
    val nodePackets = remember(state.recentPackets, node.nodeId) {
        state.recentPackets.filter { it.fromId == node.nodeId }
    }
    val nodeMessages = remember(state.recentMessages, node.nodeId) {
        state.recentMessages.filter { it.fromId == node.nodeId }
    }

    // Port type breakdown
    val portBreakdown = remember(nodePackets) {
        nodePackets.groupBy { it.portnumName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
    }

    // Hop distribution
    val hopDistribution = remember(nodePackets) {
        val hops = mutableMapOf<Int, Int>()
        for (pkt in nodePackets) {
            val h = if (pkt.hopStart > 0) pkt.hopStart - pkt.hopLimit else -1
            if (h >= 0) hops[h] = (hops[h] ?: 0) + 1
        }
        hops.entries.sortedBy { it.key }
    }

    // Per-gateway breakdown
    val gatewayBreakdown = remember(nodePackets) {
        nodePackets.groupBy { it.gatewayName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Node Details",
                color = HeaderBlue,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // --- Header ---
                NodeDetailRow("Name", node.shortName.ifEmpty { key })
                NodeDetailRow("Config Key", key)
                NodeDetailRow("Node ID", hexId)
                NodeDetailRow("Last Byte", lastByte)
                NodeDetailRow("Last Seen", node.lastSeen?.let { fullTimeFormatter.format(it) } ?: "-")

                NodeSectionHeader("Packet Stats")
                NodeDetailRow("Total", "${node.packetCount}")
                NodeDetailRow("Direct", "${node.directCount}")
                NodeDetailRow("Mod Relay", "${node.modernRelayCount}")
                NodeDetailRow("Leg Relay", "${node.legacyRelayCount}")
                NodeDetailRow("Rly Others", "${node.relayForOthers}")
                NodeDetailRow("Messages", "${node.messageCount}")

                NodeSectionHeader("Signal Stats")
                NodeDetailRow("Avg RSSI", node.avgRssi?.let { "%.1f dBm".format(it) } ?: "-")
                NodeDetailRow("Avg SNR", node.avgSnr?.let { "%.1f dB".format(it) } ?: "-")
                NodeDetailRow("Avg Hops", node.avgHops?.let { "%.2f".format(it) } ?: "-")

                if (node.gatewaysHeardBy.isNotEmpty()) {
                    NodeSectionHeader("Gateways (${node.gatewaysHeardBy.size})")
                    node.gatewaysHeardBy.sorted().forEach { gwId ->
                        Text(
                            text = gwId,
                            color = FirstHearerCyan,
                            fontFamily = Mono,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }

                if (portBreakdown.isNotEmpty()) {
                    NodeSectionHeader("Packet Types")
                    portBreakdown.forEach { (port, count) ->
                        NodeDetailRow(port, "$count")
                    }
                }

                if (hopDistribution.isNotEmpty()) {
                    NodeSectionHeader("Hop Distribution")
                    hopDistribution.forEach { (hops, count) ->
                        val color = when (hops) {
                            0 -> DirectGreen
                            1 -> Color(0xFFE0E0E0)
                            else -> RelayOrange
                        }
                        NodeDetailRow("$hops hops", "$count", valueColor = color)
                    }
                }

                if (gatewayBreakdown.isNotEmpty()) {
                    NodeSectionHeader("Per Gateway")
                    gatewayBreakdown.forEach { (gw, count) ->
                        NodeDetailRow(gw.take(12), "$count pkts")
                    }
                }

                if (nodeMessages.isNotEmpty()) {
                    NodeSectionHeader("Recent Messages")
                    nodeMessages.take(10).forEach { msg ->
                        val time = shortTimeFormatter.format(msg.timestamp)
                        val text = msg.text.replace('\n', ' ').take(40)
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "\u2192 ${msg.toName.take(8)}",
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
private fun NodeSectionHeader(title: String) {
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
private fun NodeDetailRow(label: String, value: String, valueColor: Color = Color(0xFFE0E0E0)) {
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
