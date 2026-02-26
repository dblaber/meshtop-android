package com.meshtop.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())
    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

@Serializable
data class SignalBucket(
    var packetCount: Int = 0,
    var rssiSum: Double = 0.0,
    var rssiCount: Int = 0,
    var snrSum: Double = 0.0,
    var snrCount: Int = 0,
) {
    fun addPacket(rssi: Float?, snr: Float?) {
        packetCount++
        rssi?.let { rssiSum += it; rssiCount++ }
        snr?.let { snrSum += it; snrCount++ }
    }
}

val PORTNUM_NAMES = mapOf(
    0 to "UNKNOWN",
    1 to "TEXT_MSG",
    2 to "REMOTE_HW",
    3 to "POSITION",
    4 to "NODEINFO",
    5 to "ROUTING",
    6 to "ADMIN",
    7 to "TEXT_COMP",
    8 to "WAYPOINT",
    32 to "REPLY",
    33 to "IP_TUNNEL",
    34 to "PAXCOUNT",
    64 to "SERIAL",
    65 to "STORE_FWD",
    66 to "RANGE_TST",
    67 to "TELEMETRY",
    68 to "ZPS",
    69 to "SIMULATOR",
    70 to "TRACEROUT",
    71 to "NEIGHBOR",
    72 to "ATAK_FWD",
    73 to "MAP_RPT",
    256 to "PRIVATE",
    257 to "ATAK_PLG",
)

@Serializable
data class GatewayStats(
    val gatewayId: String,
    var shortName: String = "",
    var longName: String = "",
    var packetCount: Int = 0,
    var messageCount: Int = 0,
    var totalDirectCount: Int = 0,
    var totalRelayedCount: Int = 0,
    val signalAll: SignalBucket = SignalBucket(),
    val signalGw: SignalBucket = SignalBucket(),
    val signalMyNode: SignalBucket = SignalBucket(),
    val uniqueNodes: MutableSet<Int> = mutableSetOf(),
    @Serializable(with = InstantSerializer::class)
    var lastSeen: Instant? = null,
) {
    fun filteredCount(hideGw: Boolean, hideMyNode: Boolean): Int {
        var c = signalAll.packetCount
        if (hideGw) c -= signalGw.packetCount
        if (hideMyNode) c -= signalMyNode.packetCount
        return c.coerceAtLeast(0)
    }

    fun avgRssi(hideGw: Boolean = false, hideMyNode: Boolean = false): Double? {
        var sum = signalAll.rssiSum
        var count = signalAll.rssiCount
        if (hideGw) { sum -= signalGw.rssiSum; count -= signalGw.rssiCount }
        if (hideMyNode) { sum -= signalMyNode.rssiSum; count -= signalMyNode.rssiCount }
        return if (count > 0) sum / count else null
    }

    fun avgSnr(hideGw: Boolean = false, hideMyNode: Boolean = false): Double? {
        var sum = signalAll.snrSum
        var count = signalAll.snrCount
        if (hideGw) { sum -= signalGw.snrSum; count -= signalGw.snrCount }
        if (hideMyNode) { sum -= signalMyNode.snrSum; count -= signalMyNode.snrCount }
        return if (count > 0) sum / count else null
    }
}

@Serializable
data class NodeStats(
    val nodeId: Int,
    var shortName: String = "",
    var packetCount: Int = 0,
    var directCount: Int = 0,
    var modernRelayCount: Int = 0,
    var legacyRelayCount: Int = 0,
    var relayForOthers: Int = 0,
    var messageCount: Int = 0,
    var rssiSum: Double = 0.0,
    var rssiCount: Int = 0,
    var snrSum: Double = 0.0,
    var snrCount: Int = 0,
    var hopSum: Int = 0,
    var hopCount: Int = 0,
    val signalAll: SignalBucket = SignalBucket(),
    val signalGw: SignalBucket = SignalBucket(),
    val signalMyNode: SignalBucket = SignalBucket(),
    val gatewaysHeardBy: MutableSet<String> = mutableSetOf(),
    @Serializable(with = InstantSerializer::class)
    var lastSeen: Instant? = null,
) {
    val avgHops: Double? get() = if (hopCount > 0) hopSum.toDouble() / hopCount else null

    fun avgRssi(hideGw: Boolean = false, hideMyNode: Boolean = false): Double? {
        var sum = signalAll.rssiSum
        var count = signalAll.rssiCount
        if (hideGw) { sum -= signalGw.rssiSum; count -= signalGw.rssiCount }
        if (hideMyNode) { sum -= signalMyNode.rssiSum; count -= signalMyNode.rssiCount }
        return if (count > 0) sum / count else null
    }

    fun avgSnr(hideGw: Boolean = false, hideMyNode: Boolean = false): Double? {
        var sum = signalAll.snrSum
        var count = signalAll.snrCount
        if (hideGw) { sum -= signalGw.snrSum; count -= signalGw.snrCount }
        if (hideMyNode) { sum -= signalMyNode.snrSum; count -= signalMyNode.snrCount }
        return if (count > 0) sum / count else null
    }
}

@Serializable
data class PacketInfo(
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val fromId: Int,
    val fromName: String,
    val toId: Int,
    val gatewayId: String,
    val gatewayName: String,
    val portnum: Int,
    val portnumName: String,
    val rssi: Float?,
    val snr: Float?,
    val hopStart: Int,
    val hopLimit: Int,
    val packetId: Int,
    val wantResponse: Boolean = false,
    val relayNode: Int = 0,
)

@Serializable
data class MessageInfo(
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val fromId: Int,
    val fromName: String,
    val toId: Int,
    val toName: String,
    val gatewayId: String,
    val gatewayName: String,
    val text: String,
    val rssi: Float?,
    val snr: Float?,
    val channel: String,
    val hopStart: Int = 0,
    val hopLimit: Int = 0,
    val relayNode: Int = 0,
    val packetId: Int = 0,
)

@Serializable
data class RelayNodeStats(
    val nodeId: Int,
    val shortName: String,
    var directPacketCount: Int = 0,
    var directRssiSum: Double = 0.0,
    var directRssiCount: Int = 0,
    var directSnrSum: Double = 0.0,
    var directSnrCount: Int = 0,
    var directHopSum: Int = 0,
    var directHopCount: Int = 0,
    var zeroHopCount: Int = 0,
    var relayCount: Int = 0,
    var relayRssiSum: Double = 0.0,
    var relayRssiCount: Int = 0,
    var relaySnrSum: Double = 0.0,
    var relaySnrCount: Int = 0,
    var lastSeenDirect: Long? = null,
    var lastSeenRelay: Long? = null,
) {
    val avgDirectHops: Double?
        get() = if (directHopCount > 0) directHopSum.toDouble() / directHopCount else null

    val avgDirectRssi: Double?
        get() = if (directRssiCount > 0) directRssiSum / directRssiCount else null

    val avgRelayRssi: Double?
        get() = if (relayRssiCount > 0) relayRssiSum / relayRssiCount else null

    val avgRelaySnr: Double?
        get() = if (relaySnrCount > 0) relaySnrSum / relaySnrCount else null

    fun score(currentTime: Long): Double {
        var s = 0.0

        // Never seen directly — heavy penalty, only allow if strong relay evidence
        if (directPacketCount == 0) {
            if (relayCount < 5 || lastSeenRelay == null) return -2000.0
            if ((currentTime - lastSeenRelay!!) > 300_000) return -2000.0
            s = -500.0
        }

        // 0-hop observations are the strongest signal (malla approach):
        // a node received at 0 hops is local and very likely a relay candidate
        if (zeroHopCount > 0) {
            s += 1000.0 + minOf(zeroHopCount * 20.0, 500.0)
        }

        // Hop distance — disqualify far-away nodes, reward local ones
        val avgHops = avgDirectHops
        if (avgHops != null) {
            s += when {
                avgHops >= 3.0 -> return -1500.0
                avgHops >= 2.0 -> -800.0
                avgHops >= 1.5 -> -400.0
                avgHops >= 1.0 -> -100.0
                avgHops <= 0.5 -> 300.0
                else -> 100.0
            }
        }

        // Relay observations — capped to prevent runaway accumulation
        if (relayCount > 0) {
            s += minOf(relayCount * 20.0, 400.0)
            lastSeenRelay?.let { lsr ->
                val ageMinutes = (currentTime - lsr) / 60_000.0
                s += when {
                    ageMinutes < 5 -> 100.0
                    ageMinutes < 15 -> 50.0
                    ageMinutes < 60 -> 20.0
                    else -> 0.0
                }
            }
        }

        // Direct packet activity
        if (directPacketCount > 0) {
            s += 50.0 + minOf(directPacketCount * 2.0, 100.0)
            lastSeenDirect?.let { lsd ->
                val ageMinutes = (currentTime - lsd) / 60_000.0
                s += when {
                    ageMinutes < 5 -> 30.0
                    ageMinutes < 15 -> 15.0
                    ageMinutes < 60 -> 5.0
                    ageMinutes > 180 -> -50.0
                    else -> 0.0
                }
            }
        }

        // Signal quality — minor tiebreaker
        avgRelayRssi?.let { rssi ->
            val normalized = ((rssi + 120) / 90.0).coerceIn(0.0, 1.0)
            s += normalized * 10.0
        }

        avgRelaySnr?.let { snr ->
            val normalized = ((snr + 20) / 40.0).coerceIn(0.0, 1.0)
            s += normalized * 5.0
        }

        return s
    }

    fun matchesSignal(rssi: Float?, threshold: Float = 15f): Boolean {
        val avgDirect = avgDirectRssi ?: return true
        val r = rssi ?: return true
        return kotlin.math.abs(avgDirect - r) < threshold
    }
}

data class ConnectionSettings(
    val broker: String = "",
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val topic: String = "msh/US/2/e/LongFast/#",
    val myNodes: Set<String> = emptySet(),
    val persistSession: Boolean = false,
    // Optional PostgreSQL settings (empty = disabled)
    val dbHost: String = "",
    val dbPort: Int = 5432,
    val dbName: String = "",
    val dbUser: String = "",
    val dbPassword: String = "",
) {
    val dbEnabled: Boolean get() = dbHost.isNotBlank() && dbName.isNotBlank()
}

data class MonitorUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val connectionError: String? = null,
    val totalPackets: Int = 0,
    val decryptedPackets: Int = 0,
    val failedDecryptions: Int = 0,
    val uptimeMillis: Long = 0,
    val gateways: List<GatewayStats> = emptyList(),
    val myNodes: Map<String, NodeStats> = emptyMap(),
    val recentPackets: List<PacketInfo> = emptyList(),
    val recentMessages: List<MessageInfo> = emptyList(),
    val relayNodeStats: Map<Int, Map<Int, RelayNodeStats>> = emptyMap(),
    val hexToNodeId: Map<String, Int> = emptyMap(),
    val gatewayNodeIds: Set<Int> = emptySet(),
    val myNodeIds: Set<Int> = emptySet(),
    val dbConfigured: Boolean = false,
    val dbConnecting: Boolean = false,
    val dbLoaded: Boolean = false,
    val dbError: String? = null,
    val dbNodeCount: Int = 0,
)
