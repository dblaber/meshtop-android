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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MonitorUiState
import com.meshtop.data.RelayNodeStats
import com.meshtop.ui.theme.*

// One row per relay byte: best candidate (by score) + all candidates for the dialog
private data class RelayRow(
    val lastByte: Int,
    val best: RelayNodeStats,
    val candidates: Map<Int, RelayNodeStats>,
    val maxRelayCount: Int,
)

@Composable
fun RelayNodesScreen(
    state: MonitorUiState,
    hideGateways: Boolean = false,
    hideMyNodes: Boolean = false,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
) {
    // Collapse to one row per relay byte; pick highest-scoring candidate as the identity
    val rows = remember(state.relayNodeStats, state.gatewayNodeIds, state.myNodeIds, state.clientMuteNodeIds, hideGateways, hideMyNodes, searchQuery) {
        val currentTime = System.currentTimeMillis()
        state.relayNodeStats
            .entries
            .mapNotNull { (lastByte, candidates) ->
                // Only show bytes where at least one candidate has been seen relaying
                if (candidates.values.none { it.relayCount > 0 }) return@mapNotNull null
                val best = candidates.values.maxByOrNull { it.score(currentTime) }
                    ?: return@mapNotNull null
                RelayRow(
                    lastByte = lastByte,
                    best = best,
                    candidates = candidates,
                    maxRelayCount = candidates.values.maxOf { it.relayCount },
                )
            }
            // Filters are applied to the winning candidate's nodeId
            .filter { row -> row.best.nodeId !in state.clientMuteNodeIds }
            .filter { row -> !hideGateways || row.best.nodeId !in state.gatewayNodeIds }
            .filter { row -> !hideMyNodes || row.best.nodeId !in state.myNodeIds }
            .filter { row ->
                searchQuery.isEmpty() ||
                row.best.shortName.contains(searchQuery, ignoreCase = true) ||
                "%02x".format(row.lastByte).contains(searchQuery, ignoreCase = true)
            }
            .sortedByDescending { it.maxRelayCount }
    }

    val scrollState = rememberScrollState()
    var selectedLastByte by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SectionDescription(
            title = "Relay Nodes",
            description = "Nodes observed relaying (relay_node field set). Tap row for candidate details."
        )

        Column(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.horizontalScroll(scrollState)) {
                Row(
                    modifier = Modifier
                        .background(SurfaceCard)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    HeaderCell("Node", 72.dp)
                    HeaderCell("Byte", 40.dp)
                    HeaderCell("Relay#", 52.dp)
                    HeaderCell("RlyRSSI", 56.dp)
                    HeaderCell("RlySNR", 50.dp)
                    HeaderCell("Dir#", 48.dp)
                    HeaderCell("DirRSSI", 56.dp)
                }

                HorizontalDivider(color = Color(0xFF333355))

                if (rows.isEmpty()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No relay nodes observed yet",
                            color = TextDim,
                            fontFamily = Mono,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(rows) { index, row ->
                            RelayNodeRow(
                                lastByte = row.lastByte,
                                stats = row.best,
                                relayCount = row.maxRelayCount,
                                index = index,
                                onTap = { selectedLastByte = row.lastByte },
                            )
                            HorizontalDivider(color = Color(0xFF1A1A33), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF333355))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Legacy (no relay field): ",
                color = TextDim,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
            Text(
                text = "${state.legacyRelayTotal} pkts",
                color = RelayOrange,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            state.legacyRelayAvgRssi?.let { rssi ->
                Text(
                    text = "  RSSI ${"%.0f".format(rssi)}",
                    color = rssiColor(rssi),
                    fontFamily = Mono,
                    fontSize = 12.sp,
                )
            }
            state.legacyRelayAvgSnr?.let { snr ->
                Text(
                    text = "  SNR ${"%.1f".format(snr)}",
                    color = snrColor(snr),
                    fontFamily = Mono,
                    fontSize = 12.sp,
                )
            }
        }
    }

    selectedLastByte?.let { lastByte ->
        val candidates = state.relayNodeStats[lastByte]?.values?.toList() ?: emptyList()
        RelayDetailDialog(
            lastByte = lastByte,
            candidates = candidates,
            nodeRoles = state.nodeRoles,
            onDismiss = { selectedLastByte = null },
        )
    }
}

@Composable
private fun RelayNodeRow(
    lastByte: Int,
    stats: RelayNodeStats,
    relayCount: Int,
    index: Int,
    onTap: () -> Unit,
) {
    val rowColor = if (index % 2 == 0) Color(0xFF12122A) else Color.Transparent
    val name = stats.shortName.ifEmpty { "!..%02x".format(lastByte) }
    val byteHex = "%02x".format(lastByte)
    val relayRssiStr = stats.avgRelayRssi?.let { "%.0f".format(it) } ?: "-"
    val relaySnrStr = stats.avgRelaySnr?.let { "%.1f".format(it) } ?: "-"
    val dirRssiStr = stats.avgDirectRssi?.let { "%.0f".format(it) } ?: "-"

    Row(
        modifier = Modifier
            .clickable(onClick = onTap)
            .background(rowColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        DataCell(name, 72.dp, FirstHearerCyan, FontWeight.Bold)
        DataCell(byteHex, 40.dp, TextDim)
        DataCell("$relayCount", 52.dp, RelayOrange, FontWeight.Bold)
        DataCell(relayRssiStr, 56.dp, rssiColor(stats.avgRelayRssi))
        DataCell(relaySnrStr, 50.dp, snrColor(stats.avgRelaySnr))
        DataCell("${stats.directPacketCount}", 48.dp, DirectGreen)
        DataCell(dirRssiStr, 56.dp, rssiColor(stats.avgDirectRssi))
    }
}

@Composable
private fun RelayDetailDialog(
    lastByte: Int,
    candidates: List<RelayNodeStats>,
    nodeRoles: Map<Int, Int>,
    onDismiss: () -> Unit,
) {
    val currentTime = remember { System.currentTimeMillis() }
    val sorted = remember(candidates) {
        candidates.sortedByDescending { it.score(currentTime) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Relay byte: %02x  (%d candidate%s)".format(
                    lastByte, candidates.size, if (candidates.size != 1) "s" else ""
                ),
                color = HeaderBlue,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                sorted.forEachIndexed { idx, stats ->
                    val score = stats.score(currentTime)
                    val isWinner = idx == 0 && score > 0 &&
                            (sorted.size == 1 || score > (sorted.getOrNull(1)?.score(currentTime) ?: Double.MIN_VALUE))
                    val hexId = "!%08x".format(stats.nodeId.toLong() and 0xFFFFFFFFL)
                    val avgDirSnr = if (stats.directSnrCount > 0) stats.directSnrSum / stats.directSnrCount else null
                    val roleInt = nodeRoles[stats.nodeId]
                    val roleStr = if (roleInt != null) roleLabel(roleInt) else "Unknown"

                    if (idx > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFF333355))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Candidate header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isWinner) "★ " else "  ",
                            color = WarningYellow,
                            fontFamily = Mono,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = stats.shortName.ifEmpty { hexId },
                            color = if (isWinner) FirstHearerCyan else Color(0xFFE0E0E0),
                            fontFamily = Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = hexId,
                            color = TextDim,
                            fontFamily = Mono,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    DetailRow(
                        "Score",
                        "%.1f".format(score),
                        color = when {
                            score > 500 -> DirectGreen
                            score > 0 -> Color(0xFFE0E0E0)
                            else -> ErrorRed
                        },
                    )
                    DetailRow("Role", roleStr, color = when (roleInt) {
                        1 -> WarningYellow        // CLIENT_MUTE — can't relay
                        2, 3, 4 -> DirectGreen    // ROUTER / ROUTER_CLIENT / REPEATER
                        else -> TextDim
                    })

                    // Direct stats
                    DetailRow("Direct pkts", "${stats.directPacketCount}")
                    if (stats.zeroHopCount > 0)
                        DetailRow("  0-hop obs", "${stats.zeroHopCount}", color = DirectGreen)
                    stats.avgDirectHops?.let { h ->
                        DetailRow(
                            "  avg hops", "%.2f".format(h),
                            color = when {
                                h < 1.0 -> DirectGreen
                                h >= 3.0 -> ErrorRed
                                else -> Color(0xFFE0E0E0)
                            },
                        )
                    }
                    stats.avgDirectRssi?.let { DetailRow("  dir RSSI", "%.1f dBm".format(it), color = rssiColor(it)) }
                    avgDirSnr?.let { DetailRow("  dir SNR", "%.1f dB".format(it), color = snrColor(it)) }

                    // Relay stats
                    DetailRow("Relay obs", "${stats.relayCount}", color = RelayOrange)
                    stats.avgRelayRssi?.let { DetailRow("  rly RSSI", "%.1f dBm".format(it), color = rssiColor(it)) }
                    stats.avgRelaySnr?.let { DetailRow("  rly SNR", "%.1f dB".format(it), color = snrColor(it)) }

                    // Recency
                    DetailRow("Last direct", formatAge(stats.lastSeenDirect, currentTime), color = TextDim)
                    DetailRow("Last relay", formatAge(stats.lastSeenRelay, currentTime), color = TextDim)
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
private fun DetailRow(label: String, value: String, color: Color = Color(0xFFE0E0E0)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = TextDim,
            fontFamily = Mono,
            fontSize = 11.sp,
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            color = color,
            fontFamily = Mono,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatAge(epochMillis: Long?, currentTime: Long): String {
    epochMillis ?: return "never"
    val ageMs = currentTime - epochMillis
    return when {
        ageMs < 0 -> "just now"
        ageMs < 60_000 -> "${ageMs / 1000}s ago"
        ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
        else -> "${ageMs / 3_600_000}h ago"
    }
}

private fun roleLabel(role: Int): String = when (role) {
    0 -> "Client"
    1 -> "ClientMute"
    2 -> "Router"
    3 -> "RouterClient"
    4 -> "Repeater"
    5 -> "Tracker"
    6 -> "Sensor"
    7 -> "ATAK"
    8 -> "ClientHidden"
    9 -> "LostFound"
    10 -> "ATAKTracker"
    else -> "Role($role)"
}

private fun rssiColor(rssi: Double?): Color = when {
    rssi == null -> TextDim
    rssi > -90.0 -> DirectGreen
    rssi < -110.0 -> ErrorRed
    else -> Color(0xFFE0E0E0)
}

private fun snrColor(snr: Double?): Color = when {
    snr == null -> TextDim
    snr > 5.0 -> DirectGreen
    snr < 0.0 -> ErrorRed
    else -> Color(0xFFE0E0E0)
}
