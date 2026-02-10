# Temi Phone App — Location Sender

Android app that runs on a phone. It uses the **Zeelo Indoor Location SDK** to continuously track position and publishes the location to the temi relay via **MQTT**.

## Architecture

```
┌──────────────────────────────────────────┐
│              Phone App                    │
│                                          │
│  ┌──────────────────┐    ┌────────────┐  │     MQTT
│  │ LocationApiClient │    │ MqttManager│──│──→ temi/command
│  │                  │    │            │←─│─── temi/status
│  │  Zeelo SDK       │    └────────────┘  │
│  │  (ZeeloLocation  │          ▲         │
│  │   Manager)       │          │         │
│  │                  │──────────┘         │
│  │  Callbacks:      │   exportCurrent    │
│  │  • location      │   LocationAsJson() │
│  │  • gpsLocation   │                    │
│  │  • direction     │                    │
│  └──────────────────┘                    │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │       ControllerActivity         │    │
│  │  • Auto-poll timer (every Ns)    │    │
│  │  • Manual location input         │    │
│  │  • Source-aware display          │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

## Features

### Three Location Sources

| Mode | `locationSource` | Description |
|------|------------------|-------------|
| **Auto-Poll (Indoor)** | `"LocationEngine"` | Zeelo SDK tracks indoor position using BLE fingerprinting. Active location is read from the `location` object. |
| **Auto-Poll (GPS)** | `"GPS"` | When outside Zeelo coverage, the SDK falls back to GPS. Active location is read from the `gpsLocation` object. |
| **Manual Input** | `"Manual"` | User enters latitude, longitude, HK1980 E/N, and floor level manually. |

### Source Resolution

The Zeelo SDK callback always provides both `location` and `gpsLocation` objects. The `locationSource` field determines which one holds the **active** position:

| `locationSource` Value | Active Object | Helpers |
|------------------------|---------------|---------|
| `"LocationEngine"` | `location` | `isIndoorSource()` → `true` |
| `"GPS"` | `gpsLocation` | `isIndoorSource()` → `false` |

**Active-location helper methods** in `LocationApiClient`:

- `getActiveLatitude()` / `getActiveLongitude()` — resolved from the active source
- `getActiveHkE()` / `getActiveHkN()` — HK1980 grid coordinates from the active source
- `getActiveFloorLevel()` — floor level from the active source
- `exportCurrentLocationAsJson()` — builds the full JSON payload with the resolved `"location"` key

### Auto-Poll Timer

- Configurable interval (default: 10 seconds, set via `ZEELO_POLL_INTERVAL_MS` or the UI input)
- Start / Stop buttons on the UI
- Publishes immediately on start, then repeats at the interval
- Each publish calls `exportCurrentLocationAsJson()` which resolves the active source

### Manual Location Input

Fields: Latitude, Longitude, HK1980 Easting, HK1980 Northing, Floor Level  
Sets `locationSource` to `"Manual"` in the published JSON.

## Prerequisites

| Dependency | Version | Notes |
|------------|---------|-------|
| [Android Studio](https://developer.android.com/studio) | Hedgehog+ | IDE |
| JDK | 8+ | Kotlin compilation |
| Android SDK | API 34 (compileSdk), API 24 (minSdk) | Android 7.0+ |
| An Android phone | with gyroscope + magnetometer | Required by Zeelo SDK |
| Zeelo API Key | — | Obtain from Zeelo |
| MQTT Broker | Running | See [mosquitto/README.md](../mosquitto/README.md) |

## Installation

### 1. Open in Android Studio

Open the `temi-mqtt-system/temi-phone-app` directory as an Android project.

### 2. Configure MQTT Broker IP

Edit `Config.kt`:

```kotlin
const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
```

Replace `YOUR_SERVER_IP` with the IP of the machine running Mosquitto.

### 3. Configure Zeelo API Key

Edit `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.cherrypicks.zeelo.sdk.api_key"
    android:value="YOUR_ZEELO_API_KEY_HERE" />
```

Replace `YOUR_ZEELO_API_KEY_HERE` with the API key obtained from Zeelo.

### 4. Verify AAR File

Ensure the Zeelo SDK AAR is present at:

```
temi-phone-app/libs/zeelo_location_prod_2.2.0.aar
```

This file is included in the repository and referenced by `build.gradle`.

### 5. Sync & Run

1. Sync Gradle in Android Studio
2. Connect your phone via USB
3. Run the app

> **Note:** Wait ~5 seconds after startup for the Zeelo SDK to produce accurate positioning data.

## Configuration

### Config.kt

| Constant | Default | Description |
|----------|---------|-------------|
| `MQTT_BROKER_URL` | `tcp://YOUR_SERVER_IP:1883` | MQTT broker address |
| `MQTT_USERNAME` | `temi` | MQTT username |
| `MQTT_PASSWORD` | `temi2026` | MQTT password |
| `MQTT_CLIENT_ID` | `temi-phone-controller` | MQTT client identifier |
| `TOPIC_COMMAND` | `temi/command` | Topic for publishing location to relay |
| `TOPIC_STATUS` | `temi/status` | Topic for receiving repose status from relay |
| `ZEELO_ENABLE_HK1980` | `true` | Enable HK1980 grid coordinate output |
| `ZEELO_POLL_INTERVAL_MS` | `10000` (10s) | Default auto-poll interval in milliseconds |

### AndroidManifest.xml — Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | MQTT network access |
| `ACCESS_NETWORK_STATE` | Network status detection |
| `WAKE_LOCK` | Keep MQTT service alive |
| `ACCESS_FINE_LOCATION` | GPS access (Zeelo SDK) |
| `ACCESS_COARSE_LOCATION` | Approximate location (Zeelo SDK) |
| `ACCESS_BACKGROUND_LOCATION` | Background location updates |
| `CHANGE_NETWORK_STATE` | Network switching (Zeelo SDK) |

## File Structure

```
temi-phone-app/
├── build.gradle                ← Dependencies: Zeelo AAR, Paho MQTT, Gson, EventBus
├── zeelolitesdk.gradle         ← Zeelo SDK Gradle configuration (applied by build.gradle)
├── proguard-rules.pro          ← ProGuard rules for Zeelo, MQTT, Gson, OkHttp
├── libs/
│   └── zeelo_location_prod_2.2.0.aar   ← Zeelo Location SDK binary
└── src/main/
    ├── AndroidManifest.xml     ← Permissions + Zeelo API key + MqttService
    └── java/com/example/temiphone/
        ├── ControllerActivity.kt   ← Main UI: auto-poll, manual input, status display
        ├── LocationApiClient.kt    ← Zeelo SDK wrapper, source resolution, JSON export
        ├── MqttManager.kt         ← MQTT client (publish commands, subscribe status)
        └── Config.kt              ← Constants: broker URL, topics, poll interval
```

## Source Files

### ControllerActivity.kt

Main activity and UI controller.

- **`initializeZeeloSDK()`** — Creates `LocationApiClient`, registers callback for live updates
- **`startPolling()` / `stopPolling()`** — Manages the auto-poll timer via `Handler`
- **`publishZeeloLocation()`** — Calls `exportCurrentLocationAsJson()`, wraps in `update_location` command, publishes via MQTT
- **`publishManualLocation()`** — Reads manual input fields, builds JSON with `locationSource: "Manual"`, publishes
- **`updateZeeloLocationDisplay()`** — Shows the active source label and resolved coordinates in the UI
- **`setupMqtt()`** — Initializes `MqttManager`, listens for `temi/status` responses

### LocationApiClient.kt

Zeelo SDK wrapper with source-aware location resolution.

- **`initializeZeeloSDK(callback)`** — Initializes `ZeeloLocationManager`, enables HK1980, sets up all listeners, starts the location service
- **`setupLocationListener()`** — Receives `location`, `gpsLocation`, `locationSource` from Zeelo SDK callback
- **`setupZoneListener()`** — Receives geofence zone in/out events
- **`setupDirectionListener()`** — Receives device heading/direction updates
- **`isIndoorSource()`** — Returns `true` if `locationSource == "LocationEngine"`
- **`getActive*()`** — Family of helpers that return coordinates from the active source
- **`exportCurrentLocationAsJson()`** — Builds JSON with resolved `"location"` key + raw `"gpsLocation"` + `"locationSource"` + `"direction"` + `"timestamp"`

### MqttManager.kt

MQTT client using Eclipse Paho `MqttAsyncClient`.

- Auto-reconnect enabled (`isAutomaticReconnect = true`)
- Subscribes to `temi/status` on connect
- Publishes to `temi/command` via `publishCommand()`
- Connection and message listeners for UI updates

### Config.kt

Immutable configuration constants. All MQTT and Zeelo settings are defined here.

## MQTT Message Format

### Published: `temi/command`

```json
{
  "action": "update_location",
  "location_data": {
    "location": {
      "latitude": 22.250000,
      "longitude": 113.560000,
      "hkE": 773218.82,
      "hkN": 812570.95,
      "floorLevel": 7,
      "isOutDoor": false,
      "geofenceName": "BLDG0001",
      "geofenceId": "zone-001",
      "floorName": "7F",
      "coverageArea": true
    },
    "gpsLocation": {
      "latitude": 22.250001,
      "longitude": 113.560002,
      "hkE": 773218.90,
      "hkN": 812570.98,
      "floorLevel": 0,
      "horizontalAccuracy": 12.5,
      "direction": 180.0,
      "directionAccuracy": 45.0
    },
    "locationSource": "LocationEngine",
    "direction": 54.96,
    "timestamp": 1707560000000
  }
}
```

### Received: `temi/status`

```json
{
  "status": "repose_4",
  "detail": "Repose Complete ✓",
  "timestamp": 1707560010000
}
```

## Zeelo SDK Integration Details

### AAR Setup

The Zeelo SDK is distributed as a local AAR file. The integration uses:

1. **`zeelolitesdk.gradle`** — Applied via `apply from:` in `build.gradle`
2. **`flatDir { dirs 'libs' }`** — Tells Gradle to look for AARs in the `libs/` directory
3. **`implementation files('libs/zeelo_location_prod_2.2.0.aar')`** — Direct AAR dependency

### SDK Dependencies

The Zeelo SDK requires these transitive dependencies:

| Library | Version | Purpose |
|---------|---------|---------|
| `org.greenrobot:eventbus` | 3.3.1 | Internal event bus used by Zeelo SDK |
| `androidx.constraintlayout:constraintlayout` | 2.0.1 | Layout dependency |

### SDK Classes Used

| Class | Package | Purpose |
|-------|---------|---------|
| `ZeeloLocationManager` | `zeelo.location.manager` | Main SDK entry point |
| `ZeeloLocationCallback` | `zeelo.location.callback` | Location update callback |
| `ZeeloLocationZoneCallback` | `zeelo.location.callback` | Geofence zone events |
| `ZeeloLocationDirectionCallback` | `zeelo.location.callback` | Device heading updates |
| `Location` | `zeelo.location.data` | Indoor location data (lat, lon, hkE, hkN, floor, geofence) |
| `GPSLocation` | `zeelo.location.data` | GPS location data (lat, lon, hkE, hkN, accuracy, direction) |
| `LocationSource` | `zeelo.location.data` | Source indicator (`"LocationEngine"` or `"GPS"`) |

### HK1980 Grid System

The SDK supports **Hong Kong 1980 Grid** (HK1980) coordinate output, enabled via:

```kotlin
locationManager?.enableHK80(true)
```

This adds `hkE` (Easting) and `hkN` (Northing) in metres to both `Location` and `GPSLocation` objects. These coordinates are used by the relay's `CoordinateMapper` for the affine transform to temi map coordinates.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Waiting for Zeelo SDK..." persists | Ensure the phone has gyroscope + magnetometer sensors. Wait 5–10 seconds. Check API key. |
| MQTT: Disconnected | Verify broker IP in `Config.kt`. Ensure broker is running. Check network connectivity. |
| Location shows `0.0, 0.0` | Zeelo SDK hasn't received a fix yet. Move around to help the SDK calibrate. |
| `locationSource` shows `"Unknown"` | SDK hasn't provided a source yet — this is normal during initialization. |
| Build error: AAR not found | Verify `libs/zeelo_location_prod_2.2.0.aar` exists. Sync Gradle. |
| Missing EventBus class | Ensure `org.greenrobot:eventbus:3.3.1` is in `build.gradle` dependencies. |
| GPS permission denied | Grant location permissions in Android Settings → Apps → Temi Controller → Permissions. |

## Related

- [Root README](../../README.md) — System architecture & data flow
- [MQTT Broker](../mosquitto/README.md) — Broker setup
- [Temi Pad Relay](../temi-pad-relay/README.md) — Relay app (receives location, calls repose)
