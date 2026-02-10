# temi-robot-poc

A proof-of-concept system that uses **Zeelo indoor location SDK** on a phone to continuously track position, and relays the location to a **temi robot** via MQTT so the robot can **repose** (relocate itself on its map).

## Architecture

```
┌─────────────────────┐       MQTT (temi/command)       ┌──────────────────────┐
│   Phone App         │ ──────────────────────────────→  │   Temi Pad Relay     │
│                     │                                  │                      │
│  Zeelo Location SDK │                                  │  CoordinateMapper    │
│  (LocationEngine or │       MQTT (temi/status)         │  + robot.repose()    │
│   GPS fallback or   │ ←──────────────────────────────  │                      │
│   manual input)     │                                  │  OnReposeStatus      │
└─────────────────────┘                                  └──────────────────────┘
         │                                                        │
         │  Zeelo SDK polls                              temi SDK repose()
         │  every N seconds                              relocates the robot
         ▼                                               on its internal map
   ┌───────────┐                                                  │
   │ Zeelo CMS │                                                  ▼
   │ (cloud)   │                                          ┌──────────────┐
   └───────────┘                                          │  temi Robot  │
                                                          └──────────────┘
```

## Sub-System Documentation

Each sub-system has its own detailed README with architecture, installation, configuration, and troubleshooting:

| Sub-System | README | Description |
|------------|--------|-------------|
| **MQTT Broker** | [mosquitto/README.md](temi-mqtt-system/mosquitto/README.md) | Docker Compose setup, broker configuration, credentials |
| **Phone App** | [temi-phone-app/README.md](temi-mqtt-system/temi-phone-app/README.md) | Zeelo SDK integration, source resolution, polling, manual input |
| **Relay App** | [temi-pad-relay/README.md](temi-mqtt-system/temi-pad-relay/README.md) | Coordinate mapping, calibration, temi SDK repose |

## Data Flow

```
1. Phone App  ─── Zeelo SDK callback ───→  location, gpsLocation, locationSource, direction
                         │
2.                       ▼
               Resolve active location by locationSource:
                 "LocationEngine" → use location object  (Zeelo indoor)
                 "GPS"            → use gpsLocation object (GPS fallback)
               The resolved data is placed into the "location" key.
                         │
3.                       ▼
               Build JSON command:
               {
                 "action": "update_location",
                 "location_data": {
                   "location": {              ← resolved from active source
                     "latitude": 22.25, "longitude": 113.56,
                     "hkE": 773218.82, "hkN": 812570.95,
                     "floorLevel": 7, "geofenceName": "BLDG0001", ...
                   },
                   "gpsLocation": { ... },    ← raw GPS (always included if available)
                   "locationSource": "LocationEngine" | "GPS" | "Manual",
                   "direction": 54.96,
                   "timestamp": 1707560000000
                 }
               }
                         │
4.                       ▼
               Publish to MQTT topic: temi/command
                         │
5. Relay App  ←── subscribes to temi/command ────────────  receives JSON
                         │
6.                       ▼
               Resolve active location by locationSource:
                 "LocationEngine" / "Manual" → read "location" object
                 "GPS"                       → read "gpsLocation" (fallback to "location")
               Extract hkE, hkN from resolved object.
                         │
7.                       ▼
               CoordinateMapper: HK1980 (hkE, hkN) ──affine──→ temi (x, y, yaw)
               Call  robot.repose(position)
                         │
8.                       ▼
               OnReposeStatusChanged callback:
               IDLE → START → GOING → COMPLETE ✓
                         │
9.                       ▼
               Publish status to MQTT topic: temi/status
               { "status": "repose_4", "detail": "Repose Complete" }
                         │
10. Phone App ←── subscribes to temi/status ─────────────  displays result
```

## MQTT Topics

| Topic | Direction | Payload | Description |
|-------|-----------|---------|-------------|
| `temi/command` | Phone → Relay | `{ "action": "update_location", "location_data": { ... } }` | Location update with source-resolved position |
| `temi/status` | Relay → Phone | `{ "status": "...", "detail": "...", "timestamp": ... }` | Repose status, calibration status, or error feedback |

### `locationSource` Values

| Value | Active Object | Meaning |
|-------|---------------|:--------|
| `"LocationEngine"` | `location` | Zeelo indoor positioning (high accuracy in coverage areas) |
| `"GPS"` | `gpsLocation` | GPS fallback (lower accuracy, outdoor) |
| `"Manual"` | `location` | Manually entered coordinates from the phone app |

## Project Structure

```
temi-mqtt-system/
├── temi-phone-app/              ← Android app (phone) — Zeelo location sender
│   ├── README.md                ← 📖 Phone app documentation
│   ├── build.gradle
│   ├── zeelolitesdk.gradle
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── zeelo_location_prod_2.2.0.aar
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/temiphone/
│           ├── ControllerActivity.kt
│           ├── LocationApiClient.kt
│           ├── MqttManager.kt
│           └── Config.kt
│
├── temi-pad-relay/              ← Android app (temi pad) — repose relay
│   ├── README.md                ← 📖 Relay app documentation
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/temirelay/
│           ├── RelayActivity.kt
│           ├── CoordinateMapper.kt
│           ├── MqttRelayManager.kt
│           └── Config.kt
│
└── mosquitto/                   ← MQTT broker (Docker)
    ├── README.md                ← 📖 Broker documentation
    ├── docker-compose.yml
    ├── mosquitto.conf
    ├── setup.sh
    └── password.txt
```

## Quick Start

> **Install the sub-systems in the order listed below.** The MQTT Broker must be running before either Android app can connect.

### 1. MQTT Broker

```bash
cd temi-mqtt-system/mosquitto
chmod +x setup.sh
./setup.sh
```

→ See [mosquitto/README.md](temi-mqtt-system/mosquitto/README.md) for full setup guide.

### 2. Phone App

1. Open `temi-mqtt-system/temi-phone-app` in Android Studio
2. Set broker IP in `Config.kt` and Zeelo API key in `AndroidManifest.xml`
3. Sync Gradle, run on your phone

→ See [temi-phone-app/README.md](temi-mqtt-system/temi-phone-app/README.md) for full setup guide.

### 3. Relay App

1. Open `temi-mqtt-system/temi-pad-relay` in Android Studio
2. Set broker IP in `Config.kt`
3. Sync Gradle, deploy to the temi robot's pad
4. **Run on-device calibration** (two anchor points) before repose will work

→ See [temi-pad-relay/README.md](temi-mqtt-system/temi-pad-relay/README.md) for full setup & calibration guide.

## Prerequisites

| Dependency | Version | Required For |
|------------|---------|--------------|
| [Docker](https://docs.docker.com/get-docker/) + [Compose](https://docs.docker.com/compose/install/) | 20.10+ / v2+ | MQTT Broker |
| [Android Studio](https://developer.android.com/studio) | Hedgehog+ | Both apps |
| JDK | 8+ | Both apps |
| Android SDK | API 34 (compileSdk), API 24 (minSdk) | Both apps |
| A temi robot (Launcher v134+) | — | Relay app |
| An Android phone (with gyroscope + magnetometer) | Android 7.0+ | Phone app |
| Zeelo API Key | — | Phone app |

## Key Concepts

### Location Source Resolution

Both the phone app and relay app resolve the active position based on `locationSource`:
- **`"LocationEngine"`** → Zeelo indoor positioning → uses `location` object
- **`"GPS"`** → GPS fallback → uses `gpsLocation` object
- **`"Manual"`** → User-entered coordinates → uses `location` object

### Coordinate Mapping

The relay uses a **calibrated 2-D affine transform** (`CoordinateMapper`) to convert Zeelo HK1980 grid coordinates (hkE, hkN in metres) to temi map coordinates (x, y in metres). Two anchor points must be captured on-site to establish the transform.

### Repose

The temi SDK `repose(Position)` method tells the robot "you are at this position on your map". This is used to update the robot's believed location based on the Zeelo indoor positioning data.

## Important Notes

- **Calibration required:** The relay's coordinate mapper must be calibrated on-site before repose will work. See the [relay README calibration section](temi-mqtt-system/temi-pad-relay/README.md#calibration-procedure).
- **Zeelo API key:** Must be obtained from Zeelo and configured in `AndroidManifest.xml`.
- **Network:** All three sub-systems must be on the same network (or have network routing between them).
- **MQTT credentials:** Default is `temi` / `temi2026`. Change in `mosquitto/setup.sh` before first run for production use.
- **Device sensors:** Zeelo SDK requires gyroscope + magnetometer; not all budget phones have these.
- **Network:** Both apps and the MQTT broker must be reachable on the same network.