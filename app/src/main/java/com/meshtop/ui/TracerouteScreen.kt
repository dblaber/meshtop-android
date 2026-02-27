package com.meshtop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MonitorUiState
import com.meshtop.data.TracerouteInfo
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

// One logical traceroute: path info + all gateway observations for the same packetId
private data class TracerouteGroup(
    val representative: TracerouteInfo,         // path, names, hop info
    val gateways: List<TracerouteInfo>,          // all observations, sorted best RSSI first
)

@Composable
fun TracerouteScreen(
    state: MonitorUiState,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
) {
    // Group by packetId; preserve newest-first order using the earliest timestamp per group
    val groups = remember(state.recentTraceroutes) {
        state.recentTraceroutes
            .reversed()                             // newest first
            .groupBy { it.packetId }
            .map { (_, entries) ->
                // Pick the entry with the best RSSI as representative (most informative)
                val rep = entries.maxByOrNull { it.rssi ?: -999f } ?: entries.first()
                // Sort gateways: best RSSI first
                val sorted = entries.sortedByDescending { it.rssi ?: -999f }
                TracerouteGroup(representative = rep, gateways = sorted)
            }
    }

    val displayedGroups = remember(groups, searchQuery) {
        if (searchQuery.isEmpty()) groups
        else groups.filter { group ->
            val tr = group.representative
            tr.fromName.contains(searchQuery, ignoreCase = true) ||
            tr.toName.contains(searchQuery, ignoreCase = true) ||
            tr.routeNames.any { it.contains(searchQuery, ignoreCase = true) } ||
            tr.routeBackNames.any { it.contains(searchQuery, ignoreCase = true) } ||
            group.gateways.any { it.gatewayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionDescription(
            title = "Traceroutes",
            description = "RouteDiscovery packets (portnum 70) showing the RF path through the mesh, newest first."
        )

        if (displayedGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (groups.isEmpty()) "No traceroutes received yet" else "No matches for \"$searchQuery\"",
                    color = TextDim,
                    fontFamily = Mono,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(displayedGroups) { group ->
                    TracerouteCard(group)
                }
            }
        }
    }
}

@Composable
private fun TracerouteCard(group: TracerouteGroup) {
    val tr = group.representative
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Header row: time, from→to, round-trip badge, hop count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timeFormatter.format(tr.timestamp),
                    color = TextDim,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    modifier = Modifier.width(60.dp),
                )
                Text(
                    text = "${tr.fromName} → ${tr.toName}",
                    color = FirstHearerCyan,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (tr.isRoundTrip) {
                    Text(
                        text = "↔",
                        color = DirectGreen,
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                Text(
                    text = "${tr.forwardHops}h",
                    color = RelayOrange,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Forward path
            val fwdNodes = listOf(tr.fromName) + tr.routeNames + listOf(tr.toName)
            PathRow(label = "Fwd:", nodes = fwdNodes, snrValues = tr.snrTowards)

            // Return path (if present)
            if (tr.isRoundTrip) {
                Spacer(modifier = Modifier.height(2.dp))
                val retNodes = listOf(tr.toName) + tr.routeBackNames + listOf(tr.fromName)
                PathRow(label = "Ret:", nodes = retNodes, snrValues = tr.snrBack)
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF333355), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Gateway observations — one line each
            group.gateways.forEach { obs ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "GW: ",
                        color = TextDim,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = obs.gatewayName.take(8),
                        color = StatMagenta,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        modifier = Modifier.width(64.dp),
                    )
                    obs.rssi?.let { rssi ->
                        Text(
                            text = "RSSI: ${"%.0f".format(rssi)}",
                            color = TextDim,
                            fontFamily = Mono,
                            fontSize = 11.sp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    obs.snr?.let { snr ->
                        Text(
                            text = "SNR: ${snrString(snr)}",
                            color = snrColor(snr),
                            fontFamily = Mono,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PathRow(label: String, nodes: List<String>, snrValues: List<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextDim,
            fontFamily = Mono,
            fontSize = 11.sp,
            modifier = Modifier.width(32.dp),
        )
        for (i in nodes.indices) {
            Text(
                text = nodes[i],
                color = Color(0xFFE0E0E0),
                fontFamily = Mono,
                fontSize = 11.sp,
            )
            if (i < nodes.size - 1) {
                val snr = snrValues.getOrNull(i)
                val arrowLabel = if (snr != null) "→(${snrString(snr)})→" else "→"
                Text(
                    text = arrowLabel,
                    color = if (snr != null) snrColor(snr) else TextDim,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun snrString(snr: Float): String = if (snr >= 0f) "+%.1f".format(snr) else "%.1f".format(snr)

private fun snrColor(snr: Float): Color = when {
    snr > 5f -> DirectGreen
    snr >= 0f -> Color(0xFFE0E0E0)
    else -> ErrorRed
}
