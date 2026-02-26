# CLAUDE.md - Agent Guide for meshtop-android

## What is this project?

meshtop-android is an Android app that monitors Meshtastic mesh networks via MQTT, built with Kotlin and Jetpack Compose.

The app connects to an MQTT broker, receives Meshtastic mesh packets, decrypts them (AES-128-CTR), and displays real-time statistics across six tabbed views: Summary, Messages, Gateways, My Nodes, Packets, and Traceroutes (TR).

## Build & Run

```bash
./gradlew assembleDebug                    # Build debug APK
./gradlew assembleRelease                  # Build release APK
adb install -r app/build/outputs/apk/debug/app-debug.apk  # Install via ADB
```

- JDK 17 required
- Android SDK API 35 (set via `ANDROID_HOME` or `local.properties`)
- No tests currently exist
- Version is driven from git tags (`v0.3.0` → `versionName "0.3.0"`, `versionCode` from tag count)

## Architecture

```
com.meshtop/
├── MainActivity.kt          # Entry point, TopAppBar, About dialog, notification permission
├── MeshtopApp.kt            # Application singleton, owns MeshtasticMonitor, notification channels
├── MonitorService.kt        # Foreground service: wake lock, message notifications, stats updates
├── data/
│   ├── MeshtasticMonitor.kt # Core logic: MQTT client, packet decryption, statistics, DB loading
│   ├── Models.kt            # Data classes: GatewayStats, NodeStats, PacketInfo, MessageInfo,
│   │                        #   TracerouteInfo, RelayNodeStats, ConnectionSettings, MonitorUiState
│   ├── ProtoVersion.kt      # PROTO_VERSION constant shown in About dialog
│   └── Settings.kt          # SharedPreferences wrapper for ConnectionSettings
├── viewmodel/
│   └── MonitorViewModel.kt  # AndroidViewModel bridging MeshtasticMonitor to Compose UI
└── ui/
    ├── MainScreen.kt        # HorizontalPager with tab bar (6 tabs); BackHandler for settings dismiss
    ├── SummaryScreen.kt     # Network overview dashboard with Canvas-based visualizations (6 cards)
    ├── MessagesScreen.kt    # Card-based message display grouped by packetId
    ├── GatewaysScreen.kt    # Gateway stats table + shared composables (SectionDescription, HeaderCell, DataCell)
    ├── MyNodesScreen.kt     # Tracked node stats table (dynamic from settings)
    ├── PacketsScreen.kt     # Raw packet feed table
    ├── TracerouteScreen.kt  # Traceroute (portnum 70) cards, grouped by packetId across gateways
    ├── SettingsScreen.kt    # MQTT + PostgreSQL settings form
    ├── StatsHeader.kt       # Connection status dots (MQTT + DB) and stats chips
    └── theme/Theme.kt       # Dark color scheme and typography
```

### Key patterns

- **Application singleton**: `MeshtopApp` owns the single `MeshtasticMonitor` instance shared by both `MonitorViewModel` and `MonitorService`
- **StateFlow**: `MeshtasticMonitor` emits `MonitorUiState` via `StateFlow` at 1Hz. All data maps are updated under `synchronized(this)` blocks and snapshot-copied into the immutable state
- **Foreground service**: `MonitorService` holds a `PARTIAL_WAKE_LOCK` and runs as `foregroundServiceType="dataSync"` to keep MQTT alive in the background
- **Tab navigation only**: `HorizontalPager` has `userScrollEnabled = false` to avoid conflicts with horizontally scrollable tables
- **Settings back gesture**: `BackHandler(enabled = showSettings)` in `MainScreen` intercepts the system back gesture to dismiss settings rather than exit the app
- **Notification dedup**: `MonitorService` seeds `notifiedPacketIds` from the first `recentMessages` emission (MQTT backlog) to suppress startup notification blasts; only subsequent new messages fire alerts

### Data flow

1. MQTT message arrives → `MeshtasticMonitor.processMessage()`
2. Packet is decrypted (AES-128-CTR with Meshtastic LongFast default key)
3. Protobuf parsed → gateway stats, node stats, relay stats, traceroutes updated
4. `isDirty` flag set → refresh loop emits new `MonitorUiState` via `StateFlow`
5. Compose UI recomposes from the new state

### Relay node disambiguation

Meshtastic relay node IDs are only the last byte of the full node ID, creating ambiguity. The `RelayNodeStats` class tracks candidates per last-byte value, scoring them by:
- Direct packet count and recency
- Hop distance (>= 3 hops disqualifies)
- Relay count and recency
- RSSI/SNR signal quality
- Signal consistency (`matchesSignal()`)

### Traceroute handling (portnum 70)

`RouteDiscovery` packets are decoded inside the main `synchronized` block in `processMessage()`. Each observation (one per gateway) is stored as a `TracerouteInfo` in a capped `ArrayDeque<TracerouteInfo>(MAX_TRACEROUTES=100)`. The `TracerouteScreen` groups entries by `packetId` so multiple gateway observations of the same traceroute collapse into one card, with per-gateway RSSI/SNR listed in the footer. The `RouteDiscovery` proto message lives in `mesh.proto` (fields 1–4 matching official Meshtastic protobufs).

## Key dependencies

- `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` - MQTT client
- `com.google.protobuf:protobuf-javalite:4.28.3` - Protobuf lite runtime
- `org.postgresql:postgresql:42.2.5` - JDBC driver (42.2.x for Android compatibility; newer versions crash due to `java.lang.management.ManagementFactory` not existing on Android)
- Compose BOM `2024.12.01` with Material 3

## Protobuf

Minimal `.proto` files in `app/src/main/proto/meshtastic/`:
- `mesh.proto` - `MeshPacket`, `Data`, `User`, `RouteDiscovery`, `PortNum` enum
- `mqtt.proto` - `ServiceEnvelope` (imports mesh.proto)

Generated via the `com.google.protobuf` Gradle plugin with the `lite` option.

**Critical**: proto field numbers must exactly match the official Meshtastic protobufs at
https://github.com/meshtastic/protobufs/blob/master/meshtastic/mesh.proto — wrong field numbers
cause silent misparse (e.g. RSSI always 0, SNR garbage). This has bitten us before.

## Things to know

- **No hardcoded credentials**: All connection settings (MQTT broker, DB) default to empty and must be configured by the user in the Settings screen
- **The default encryption key** `1PG7OiApB1nwvP+rz05pAQ==` is the **public** Meshtastic LongFast channel key, not a secret
- **PostgreSQL is optional**: The app works fine without it, resolving node names from NODEINFO packets received over MQTT. The DB just pre-populates names at startup
- **PostgreSQL JDBC version matters**: Must use 42.2.x on Android. Version 42.3+ uses `java.lang.management.ManagementFactory` which doesn't exist on Android and causes a crash
- **No tests yet**: The project has no unit or instrumentation tests
- **Notification channels**: `meshtop_service` (low importance, silent) for the persistent service notification; `meshtop_messages` (high importance, heads-up) for text message alerts
- **Versioning**: `versionName` and `versionCode` are derived from git tags automatically. Create releases with `git tag -a vX.Y.Z` + `gh release create`

## Release history

| Version | Highlights |
|---------|-----------|
| v0.3.0  | Traceroutes (TR) tab; settings back gesture fix; startup notification blast fix |
| v0.2.x  | About dialog, version from git tags, per-source signal bucketing, relay scoring fixes |
| v0.1.x  | Initial build: MQTT monitoring, 5 tabs, AES decryption, PostgreSQL optional preload |
