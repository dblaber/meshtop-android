package com.meshtop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MessageInfo
import com.meshtop.data.MonitorUiState
import com.meshtop.data.PacketInfo
import com.meshtop.ui.theme.*

// Colors for packet type bars
private val PortnumColors = mapOf(
    "TEXT_MSG" to StatMagenta,
    "POSITION" to DirectGreen,
    "NODEINFO" to FirstHearerCyan,
    "TELEMETRY" to WarningYellow,
    "ROUTING" to RelayOrange,
    "TRACEROUT" to Color(0xFF90CAF9),
    "NEIGHBOR" to MultiGatewayCyan,
    "STORE_FWD" to Color(0xFFFFAB91),
    "MAP_RPT" to Color(0xFFA5D6A7),
    "ADMIN" to Color(0xFFEF9A9A),
)
private val DefaultBarColor = TextDim

@Composable
fun SummaryScreen(
    state: MonitorUiState,
    hideGateways: Boolean = false,
    hideMyNodes: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val excludedIds by remember(hideGateways, hideMyNodes, state.gatewayNodeIds, state.myNodeIds) {
        derivedStateOf {
            buildSet {
                if (hideGateways) addAll(state.gatewayNodeIds)
                if (hideMyNodes) addAll(state.myNodeIds)
            }
        }
    }

    val filteredPackets by remember(state.recentPackets, excludedIds) {
        derivedStateOf {
            if (excludedIds.isEmpty()) state.recentPackets
            else state.recentPackets.filter { it.fromId !in excludedIds }
        }
    }
    val filteredMessages by remember(state.recentMessages, excludedIds) {
        derivedStateOf {
            if (excludedIds.isEmpty()) state.recentMessages
            else state.recentMessages.filter { it.fromId !in excludedIds }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { NetworkOverview(state, filteredPackets, filteredMessages) }
        item { PacketTypesChart(filteredPackets) }
        item { SignalQualityChart(filteredPackets) }
        item { HopDistributionChart(filteredPackets) }
        item { TopGatewaysChart(state, hideGateways, hideMyNodes) }
        item { TopNodesChart(filteredPackets) }
    }
}

// ── Shared composables ──────────────────────────────────────────────

@Composable
private fun SummaryCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = HeaderBlue,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SurfaceDark, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 10.sp,
            color = TextDim,
        )
    }
}

@Composable
private fun HorizontalBarRow(
    label: String,
    value: Int,
    maxValue: Int,
    barColor: Color,
    suffix: String = "",
) {
    val fraction = if (maxValue > 0) value.toFloat() / maxValue else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = 12.sp,
            color = Color(0xFFE0E0E0),
            modifier = Modifier.width(90.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(16.dp),
        ) {
            val cr = CornerRadius(4.dp.toPx())
            // Background track
            drawRoundRect(
                color = SurfaceDark,
                size = size,
                cornerRadius = cr,
            )
            // Fill bar
            if (fraction > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = cr,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$value$suffix",
            fontFamily = Mono,
            fontSize = 11.sp,
            color = Color(0xFFE0E0E0),
            modifier = Modifier.width(52.dp),
        )
    }
}

// ── Section 1: Network Overview ─────────────────────────────────────

@Composable
private fun NetworkOverview(
    state: MonitorUiState,
    packets: List<PacketInfo>,
    messages: List<MessageInfo>,
) {
    val uniqueNodes = packets.map { it.fromId }.toSet().size
    val activeGateways = state.gateways.size
    val msgCount = messages.size
    val total = state.totalPackets
    val decrypted = state.decryptedPackets
    val decryptRate = if (total > 0) "%.0f%%".format(decrypted * 100.0 / total) else "—"

    SummaryCard(title = "Network Overview") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard("Nodes", "$uniqueNodes", FirstHearerCyan, Modifier.weight(1f))
            MetricCard("Gateways", "$activeGateways", DirectGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard("Messages", "$msgCount", StatMagenta, Modifier.weight(1f))
            MetricCard("Decrypt %", decryptRate, WarningYellow, Modifier.weight(1f))
        }
    }
}

// ── Section 2: Packet Types ─────────────────────────────────────────

@Composable
private fun PacketTypesChart(packets: List<PacketInfo>) {
    val counts = packets
        .groupingBy { it.portnumName }
        .eachCount()
        .entries
        .sortedByDescending { it.value }

    SummaryCard(title = "Packet Types") {
        if (counts.isEmpty()) {
            Text("No packets yet", fontFamily = Mono, fontSize = 12.sp, color = TextDim)
        } else {
            val maxCount = counts.first().value
            counts.forEach { (name, count) ->
                HorizontalBarRow(
                    label = name,
                    value = count,
                    maxValue = maxCount,
                    barColor = PortnumColors[name] ?: DefaultBarColor,
                )
            }
        }
    }
}

// ── Section 3: Signal Quality (RSSI) ────────────────────────────────

@Composable
private fun SignalQualityChart(packets: List<PacketInfo>) {
    val rssiValues = packets.mapNotNull { it.rssi?.toInt() }

    val excellent = rssiValues.count { it > -90 }
    val good = rssiValues.count { it in -105..-90 }
    val fair = rssiValues.count { it in -115..-106 }
    val poor = rssiValues.count { it < -115 }
    val maxVal = maxOf(excellent, good, fair, poor, 1)

    SummaryCard(title = "Signal Quality (RSSI)") {
        if (rssiValues.isEmpty()) {
            Text("No signal data yet", fontFamily = Mono, fontSize = 12.sp, color = TextDim)
        } else {
            HorizontalBarRow("Excellent", excellent, maxVal, DirectGreen, suffix = " >-90")
            HorizontalBarRow("Good", good, maxVal, FirstHearerCyan, suffix = "")
            HorizontalBarRow("Fair", fair, maxVal, WarningYellow, suffix = "")
            HorizontalBarRow("Poor", poor, maxVal, ErrorRed, suffix = " <-115")
        }
    }
}

// ── Section 4: Hop Distribution ─────────────────────────────────────

@Composable
private fun HopDistributionChart(packets: List<PacketInfo>) {
    val hops = packets.map { (it.hopStart - it.hopLimit).coerceAtLeast(0) }

    val direct = hops.count { it == 0 }
    val hop1 = hops.count { it == 1 }
    val hop2 = hops.count { it == 2 }
    val hop3plus = hops.count { it >= 3 }
    val maxVal = maxOf(direct, hop1, hop2, hop3plus, 1)
    val directPct = if (hops.isNotEmpty()) "%.0f%%".format(direct * 100.0 / hops.size) else "—"

    SummaryCard(title = "Hop Distribution") {
        if (hops.isEmpty()) {
            Text("No hop data yet", fontFamily = Mono, fontSize = 12.sp, color = TextDim)
        } else {
            HorizontalBarRow("Direct", direct, maxVal, DirectGreen)
            HorizontalBarRow("1 hop", hop1, maxVal, FirstHearerCyan)
            HorizontalBarRow("2 hops", hop2, maxVal, WarningYellow)
            HorizontalBarRow("3+ hops", hop3plus, maxVal, ErrorRed)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$directPct direct",
                fontFamily = Mono,
                fontSize = 11.sp,
                color = TextDim,
            )
        }
    }
}

// ── Section 5: Top Gateways ─────────────────────────────────────────

@Composable
private fun TopGatewaysChart(state: MonitorUiState, hideGw: Boolean = false, hideMyNode: Boolean = false) {
    val top = state.gateways
        .sortedByDescending { it.filteredCount(hideGw, hideMyNode) }
        .take(5)

    SummaryCard(title = "Top Gateways") {
        if (top.isEmpty()) {
            Text("No gateways yet", fontFamily = Mono, fontSize = 12.sp, color = TextDim)
        } else {
            val maxPkts = top.first().filteredCount(hideGw, hideMyNode).coerceAtLeast(1)
            top.forEach { gw ->
                val name = gw.shortName.ifBlank { gw.gatewayId.takeLast(4) }
                val count = gw.filteredCount(hideGw, hideMyNode)
                HorizontalBarRow(
                    label = name,
                    value = count,
                    maxValue = maxPkts,
                    barColor = HeaderBlue,
                    suffix = " (${gw.uniqueNodes.size}n)",
                )
            }
        }
    }
}

// ── Section 6: Top Nodes (Recent) ───────────────────────────────────

@Composable
private fun TopNodesChart(packets: List<PacketInfo>) {
    val counts = packets
        .groupingBy { it.fromName.ifBlank { "!%08x".format(it.fromId) } }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(10)

    SummaryCard(title = "Top Nodes (Recent)") {
        if (counts.isEmpty()) {
            Text("No nodes yet", fontFamily = Mono, fontSize = 12.sp, color = TextDim)
        } else {
            val maxCount = counts.first().value
            counts.forEach { (name, count) ->
                HorizontalBarRow(
                    label = name,
                    value = count,
                    maxValue = maxCount,
                    barColor = MultiGatewayCyan,
                )
            }
        }
    }
}
