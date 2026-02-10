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
| `temi/command` | Phone → Relay | `{ "action": "update_location", "location_data": { "location": {...}, "gpsLocation": {...}, "locationSource": "...", "direction": ..., "timestamp": ... } }` | Location update; `locationSource` indicates which object holds the active position |
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
│   ├── build.gradle
│   ├── zeelolitesdk.gradle      ← Zeelo SDK dependency config
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── zeelo_location_prod_2.2.0.aar
│   └── src/main/
│       ├── AndroidManifest.xml  ← permissions + Zeelo API key
│       ├── java/com/example/temiphone/
│       │   ├── ControllerActivity.kt   ← UI: auto-poll + manual input, source display
│       │   ├── LocationApiClient.kt    ← Zeelo SDK wrapper, source-aware active location
│       │   ├── MqttManager.kt         ← MQTT publish/subscribe
│       │   └── Config.kt              ← broker URL, topics, poll interval
│       └── res/layout/activity_controller.xml
│
├── temi-pad-relay/              ← Android app (temi pad) — repose relay
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/temirelay/
│       │   ├── RelayActivity.kt       ← source resolution → maps → calls repose()
│       │   ├── CoordinateMapper.kt    ← HK1980 → temi affine transform + calibration
│       │   ├── MqttRelayManager.kt    ← MQTT client
│       │   └── Config.kt             ← broker URL, topics, mapping settings
│       └── res/layout/activity_relay.xml
│
└── mosquitto/                   ← MQTT broker (Docker)
    ├── docker-compose.yml
    ├── mosquitto.conf
    ├── setup.sh
    └── password.txt
```

## Phone App — Features

The phone app has **three location sources** and resolves the active one automatically:

| Mode | `locationSource` | How it works |
|------|------------------|--------------|
| **Auto-Poll (Zeelo Indoor)** | `"LocationEngine"` | Zeelo SDK tracks indoor position using its fingerprinting database. Active location is read from the `location` object. |
| **Auto-Poll (GPS Fallback)** | `"GPS"` | When outside Zeelo coverage, the SDK falls back to GPS. Active location is read from the `gpsLocation` object. |
| **Manual Input** | `"Manual"` | User enters latitude, longitude, HK1980 Easting/Northing, and floor level manually and taps "Send". |

### Source Resolution (`LocationApiClient`)

The SDK callback provides both `location` and `gpsLocation` objects. The `locationSource` field determines which one is the **active** position:

- `isIndoorSource()` — returns `true` when source is `"LocationEngine"`
- `getActiveLatitude()`, `getActiveLongitude()`, `getActiveHkE()`, `getActiveHkN()`, `getActiveFloorLevel()` — return values from the active source
- `exportCurrentLocationAsJson()` — places the resolved active data into the `"location"` key and includes raw `"gpsLocation"` when available

The UI displays the current source label and coordinates from the resolved active location.

## Relay App — Features

The relay app handles **source resolution**, **coordinate mapping**, and **repose**:

1. Subscribes to `temi/command`
2. On receiving `update_location` → checks `locationSource`:
   - `"LocationEngine"` or `"Manual"` → reads the `location` object
   - `"GPS"` → reads the `gpsLocation` object (falls back to `location` if absent)
3. Extracts `hkE`, `hkN` from the resolved object
4. **CoordinateMapper** transforms HK1980 → temi map coordinates using a calibrated affine transform
5. Calls `robot.repose(Position(x, y, yaw))` with the mapped position
6. Listens to `OnReposeStatusChangedListener` and publishes repose status back to `temi/status`

### Coordinate Mapping & Calibration

The Zeelo SDK reports position in **HK1980 Grid** (hkE / hkN in metres), while the temi SDK uses an **internal map frame** (also in metres, origin at the home-base / charging dock). A 2-D affine transform (translation + rotation + uniform scale) maps between the two.

**Calibration Steps (one-time, on-site):**

1. Start both the phone app and the relay app. Ensure the phone is sending Zeelo location data.
2. **Drive the robot to Anchor A** — a clearly identifiable spot.
3. Tap **"Capture A"** on the relay app. This records the robot's temi Position and the latest Zeelo HK1980 location.
4. **Drive the robot to Anchor B** — a second spot, at least 1–2 metres away from A.
5. Tap **"Capture B"** on the relay app.
6. Tap **"Calibrate"**. The app computes the affine transform and saves it to SharedPreferences.
7. From now on, every incoming Zeelo location is automatically mapped before calling `repose()`.

> **Tips:**
> - Choose anchors that are far apart (>2 m) for better accuracy.
> - Calibration is persisted across app restarts.
> - Tap "Reset Calibration" to start over.
> - The relay UI shows the robot's current position, latest Zeelo location, and the mapped temi position for debugging.

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
| `USE_HK1980_MAPPING` | `true` | Use HK1980 coordinates for mapping (requires calibration) |
| `DEFAULT_YAW` | `0f` | Fallback yaw when direction data is unavailable |

## Important Notes

- **Location source resolution:** The `locationSource` field in the Zeelo SDK callback determines which position object is active. `"LocationEngine"` uses the `location` object (Zeelo indoor positioning), `"GPS"` uses the `gpsLocation` object (GPS fallback). Both the phone app and relay app resolve this automatically.
- **Coordinate mapping:** The relay uses a **calibrated affine transform** (`CoordinateMapper`) to convert Zeelo HK1980 grid coordinates to temi map coordinates. You must run the on-device calibration (two anchor points) before repose will work correctly. See the "Coordinate Mapping & Calibration" section above.
- **Zeelo API key:** Must be obtained from Zeelo and set in `AndroidManifest.xml` before the phone app can get indoor positioning.
- **Device sensors:** Zeelo SDK requires gyroscope + magnetometer; not all budget phones have these.
- **Network:** Both apps and the MQTT broker must be reachable on the same network.