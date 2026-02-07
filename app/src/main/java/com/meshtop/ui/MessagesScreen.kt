package com.meshtop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshtop.data.MessageInfo
import com.meshtop.data.MonitorUiState
import com.meshtop.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun MessagesScreen(state: MonitorUiState, modifier: Modifier = Modifier) {
    val messages = state.recentMessages

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
                    MessageCard(gm)
                }
            }
        }
    }
}

private data class GroupedMessage(
    val best: MessageInfo,
    val gatewayNames: List<String>,
    val gatewayCount: Int,
    val hops: Int,
)

@Composable
private fun MessageCard(gm: GroupedMessage) {
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
        modifier = Modifier.fillMaxWidth(),
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
