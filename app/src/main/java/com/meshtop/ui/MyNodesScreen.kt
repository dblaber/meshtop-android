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
                        NodeRow(key, node)
                        HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(key: String, node: NodeStats?) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
