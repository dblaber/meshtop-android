# meshtop for Android

An Android app for monitoring Meshtastic mesh networks via MQTT. Provides real-time visibility into gateway performance, node activity, packet flow, and text messages across the mesh.

## Features

- **Real-time MQTT monitoring** - Connects to any Meshtastic MQTT broker and processes mesh traffic live
- **AES-128-CTR decryption** - Decrypts encrypted packets using the standard Meshtastic LongFast key
- **Four tabbed views:**
  - **Messages** - Text messages grouped by packet ID across gateways, showing sender, recipient, gateway, hops, and signal quality
  - **Gateways** - Gateway performance table with packet counts, direct/relayed ratios, average RSSI/SNR, and unique node counts
  - **My Nodes** - Detailed stats for your tracked nodes including direct vs relay counts, signal averages, and gateway coverage
  - **Packets** - Raw packet feed with port type, signal data, hop count, and relay node disambiguation
- **Relay node disambiguation** - Resolves ambiguous single-byte relay node IDs using proximity-based scoring (ported from the Python implementation)
- **Background operation** - Foreground service with wake lock keeps MQTT connected when the screen is off
- **Text message notifications** - Heads-up notifications for incoming mesh messages with sender, gateway, and signal info
- **Optional PostgreSQL database** - Load node names from an existing Meshtastic database for immediate name resolution instead of waiting for NODEINFO packets
- **Dark terminal aesthetic** - Color-coded UI inspired by the original TUI

## Screenshots

*Coming soon*

## Setup

1. Install the APK or build from source
2. Open the app and tap the gear icon to configure:
   - **MQTT Broker** - Your Meshtastic MQTT server hostname
   - **Port** - MQTT port (default: 1883)
   - **Username/Password** - MQTT credentials (if required)
   - **Topic** - MQTT topic filter (e.g. `msh/US/2/e/LongFast/#`)
   - **My Nodes** - Comma-separated short names of your nodes to track
3. Optionally configure PostgreSQL database settings for node name resolution
4. Tap "Save & Reconnect"

## Building

### Prerequisites

- Android Studio Ladybug or later (or JDK 17 + Android SDK)
- Android SDK with API level 35

### Build from source

```bash
git clone https://github.com/your-username/meshtop-android.git
cd meshtop-android
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### CI/CD

- **Every push to main** builds a debug APK, downloadable from GitHub Actions artifacts
- **Tagged releases** (`git tag v1.0.0 && git push origin v1.0.0`) automatically create GitHub Releases with the APK attached

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Eclipse Paho MQTT v3 client
- Protocol Buffers (lite) for Meshtastic packet parsing
- PostgreSQL JDBC for optional database connectivity
- Coroutines + StateFlow for reactive updates

## Requirements

- Android 8.0+ (API 26)
- Network access to an MQTT broker carrying Meshtastic traffic

## License

[MIT](LICENSE)
