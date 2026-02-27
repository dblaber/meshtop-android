package com.meshtop.data

import android.util.Base64
import android.util.Log
import com.meshtop.proto.MeshProtos
import com.meshtop.proto.MqttProtos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.DriverManager
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class MonitorSnapshot(
    val gateways: Map<String, GatewayStats>,
    val myNodes: Map<String, NodeStats>,
    val nodeNames: Map<Int, String>,
    val nodeLongNames: Map<Int, String>,
    val hexToNodeId: Map<String, Int>,
    val gatewayNodeIds: Set<Int>,
    val myNodeLastBytes: Set<Int>,
    val lastByteToNames: Map<Int, List<String>>,
    val relayNodeStats: Map<Int, Map<Int, RelayNodeStats>>,
    val nodeRoles: Map<Int, Int> = emptyMap(),
    val recentPackets: List<PacketInfo>,
    val recentMessages: List<MessageInfo>,
    val recentTraceroutes: List<TracerouteInfo> = emptyList(),
    val legacyRelayTotal: Int = 0,
    val legacyRelayRssiSum: Double = 0.0,
    val legacyRelayRssiCount: Int = 0,
    val legacyRelaySnrSum: Double = 0.0,
    val legacyRelaySnrCount: Int = 0,
    val totalPackets: Int,
    val decryptedPackets: Int,
    val failedDecryptions: Int,
    val uptimeMillis: Long,
)

private const val TAG = "MeshtasticMonitor"
private const val MAX_PACKETS = 200
private const val MAX_MESSAGES = 100
private const val MAX_TRACEROUTES = 100

class MeshtasticMonitor {

    private val defaultKey: ByteArray =
        Base64.decode("1PG7OiApB1nwvP+rz05pAQ==", Base64.DEFAULT)

    // Data storage (accessed under lock via synchronized)
    private val gateways = mutableMapOf<String, GatewayStats>()
    private val myNodes = mutableMapOf<String, NodeStats>()
    private val nodeNames = mutableMapOf<Int, String>()
    private val nodeLongNames = mutableMapOf<Int, String>()
    private val hexToNodeId = mutableMapOf<String, Int>()
    private val gatewayNodeIds = mutableSetOf<Int>()
    private val myNodeLastBytes = mutableSetOf<Int>()
    private val lastByteToNames = mutableMapOf<Int, MutableList<String>>()
    private val relayNodeStats = mutableMapOf<Int, MutableMap<Int, RelayNodeStats>>()
    private val nodeRoles = mutableMapOf<Int, Int>()
    private val recentPackets = ArrayDeque<PacketInfo>(MAX_PACKETS + 10)
    private val recentMessages = ArrayDeque<MessageInfo>(MAX_MESSAGES + 10)
    private val recentTraceroutes = ArrayDeque<TracerouteInfo>(MAX_TRACEROUTES + 10)

    // Stats
    private var totalPackets = 0
    private var decryptedPackets = 0
    private var failedDecryptions = 0
    private var legacyRelayTotal = 0
    private var legacyRelayRssiSum = 0.0
    private var legacyRelayRssiCount = 0
    private var legacyRelaySnrSum = 0.0
    private var legacyRelaySnrCount = 0
    private var startTime = System.currentTimeMillis()
    private var dbConfigured = false
    private var dbConnecting = false
    private var dbLoaded = false
    private var dbError: String? = null
    private var dbNodeCount = 0

    // MQTT
    private var mqttClient: MqttClient? = null
    private var connected = false
    private var connectionError: String? = null

    // Settings
    private var settings = ConnectionSettings()
    private val myNodesLower: Set<String>
        get() = settings.myNodes.map { it.lowercase() }.toSet()

    // State flow
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    private val isDirty = AtomicBoolean(false)
    private var refreshJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun connect(newSettings: ConnectionSettings) {
        settings = newSettings
        initMyNodes()
        if (newSettings.broker.isBlank()) {
            connectionError = "Broker not configured"
            emitState()
            return
        }
        scope.launch(Dispatchers.IO) {
            doConnect()
        }
        startRefreshLoop()
    }

    fun disconnect() {
        refreshJob?.cancel()
        refreshJob = null
        try {
            mqttClient?.let { client ->
                if (client.isConnected) {
                    client.disconnect()
                }
                client.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Disconnect error", e)
        }
        mqttClient = null
        connected = false
        emitState()
    }

    fun clearAllData() = synchronized(this) {
        gateways.clear()
        myNodes.clear()
        nodeNames.clear()
        nodeLongNames.clear()
        hexToNodeId.clear()
        gatewayNodeIds.clear()
        myNodeLastBytes.clear()
        lastByteToNames.clear()
        relayNodeStats.clear()
        nodeRoles.clear()
        recentPackets.clear()
        recentMessages.clear()
        recentTraceroutes.clear()
        totalPackets = 0
        decryptedPackets = 0
        failedDecryptions = 0
        legacyRelayTotal = 0
        legacyRelayRssiSum = 0.0
        legacyRelayRssiCount = 0
        legacyRelaySnrSum = 0.0
        legacyRelaySnrCount = 0
        startTime = System.currentTimeMillis()
        dbConfigured = false
        dbConnecting = false
        dbLoaded = false
        dbError = null
        dbNodeCount = 0
        isDirty.set(true)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun initMyNodes() {
        myNodeLastBytes.clear()
        // Pre-create entries for tracked nodes (names only, nodeId will be filled from NODEINFO)
        for (name in settings.myNodes) {
            val key = name.lowercase()
            if (key !in myNodes) {
                myNodes[key] = NodeStats(nodeId = 0, shortName = name)
            }
        }
    }

    fun loadNodeNamesFromDb(dbSettings: ConnectionSettings) {
        if (!dbSettings.dbEnabled) {
            synchronized(this) { dbConfigured = false }
            isDirty.set(true)
            return
        }
        synchronized(this) {
            dbConfigured = true
            dbConnecting = true
            dbError = null
        }
        isDirty.set(true)
        scope.launch(Dispatchers.IO) {
            try {
                Class.forName("org.postgresql.Driver")
                val url = "jdbc:postgresql://${dbSettings.dbHost}:${dbSettings.dbPort}/${dbSettings.dbName}"
                val props = java.util.Properties().apply {
                    setProperty("user", dbSettings.dbUser)
                    setProperty("password", dbSettings.dbPassword)
                }
                val conn = DriverManager.getConnection(url, props)

                // Detect optional columns so the query degrades gracefully on older schemas
                val dbMeta = conn.metaData
                val roleColRs = dbMeta.getColumns(null, null, "node_info", "role")
                val hasRoleCol = roleColRs.next()
                roleColRs.close()
                Log.d(TAG, "DB role column detected: $hasRoleCol")

                val query = buildString {
                    append("SELECT node_id, short_name, long_name, hex_id")
                    if (hasRoleCol) append(", role AS node_role")
                    append(" FROM node_info WHERE short_name IS NOT NULL AND short_name != ''")
                }
                Log.d(TAG, "DB query: $query")
                val stmt = conn.createStatement()
                val rs = stmt.executeQuery(query)

                synchronized(this@MeshtasticMonitor) {
                    while (rs.next()) {
                        val nodeIdRaw = rs.getString("node_id") ?: continue
                        val nodeId = nodeIdRaw.toLongOrNull()?.toInt() ?: continue
                        val shortName = rs.getString("short_name") ?: continue
                        val longName = rs.getString("long_name")
                        val hexId = rs.getString("hex_id")

                        if (nodeId != 0 && shortName.isNotEmpty()) {
                            nodeNames[nodeId] = shortName
                            if (!longName.isNullOrEmpty()) {
                                nodeLongNames[nodeId] = longName
                            }
                            if (hasRoleCol) {
                                val roleVal = rs.getString("node_role")
                                // role column is text; may be numeric ("1") or named ("CLIENT_MUTE")
                                val role = roleVal?.toIntOrNull() ?: when (roleVal?.uppercase()) {
                                    "CLIENT" -> 0
                                    "CLIENT_MUTE" -> 1
                                    "ROUTER" -> 2
                                    "ROUTER_CLIENT" -> 3
                                    "REPEATER" -> 4
                                    "TRACKER" -> 5
                                    "SENSOR" -> 6
                                    "TAK" -> 7
                                    "CLIENT_HIDDEN" -> 8
                                    "LOST_AND_FOUND" -> 9
                                    "TAK_TRACKER" -> 10
                                    "ROUTER_LATE" -> 11
                                    "CLIENT_BASE" -> 12
                                    else -> null
                                }
                                if (role != null) {
                                    nodeRoles[nodeId] = role
                                    Log.d(TAG, "DB role: $shortName ($nodeId) = $roleVal -> $role")
                                } else {
                                    Log.d(TAG, "DB role UNRECOGNIZED: $shortName ($nodeId) = '$roleVal'")
                                }
                            }
                            if (!hexId.isNullOrEmpty()) {
                                hexToNodeId[hexId] = nodeId
                                try {
                                    val lastByte = hexId.takeLast(2).toInt(16)
                                    val lbNames = lastByteToNames.getOrPut(lastByte) { mutableListOf() }
                                    if (shortName !in lbNames) lbNames.add(shortName)
                                    val lbStats = relayNodeStats.getOrPut(lastByte) { mutableMapOf() }
                                    if (nodeId !in lbStats) {
                                        lbStats[nodeId] = RelayNodeStats(nodeId = nodeId, shortName = shortName)
                                    }
                                } catch (_: Exception) { }
                            }

                            // Pre-populate my_nodes
                            if (shortName.lowercase() in myNodesLower) {
                                val nodeKey = shortName.lowercase()
                                if (nodeKey !in myNodes) {
                                    myNodes[nodeKey] = NodeStats(nodeId = nodeId, shortName = shortName)
                                    myNodeLastBytes.add(nodeId and 0xFF)
                                }
                            }
                        }
                    }
                    dbLoaded = true
                    dbConnecting = false
                    dbError = null
                    dbNodeCount = nodeNames.size
                }

                rs.close()
                stmt.close()
                conn.close()
                Log.i(TAG, "Loaded $dbNodeCount node names from database")
                isDirty.set(true)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load node names from DB (will use NODEINFO packets)", e)
                synchronized(this@MeshtasticMonitor) {
                    dbLoaded = false
                    dbConnecting = false
                    dbError = e.message?.take(40) ?: "Connection failed"
                    dbNodeCount = 0
                }
                isDirty.set(true)
            }
        }
    }

    private fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (isDirty.getAndSet(false)) {
                    emitState()
                }
            }
        }
    }

    private fun doConnect() {
        try {
            _state.value = _state.value.copy(connecting = true, connectionError = null)

            val serverUri = "tcp://${settings.broker}:${settings.port}"
            val client = MqttClient(serverUri, "meshtop_android_${System.currentTimeMillis() % 10000}", MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 30
                isAutomaticReconnect = true
                maxReconnectDelay = 16000 // max 16s between retries
                if (settings.username.isNotEmpty()) {
                    userName = settings.username
                    password = settings.password.toCharArray()
                }
            }

            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.i(TAG, "MQTT ${if (reconnect) "re" else ""}connected to $serverURI")
                    connected = true
                    connectionError = null
                    try {
                        client.subscribe(settings.topic)
                    } catch (e: Exception) {
                        Log.e(TAG, "Subscribe error", e)
                    }
                    isDirty.set(true)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT connection lost, auto-reconnect will retry", cause)
                    connected = false
                    // Show "Reconnecting..." instead of the raw error since auto-reconnect is active
                    connectionError = "Reconnecting..."
                    isDirty.set(true)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.payload?.let { processMessage(it) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client.connect(options)
            mqttClient = client
            connected = true
            startTime = System.currentTimeMillis()

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            connected = false
            connectionError = e.message ?: "Connection failed"
            // Schedule a retry after 5 seconds
            scope.launch {
                delay(5000)
                if (!connected && mqttClient == null) {
                    Log.i(TAG, "Retrying connection...")
                    doConnect()
                }
            }
        }

        _state.value = _state.value.copy(connecting = false)
        isDirty.set(true)
    }

    private fun processMessage(payload: ByteArray) {
        try {
            totalPackets++

            val envelope = MqttProtos.ServiceEnvelope.parseFrom(payload)
            val packet = envelope.packet
            val fromId = packet.from
            val toId = packet.to
            val gatewayId = envelope.gatewayId.ifEmpty { "unknown" }
            val channelId = envelope.channelId.ifEmpty { "LongFast" }
            val meshPacketId = packet.id

            // Parse gateway hex ID to integer node ID (e.g. "!a1b2c3d4" -> int)
            // This builds the hexToNodeId mapping on-the-fly since we don't have a database
            if (gatewayId.startsWith("!") && gatewayId.length >= 3) {
                try {
                    val gwNodeId = java.lang.Long.parseUnsignedLong(gatewayId.removePrefix("!"), 16).toInt()
                    synchronized(this) {
                        hexToNodeId[gatewayId] = gwNodeId
                    }
                } catch (_: NumberFormatException) { }
            }

            // Try to decrypt if needed
            val hasDecoded = packet.payloadVariantCase == MeshProtos.MeshPacket.PayloadVariantCase.DECODED
            val hasEncrypted = packet.payloadVariantCase == MeshProtos.MeshPacket.PayloadVariantCase.ENCRYPTED

            var decoded: MeshProtos.Data? = if (hasDecoded) packet.decoded else null

            if (!hasDecoded && hasEncrypted) {
                decoded = decryptPacket(packet)
            }

            val rssi: Float? = if (packet.rxRssi != 0) packet.rxRssi.toFloat() else null
            val snr: Float? = if (packet.rxSnr != 0f) packet.rxSnr else null
            val hopStart = packet.hopStart.toInt()
            val hopLimit = packet.hopLimit.toInt()
            val relayNode = packet.relayNode.toInt()

            val portnum: Int
            val portnumName: String
            val wantResponse: Boolean

            if (decoded != null) {
                portnum = decoded.portnumValue
                portnumName = PORTNUM_NAMES[portnum] ?: "PORT_$portnum"
                wantResponse = decoded.wantResponse
            } else {
                portnum = 0
                portnumName = "ENCRYPTED"
                wantResponse = false
            }

            synchronized(this) {
                val fromName = getNodeName(fromId)
                val gatewayName = resolveGatewayName(gatewayId)

                val now = Instant.now()
                val hops = hopStart - hopLimit

                if (hops > 0 && relayNode == 0) {
                    legacyRelayTotal++
                    rssi?.let { legacyRelayRssiSum += it; legacyRelayRssiCount++ }
                    snr?.let { legacyRelaySnrSum += it; legacyRelaySnrCount++ }
                    Log.d(TAG, "Legacy relay: hops=$hops rssi=$rssi snr=$snr total=$legacyRelayTotal rssiCount=$legacyRelayRssiCount")
                }

                // Track gateway node IDs
                hexToNodeId[gatewayId]?.let { gatewayNodeIds.add(it) }

                val fromIsGateway = fromId in gatewayNodeIds
                val fromIsMyNode = !fromIsGateway && fromName.lowercase() in myNodesLower

                // Update gateway stats
                val gw = gateways.getOrPut(gatewayId) { GatewayStats(gatewayId) }
                gw.packetCount++
                gw.lastSeen = now
                gw.uniqueNodes.add(fromId)

                if (gatewayName != gatewayId) gw.shortName = gatewayName
                resolveGatewayLongName(gatewayId)?.let { gw.longName = it }

                if (hops == 0) gw.totalDirectCount++
                else if (hops > 0) gw.totalRelayedCount++

                // Accumulate signal into all bucket
                gw.signalAll.addPacket(rssi, snr)

                // Categorize source into gateway or my-node bucket
                when {
                    fromIsGateway -> gw.signalGw.addPacket(rssi, snr)
                    fromIsMyNode -> gw.signalMyNode.addPacket(rssi, snr)
                }

                // Check if from a tracked node
                if (fromName.lowercase() in myNodesLower) {
                    val nodeKey = fromName.lowercase()
                    val node = myNodes.getOrPut(nodeKey) { NodeStats(nodeId = fromId, shortName = fromName) }
                    if (node.nodeId == 0) {
                        // Fill in nodeId from first observation
                        myNodes[nodeKey] = node.copy(nodeId = fromId)
                    }
                    val n = myNodes[nodeKey]!!
                    n.packetCount++
                    n.lastSeen = now
                    n.gatewaysHeardBy.add(gatewayId)

                    if (hops >= 0) {
                        n.hopSum += hops
                        n.hopCount++
                    }

                    when {
                        relayNode > 0 -> n.modernRelayCount++
                        hops > 0 -> n.legacyRelayCount++
                        else -> n.directCount++
                    }

                    rssi?.let { n.rssiSum += it; n.rssiCount++ }
                    snr?.let { n.snrSum += it; n.snrCount++ }

                    // Accumulate signal buckets for node stats
                    // Categorize by receiving gateway: if gateway is one of our nodes,
                    // this is inter-my-node traffic that we may want to filter out
                    val gwNodeId = hexToNodeId[gatewayId]
                    val gwIsGateway = gwNodeId != null && gwNodeId in gatewayNodeIds
                    val gwIsMyNode = !gwIsGateway && gwNodeId != null &&
                            (nodeNames[gwNodeId]?.lowercase() ?: "") in myNodesLower
                    n.signalAll.addPacket(rssi, snr)
                    when {
                        gwIsGateway -> n.signalGw.addPacket(rssi, snr)
                        gwIsMyNode -> n.signalMyNode.addPacket(rssi, snr)
                    }

                    // Track last byte for relay filtering
                    if (fromId != 0) myNodeLastBytes.add(fromId and 0xFF)
                }

                // Track direct packet observations for relay disambiguation
                val lastByteFrom = fromId and 0xFF
                relayNodeStats[lastByteFrom]?.get(fromId)?.let { stats ->
                    stats.directPacketCount++
                    stats.lastSeenDirect = System.currentTimeMillis()
                    rssi?.let { if (it > -120f) { stats.directRssiSum += it; stats.directRssiCount++ } }
                    snr?.let { stats.directSnrSum += it; stats.directSnrCount++ }
                    if (hops >= 0) {
                        stats.directHopSum += hops
                        stats.directHopCount++
                        if (hops == 0) stats.zeroHopCount++
                    }
                }

                // Check if relayed BY one of my nodes
                if (relayNode > 0) {
                    for ((_, node) in myNodes) {
                        if (node.nodeId != 0 && (node.nodeId and 0xFF) == relayNode) {
                            node.relayForOthers++
                            break
                        }
                    }
                }

                // Update relay node stats for candidates
                if (relayNode > 0) {
                    if (relayNode !in relayNodeStats) {
                        // Create placeholder entry so relay observations aren't lost
                        // for bytes not yet seen via NODEINFO or DB
                        relayNodeStats[relayNode] = mutableMapOf()
                        Log.d(TAG, "Relay byte 0x${"%02x".format(relayNode)} not in relayNodeStats, created empty map")
                    }
                    val candidates = relayNodeStats[relayNode]!!
                    val currentTime = System.currentTimeMillis()

                    // If no candidates exist yet, create a placeholder
                    if (candidates.isEmpty()) {
                        val syntheticId = relayNode // placeholder nodeId
                        candidates[syntheticId] = RelayNodeStats(
                            nodeId = syntheticId,
                            shortName = "!..${"%02x".format(relayNode)}",
                        )
                        Log.d(TAG, "Created placeholder relay candidate for byte 0x${"%02x".format(relayNode)}")
                    }

                    val viable = candidates.filter { (nodeId, stats) ->
                        // CLIENT_MUTE nodes (role=1) cannot relay in firmware — skip them
                        if (nodeRoles[nodeId] == 1) return@filter false
                        if (stats.directPacketCount > 0) {
                            val avgH = stats.avgDirectHops
                            (avgH == null || avgH < 2.0) && stats.matchesSignal(rssi)
                        } else true
                    }

                    // If any viable candidates have 0-hop observations, prefer only those
                    val zeroHopViable = viable.filter { (_, stats) -> stats.zeroHopCount > 0 }
                    val toUpdate = if (zeroHopViable.isNotEmpty()) zeroHopViable else viable

                    if (toUpdate.isEmpty()) {
                        // All candidates were filtered out — fall back to all non-CLIENT_MUTE
                        val fallback = candidates.filter { (nodeId, _) -> nodeRoles[nodeId] != 1 }
                        if (fallback.isNotEmpty()) {
                            for ((_, stats) in fallback) {
                                stats.relayCount++
                                stats.lastSeenRelay = currentTime
                                rssi?.let { if (it > -120f) { stats.relayRssiSum += it; stats.relayRssiCount++ } }
                                snr?.let { stats.relaySnrSum += it; stats.relaySnrCount++ }
                            }
                        }
                    } else if (rssi != null && rssi > -120f) {
                        for ((_, stats) in toUpdate) {
                            stats.relayCount++
                            stats.lastSeenRelay = currentTime
                            stats.relayRssiSum += rssi
                            stats.relayRssiCount++
                            snr?.let { stats.relaySnrSum += it; stats.relaySnrCount++ }
                        }
                    }
                }

                // Process NODEINFO
                if (decoded != null && portnum == 4) { // NODEINFO_APP
                    try {
                        val user = MeshProtos.User.parseFrom(decoded.payload)
                        if (user.shortName.isNotEmpty()) {
                            nodeNames[fromId] = user.shortName
                            // Also map hex ID -> node ID for future lookups
                            val hexId = "!${Integer.toUnsignedString(fromId, 16).padStart(8, '0')}"
                            hexToNodeId[hexId] = fromId
                            // Update relay candidate data
                            val lb = fromId and 0xFF
                            val lbNames = lastByteToNames.getOrPut(lb) { mutableListOf() }
                            if (user.shortName !in lbNames) lbNames.add(user.shortName)
                            val lbStats = relayNodeStats.getOrPut(lb) { mutableMapOf() }
                            if (fromId !in lbStats) {
                                lbStats[fromId] = RelayNodeStats(nodeId = fromId, shortName = user.shortName)
                            }
                        }
                        if (user.longName.isNotEmpty()) {
                            nodeLongNames[fromId] = user.longName
                        }
                        nodeRoles[fromId] = user.role
                        // Update gateway name
                        if (gatewayId == "!${Integer.toUnsignedString(fromId, 16).padStart(8, '0')}") {
                            if (user.shortName.isNotEmpty()) gw.shortName = user.shortName
                            if (user.longName.isNotEmpty()) gw.longName = user.longName
                        }
                        // Update my_nodes
                        if (user.shortName.isNotEmpty() && user.shortName.lowercase() in myNodesLower) {
                            val nodeKey = user.shortName.lowercase()
                            val existing = myNodes[nodeKey]
                            if (existing != null && existing.nodeId == 0) {
                                myNodes[nodeKey] = existing.copy(nodeId = fromId, shortName = user.shortName)
                            }
                            if (fromId != 0) myNodeLastBytes.add(fromId and 0xFF)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "NODEINFO parse error", e)
                    }
                }

                // Text messages
                if (decoded != null && portnum == 1) { // TEXT_MESSAGE_APP
                    try {
                        val text = decoded.payload.toStringUtf8()
                        gw.messageCount++

                        val toName = if (toId.toLong() and 0xFFFFFFFFL == 0xFFFFFFFFL) "broadcast"
                        else getNodeName(toId)

                        val msgInfo = MessageInfo(
                            timestamp = now,
                            fromId = fromId,
                            fromName = fromName,
                            toId = toId,
                            toName = toName,
                            gatewayId = gatewayId,
                            gatewayName = gatewayName,
                            text = text,
                            rssi = rssi,
                            snr = snr,
                            channel = channelId,
                            hopStart = hopStart,
                            hopLimit = hopLimit,
                            relayNode = relayNode,
                            packetId = meshPacketId,
                        )
                        recentMessages.addFirst(msgInfo)
                        while (recentMessages.size > MAX_MESSAGES) recentMessages.removeLast()
                    } catch (e: Exception) {
                        Log.d(TAG, "Text message parse error", e)
                    }
                }

                // Traceroutes
                if (decoded != null && portnum == 70) { // TRACEROUTE_APP
                    try {
                        val rd = MeshProtos.RouteDiscovery.parseFrom(decoded.payload)
                        val route = rd.routeList.map { it.toInt() }
                        val snrTowards = rd.snrTowardsList.map { it / 4.0f }
                        val routeBack = rd.routeBackList.map { it.toInt() }
                        val snrBack = rd.snrBackList.map { it / 4.0f }
                        fun resolveId(id: Int) = nodeNames[id] ?: "!%08x".format(id.toLong() and 0xFFFFFFFFL)
                        val toName = if (toId.toLong() and 0xFFFFFFFFL == 0xFFFFFFFFL) "broadcast"
                        else resolveId(toId)
                        val traceroute = TracerouteInfo(
                            timestamp = now,
                            packetId = meshPacketId.toInt(),
                            fromId = fromId, fromName = fromName,
                            toId = toId, toName = toName,
                            gatewayId = gatewayId, gatewayName = gatewayName,
                            rssi = rssi, snr = snr,
                            hopStart = hopStart, hopLimit = hopLimit,
                            route = route, routeNames = route.map { resolveId(it) }, snrTowards = snrTowards,
                            routeBack = routeBack, routeBackNames = routeBack.map { resolveId(it) }, snrBack = snrBack,
                        )
                        while (recentTraceroutes.size >= MAX_TRACEROUTES) recentTraceroutes.removeFirst()
                        recentTraceroutes.addLast(traceroute)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse traceroute", e)
                    }
                }

                // Add to recent packets
                val pktInfo = PacketInfo(
                    timestamp = now,
                    fromId = fromId,
                    fromName = fromName,
                    toId = toId,
                    gatewayId = gatewayId,
                    gatewayName = gatewayName,
                    portnum = portnum,
                    portnumName = portnumName,
                    rssi = rssi,
                    snr = snr,
                    hopStart = hopStart,
                    hopLimit = hopLimit,
                    packetId = meshPacketId,
                    wantResponse = wantResponse,
                    relayNode = relayNode,
                )
                recentPackets.addFirst(pktInfo)
                while (recentPackets.size > MAX_PACKETS) recentPackets.removeLast()
            }

            isDirty.set(true)

        } catch (e: Exception) {
            Log.d(TAG, "Message processing error", e)
        }
    }

    private fun decryptPacket(packet: MeshProtos.MeshPacket): MeshProtos.Data? {
        return try {
            val encrypted = packet.encrypted.toByteArray()
            val fromId = packet.from
            val packetId = packet.id

            val nonce = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(packetId)
                .putInt(0)
                .putInt(fromId)
                .putInt(0)
                .array()

            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(defaultKey, "AES"), IvParameterSpec(nonce))
            val decrypted = cipher.doFinal(encrypted)

            val data = MeshProtos.Data.parseFrom(decrypted)
            if (data.portnumValue == 0) {
                failedDecryptions++
                return null
            }

            decryptedPackets++
            data
        } catch (e: Exception) {
            failedDecryptions++
            null
        }
    }

    private fun getNodeName(nodeId: Int): String {
        return nodeNames[nodeId] ?: "!${Integer.toUnsignedString(nodeId, 16).padStart(8, '0')}"
    }

    private fun resolveGatewayName(gatewayId: String): String {
        val nodeId = hexToNodeId[gatewayId] ?: return gatewayId
        return nodeNames[nodeId] ?: gatewayId
    }

    private fun resolveGatewayLongName(gatewayId: String): String? {
        val nodeId = hexToNodeId[gatewayId] ?: return null
        return nodeLongNames[nodeId]
    }

    fun getRelayNodeName(relayNode: Int): String {
        if (relayNode == 0) return ""

        synchronized(this) {
            val candidates = relayNodeStats[relayNode]
            if (candidates != null && candidates.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val sorted = candidates.values.sortedByDescending { it.score(currentTime) }
                val best = sorted.first()

                if (sorted.size > 1) {
                    val bestScore = best.score(currentTime)
                    val secondScore = sorted[1].score(currentTime)
                    return if (bestScore > 0 && secondScore < 0) {
                        best.shortName
                    } else if (bestScore > secondScore * 2.0) {
                        best.shortName
                    } else {
                        "${sorted[0].shortName}/${sorted[1].shortName}"
                    }
                } else {
                    return best.shortName
                }
            }

            val names = lastByteToNames[relayNode]
            if (names != null && names.isNotEmpty()) {
                return if (names.size == 1) names[0] else "${names[0]}/${names[1]}"
            }

            return "!..${String.format("%02x", relayNode)}"
        }
    }

    fun createSnapshot(): MonitorSnapshot = synchronized(this) {
        MonitorSnapshot(
            gateways = gateways.toMap(),
            myNodes = myNodes.toMap(),
            nodeNames = nodeNames.toMap(),
            nodeLongNames = nodeLongNames.toMap(),
            hexToNodeId = hexToNodeId.toMap(),
            gatewayNodeIds = gatewayNodeIds.toSet(),
            myNodeLastBytes = myNodeLastBytes.toSet(),
            lastByteToNames = lastByteToNames.mapValues { (_, v) -> v.toList() },
            relayNodeStats = relayNodeStats.mapValues { (_, v) -> v.toMap() },
            nodeRoles = nodeRoles.toMap(),
            recentPackets = recentPackets.toList(),
            recentMessages = recentMessages.toList(),
            recentTraceroutes = recentTraceroutes.toList(),
            legacyRelayTotal = legacyRelayTotal,
            legacyRelayRssiSum = legacyRelayRssiSum,
            legacyRelayRssiCount = legacyRelayRssiCount,
            legacyRelaySnrSum = legacyRelaySnrSum,
            legacyRelaySnrCount = legacyRelaySnrCount,
            totalPackets = totalPackets,
            decryptedPackets = decryptedPackets,
            failedDecryptions = failedDecryptions,
            uptimeMillis = System.currentTimeMillis() - startTime,
        )
    }

    fun restoreFromSnapshot(snapshot: MonitorSnapshot) = synchronized(this) {
        gateways.clear()
        gateways.putAll(snapshot.gateways)
        myNodes.clear()
        myNodes.putAll(snapshot.myNodes)
        nodeNames.clear()
        nodeNames.putAll(snapshot.nodeNames)
        nodeLongNames.clear()
        nodeLongNames.putAll(snapshot.nodeLongNames)
        hexToNodeId.clear()
        hexToNodeId.putAll(snapshot.hexToNodeId)
        gatewayNodeIds.clear()
        gatewayNodeIds.addAll(snapshot.gatewayNodeIds)
        myNodeLastBytes.clear()
        myNodeLastBytes.addAll(snapshot.myNodeLastBytes)
        lastByteToNames.clear()
        snapshot.lastByteToNames.forEach { (k, v) -> lastByteToNames[k] = v.toMutableList() }
        relayNodeStats.clear()
        snapshot.relayNodeStats.forEach { (k, v) -> relayNodeStats[k] = v.toMutableMap() }
        nodeRoles.clear()
        nodeRoles.putAll(snapshot.nodeRoles)
        recentPackets.clear()
        recentPackets.addAll(snapshot.recentPackets)
        recentMessages.clear()
        recentMessages.addAll(snapshot.recentMessages)
        recentTraceroutes.clear()
        recentTraceroutes.addAll(snapshot.recentTraceroutes)
        totalPackets = snapshot.totalPackets
        decryptedPackets = snapshot.decryptedPackets
        failedDecryptions = snapshot.failedDecryptions
        legacyRelayTotal = snapshot.legacyRelayTotal
        legacyRelayRssiSum = snapshot.legacyRelayRssiSum
        legacyRelayRssiCount = snapshot.legacyRelayRssiCount
        legacyRelaySnrSum = snapshot.legacyRelaySnrSum
        legacyRelaySnrCount = snapshot.legacyRelaySnrCount
        startTime = System.currentTimeMillis() - snapshot.uptimeMillis
        isDirty.set(true)
        Log.i(TAG, "Restored snapshot: ${snapshot.totalPackets} packets, ${snapshot.gateways.size} gateways")
    }

    private fun emitState() {
        synchronized(this) {
            _state.value = MonitorUiState(
                connected = connected,
                connecting = false,
                connectionError = connectionError,
                totalPackets = totalPackets,
                decryptedPackets = decryptedPackets,
                failedDecryptions = failedDecryptions,
                uptimeMillis = System.currentTimeMillis() - startTime,
                gateways = gateways.values
                    .sortedByDescending { it.signalAll.packetCount }
                    .toList(),
                myNodes = myNodes.toMap(),
                recentPackets = recentPackets.toList(),
                recentMessages = recentMessages.toList(),
                recentTraceroutes = recentTraceroutes.toList(),
                legacyRelayTotal = legacyRelayTotal,
                legacyRelayAvgRssi = if (legacyRelayRssiCount > 0) legacyRelayRssiSum / legacyRelayRssiCount else null,
                legacyRelayAvgSnr = if (legacyRelaySnrCount > 0) legacyRelaySnrSum / legacyRelaySnrCount else null,
                relayNodeStats = relayNodeStats.mapValues { (_, v) -> v.toMap() },
                clientMuteNodeIds = nodeRoles.entries.filter { it.value == 1 }.map { it.key }.toSet(),
                nodeRoles = nodeRoles.toMap(),
                hexToNodeId = hexToNodeId.toMap(),
                gatewayNodeIds = gatewayNodeIds.toSet(),
                myNodeIds = myNodes.values.map { it.nodeId }.filter { it != 0 }.toSet(),
                dbConfigured = dbConfigured,
                dbConnecting = dbConnecting,
                dbLoaded = dbLoaded,
                dbError = dbError,
                dbNodeCount = dbNodeCount,
            )
        }
    }
}
