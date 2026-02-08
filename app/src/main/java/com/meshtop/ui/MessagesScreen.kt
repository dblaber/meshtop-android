package com.meshtop.ui

import androidx.compose.foundation.background
cimport androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MessageInfo
import com.meshtop.data.MonitorUiState
import com.meshtop.data.RelayNodeStats
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun MessagesScreen(
    state: MonitorUiState,
    getRelayName: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val messages = state.recentMessages
    var selectedMessage by remember { mutableStateOf<GroupedMessage?>(null) }

    // Group by packetId to show unique messages
    val grouped = remember(messages) {
        val groups = mutableMapOf<Int, MutableList<MessageInfo>>()
        for (msg in messages) {
            groups.getOrPut(msg.packetId) { mutableListOf() }.add(msg)
        }

        val seen = mutableSetOf<Int>()
        val result = mutableListOf<GroupedMessage>()
        for (msg in messages) {
            if (msg.packetId in seen) continue
            seen.add(msg.packetId)
            if (result.size >= 50) break

            val group = groups[msg.packetId] ?: listOf(msg)
            val best = group.minByOrNull {
                val hops = it.hopStart - it.hopLimit
                if (it.hopStart > 0) hops else 999
            } ?: msg

            val gwNames = group.map { it.gatewayName.take(6) }.toSortedSet()

            result.add(
                GroupedMessage(
                    best = best,
                    gatewayNames = gwNames.toList(),
                    gatewayCount = group.size,
                    hops = if (best.hopStart > 0) best.hopStart - best.hopLimit else -1,
                )
            )
        }
        result
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionDescription(
            title = "Text Messages",
            description = "Aggregated messages from all nodes. Grouped by packet ID across gateways."
        )

        if (grouped.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Waiting for messages...",
                    color = TextDim,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(grouped, key = { it.best.packetId }) { gm ->
                    MessageCard(gm, onTap = { selectedMessage = gm })
                }
            }
        }
    }

    selectedMessage?.let { gm ->
        MessageDetailDialog(gm, getRelayName, state.relayNodeStats) { selectedMessage = null }
    }
}

private data class GroupedMessage(
    val best: MessageInfo,
    val gatewayNames: List<String>,
    val gatewayCount: Int,
    val hops: Int,
)

@Composable
private fun MessageCard(gm: GroupedMessage, onTap: () -> Unit) {
    val isDirect = gm.hops == 0
    val multiGw = gm.gatewayCount > 1

    val accentColor = when {
        isDirect -> DirectGreen
        multiGw -> MultiGatewayCyan
        else -> Color(0xFF7986CB)
    }

    val timeStr = timeFormatter.format(gm.best.timestamp)
    val gwStr = gm.gatewayNames.joinToString(", ")
    val hopStr = if (gm.hops >= 0) "${gm.hops} hop${if (gm.hops != 1) "s" else ""}" else ""
    val relayHex = if (gm.best.relayNode > 0) String.format(" relay:%02x", gm.best.relayNode) else ""

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header line: From -> To  |  Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = gm.best.fromName,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = " \u2192 ",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = gm.best.toName,
                        color = Color(0xFFBDBDBD),
                        fontFamily = Mono,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    text = timeStr,
                    color = TextDim,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message text
            Text(
                text = gm.best.text.replace('\n', ' '),
                color = Color(0xFFE8E8E8),
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Footer: GWs, hops, relay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "GW: $gwStr",
                    color = TextDim,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                )
                Text(
                    text = buildString {
                        if (hopStr.isNotEmpty()) append(hopStr)
                        if (relayHex.isNotEmpty()) append(relayHex)
                        if (isDirect) append(" \u2713 direct")
                    },
                    color = if (isDirect) DirectGreen else TextDim,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageDetailDialog(
    gm: GroupedMessage,
    getRelayName: (Int) -> String,
    relayNodeStats: Map<Int, Map<Int, RelayNodeStats>>,
    onDismiss: () -> Unit,
) {
    val msg = gm.best
    val hopCount = if (msg.hopStart > 0) msg.hopStart - msg.hopLimit else -1
    val relayNameFull = if (msg.relayNode > 0) getRelayName(msg.relayNode) else "-"
    val relayHex = if (msg.relayNode > 0) String.format("0x%02x", msg.relayNode) else "-"
    val fullTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Message Details",
                color = HeaderBlue,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MessageDetailRow("Packet ID", String.format("0x%08x", msg.packetId))
                MessageDetailRow("Time", fullTimeFormatter.format(msg.timestamp))
                MessageDetailRow("From", "${msg.fromName} (${String.format("!%08x", msg.fromId)})")
                MessageDetailRow("To", "${msg.toName} (${String.format("!%08x", msg.toId)})")
                MessageDetailRow("Gateway", "${msg.gatewayName} (${msg.gatewayId})")
                MessageDetailRow("Channel", msg.channel.ifEmpty { "-" })
                MessageDetailRow("RSSI", msg.rssi?.let { "%.0f dBm".format(it) } ?: "-")
                MessageDetailRow("SNR", msg.snr?.let { "%.1f dB".format(it) } ?: "-")
                MessageDetailRow("Hop Start", if (msg.hopStart > 0) "${msg.hopStart}" else "-")
                MessageDetailRow("Hop Limit", if (msg.hopStart > 0) "${msg.hopLimit}" else "-")
                MessageDetailRow("Hop Count", if (hopCount >= 0) "$hopCount" else "-")
                MessageDetailRow("Relay Hex", relayHex)
                MessageDetailRow("Relay Node", relayNameFull)
                if (gm.gatewayCount > 1) {
                    MessageDetailRow("Gateways", "${gm.gatewayCount} (${gm.gatewayNames.joinToString(", ")})")
                }

                if (msg.relayNode > 0) {
                    RelayCandidatesSection(msg.relayNode, relayNodeStats)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF333355))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Message",
                    color = HeaderBlue,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    color = Color(0xFFE8E8E8),
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
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
private fun MessageDetailRow(label: String, value: String) {
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
