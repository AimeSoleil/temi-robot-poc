# temi-robot-poc

A proof-of-concept system that uses **Zeelo indoor location SDK** on a phone to continuously track position, and relays the location to a **temi robot** via MQTT so the robot can **repose** (relocate itself on its map).

## Architecture

```
┌─────────────────────┐       MQTT (temi/command)       ┌──────────────────────┐
│   Phone App         │ ──────────────────────────────→  │   Temi Pad Relay     │
│                     │                                  │                      │
│  Zeelo Location SDK │                                  │  robot.repose(pos)   │
│  (indoor positioning│       MQTT (temi/status)         │                      │
│   or manual input)  │ ←──────────────────────────────  │  OnReposeStatus      │
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

## Data Flow

```
1. Phone App  ─── Zeelo SDK callback or manual input ───→  lat, lon, floor
                         │
2.                       ▼
               Build JSON command:
               {
                 "action": "update_location",
                 "location_data": {
                   "location": { "latitude": 22.25, "longitude": 113.56, "floorLevel": 7, ... },
                   "locationSource": "LocationEngine" | "Manual",
                   "timestamp": 1707560000000
                 }
               }
                         │
3.                       ▼
               Publish to MQTT topic: temi/command
                         │
4. Relay App  ←── subscribes to temi/command ────────────  receives JSON
                         │
5.                       ▼
               Parse lat/lon → Position(x, y, yaw)
               Call  robot.repose(position)
                         │
6.                       ▼
               OnReposeStatusChanged callback:
               IDLE → START → GOING → COMPLETE ✓
                         │
7.                       ▼
               Publish status to MQTT topic: temi/status
               { "status": "repose_4", "detail": "Repose Complete" }
                         │
8. Phone App  ←── subscribes to temi/status ─────────────  displays result
```

## MQTT Topics

| Topic | Direction | Payload | Description |
|-------|-----------|---------|-------------|
| `temi/command` | Phone → Relay | `{ "action": "update_location", "location_data": { ... } }` | Location update that triggers `robot.repose()` |
| `temi/status` | Relay → Phone | `{ "status": "...", "detail": "...", "timestamp": ... }` | Repose status / errors feedback |

## Project Structure

```
temi-mqtt-system/
├── temi-phone-app/              ← Android app (phone) — Zeelo location sender
│   ├── build.gradle
│   ├── zeelolitesdk.gradle      ← Zeelo SDK dependency config
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── zeelo_location_prod_2.2.0.aar
│   └── src/main/
│       ├── AndroidManifest.xml  ← permissions + Zeelo API key
│       ├── java/com/example/temiphone/
│       │   ├── ControllerActivity.kt   ← UI: auto-poll + manual input
│       │   ├── LocationApiClient.kt    ← Zeelo SDK wrapper
│       │   ├── MqttManager.kt         ← MQTT publish/subscribe
│       │   └── Config.kt              ← broker URL, topics, poll interval
│       └── res/layout/activity_controller.xml
│
├── temi-pad-relay/              ← Android app (temi pad) — repose relay
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/temirelay/
│       │   ├── RelayActivity.kt       ← listens for location → calls repose()
│       │   ├── MqttRelayManager.kt    ← MQTT client
│       │   └── Config.kt             ← broker URL, topics
│       └── res/layout/activity_relay.xml
│
└── mosquitto/                   ← MQTT broker (Docker)
    ├── docker-compose.yml
    ├── mosquitto.conf
    ├── setup.sh
    └── password.txt
```

## Phone App — Features

The phone app has **two modes** for sending location data to the relay:

| Mode | How it works |
|------|-------------|
| **Auto-Poll (Zeelo SDK)** | Zeelo SDK tracks indoor position continuously. A configurable timer (default 10 s) reads the latest cached location and publishes it to MQTT. |
| **Manual Input** | User enters latitude, longitude, and floor level manually and taps "Send". |

## Relay App — Features

The relay app is minimal:

1. Subscribes to `temi/command`
2. On receiving `update_location` → parses `lat`, `lon` → calls `robot.repose(Position(lat, lon, 0))`
3. Listens to `OnReposeStatusChangedListener` and publishes repose status back to `temi/status`

## Prerequisites

| Dependency | Version | Required For |
|------------|---------|--------------|
| [Docker](https://docs.docker.com/get-docker/) | 20.10+ | MQTT Broker |
| [Docker Compose](https://docs.docker.com/compose/install/) | v2+ | MQTT Broker |
| [Android Studio](https://developer.android.com/studio) | Hedgehog+ | Both apps |
| JDK | 8+ | Both apps |
| Android SDK | API 34 (compileSdk) | Both apps |
| A temi robot | — | Relay app |
| An Android phone | Android 7.0+ (API 24) | Phone app |
| Zeelo API Key | — | Phone app (obtain from Zeelo) |

## Installation

> **Install the sub-systems in the order listed below.** The MQTT Broker must be running before either Android app can connect.

---

### 1. MQTT Broker (Mosquitto)

Runs via Docker Compose. Provides the message broker that connects the phone app to the temi pad relay.

#### Dependencies

- Docker 20.10+
- Docker Compose v2+
- A server or machine reachable from both the phone and the temi robot on the network

#### Steps

1. **Navigate to the Mosquitto directory:**
   ```bash
   cd temi-mqtt-system/mosquitto
   ```

2. **Make the setup script executable and run it:**
   ```bash
   chmod +x setup.sh
   ./setup.sh
   ```
   This script will:
   - Generate a hashed password file (default user: `temi`, password: `temi2026`)
   - Start the Mosquitto container in the background

   > **Security:** For production use, change the default credentials by editing `setup.sh` before running it, or re-run `mosquitto_passwd` to set a new password.

3. **Verify the broker is running:**
   ```bash
   docker ps
   ```
   You should see a container named `temi-mqtt-broker` running.

4. **Note your server's IP address** — you will need it to configure both Android apps:
   ```bash
   hostname -I
   ```

The broker listens on port **1883** (MQTT) and **9001** (WebSocket).

---

### 2. Temi Phone App (Location Sender)

Android app that runs on your phone. Sends Zeelo indoor location (or manual coordinates) to the relay via MQTT.

#### Key Dependencies

| Library | Purpose |
|---------|---------|
| `zeelo_location_prod_2.2.0.aar` | Zeelo indoor location SDK |
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` | MQTT client |
| `com.google.code.gson:gson:2.10.1` | JSON serialization |
| `org.greenrobot:eventbus:3.3.1` | Required by Zeelo SDK |

#### Steps

1. Open `temi-mqtt-system/temi-phone-app` in Android Studio.

2. Set your MQTT broker IP in `Config.kt`:
   ```kotlin
   const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
   ```

3. Set your Zeelo API key in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.cherrypicks.zeelo.sdk.api_key"
       android:value="YOUR_ZEELO_API_KEY" />
   ```

4. Sync Gradle, then run on your phone.

> **Note:** The Zeelo SDK requires a device with **gyroscope** and **magnetometer** sensors. Wait ~5 seconds after startup for accurate positioning.

---

### 3. Temi Pad Relay App

Android app that runs on the temi robot's pad. It:
- Listens for `update_location` messages via MQTT
- Calls `robot.repose(Position)` to relocate the robot on its map
- Reports repose status back to the phone via MQTT

#### Key Dependencies

| Library | Purpose |
|---------|---------|
| `com.robotemi:sdk:0.10.77` | temi robot SDK |
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5` | MQTT client |
| `com.google.code.gson:gson:2.10.1` | JSON parsing |

#### Steps

1. Open `temi-mqtt-system/temi-pad-relay` in Android Studio.

2. Set your MQTT broker IP in `Config.kt`:
   ```kotlin
   const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
   ```

3. Sync Gradle, then deploy to the temi robot's pad via USB or wireless ADB.

> **Note:** The relay app uses [temi SDK](https://github.com/robotemi/sdk) `0.10.77` and must run on the temi robot's tablet. The `repose()` method requires Launcher v134+.

---

## Configuration

Update `Config.kt` in **both** apps with your MQTT broker IP:
```kotlin
const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
```

Default MQTT credentials (set in `mosquitto/setup.sh`):
- **Username:** `temi`
- **Password:** `temi2026`

### Phone App Config (`temi-phone-app/Config.kt`)

| Constant | Default | Description |
|----------|---------|-------------|
| `MQTT_BROKER_URL` | `tcp://YOUR_SERVER_IP:1883` | Broker address |
| `MQTT_CLIENT_ID` | `temi-phone-controller` | MQTT client ID |
| `TOPIC_COMMAND` | `temi/command` | Topic for sending location to relay |
| `TOPIC_STATUS` | `temi/status` | Topic for receiving repose status |
| `ZEELO_ENABLE_HK1980` | `true` | Enable Hong Kong 1980 grid coordinates |
| `ZEELO_POLL_INTERVAL_MS` | `10000` | Auto-poll interval (milliseconds) |

### Relay App Config (`temi-pad-relay/Config.kt`)

| Constant | Default | Description |
|----------|---------|-------------|
| `MQTT_BROKER_URL` | `tcp://YOUR_SERVER_IP:1883` | Broker address |
| `MQTT_CLIENT_ID` | `temi-pad-relay` | MQTT client ID |
| `TOPIC_COMMAND` | `temi/command` | Topic for receiving location updates |
| `TOPIC_STATUS` | `temi/status` | Topic for publishing repose status |

## Important Notes

- **Coordinate mapping:** The relay currently passes Zeelo lat/lon directly as `Position(x=lat, y=lon, yaw=0)` to `robot.repose()`. If your temi robot's map uses a different coordinate system, you will need to add a mapping/transformation layer.
- **Zeelo API key:** Must be obtained from Zeelo and set in `AndroidManifest.xml` before the phone app can get indoor positioning.
- **Device sensors:** Zeelo SDK requires gyroscope + magnetometer; not all budget phones have these.
- **Network:** Both apps and the MQTT broker must be reachable on the same network.