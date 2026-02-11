# Temi Pad Relay — Location Receiver & Repose Controller

Android app that runs on the **temi robot's tablet**. It listens for location updates via MQTT, resolves the active location source, maps coordinates using a calibrated affine transform, and calls `robot.repose()` to relocate the robot on its internal map.

## Architecture

```
    MQTT                         Relay App (temi pad)
temi/command                ┌───────────────────────────────────┐
    │                       │                                   │
    ▼                       │  ┌────────────────┐               │
┌──────────────┐            │  │ MqttRelayManager│              │
│ JSON payload │──────────→ │  │ subscribes to   │              │
│              │            │  │ temi/command     │              │
└──────────────┘            │  └───────┬─────────┘              │
                            │          │ message                │
                            │          ▼                        │
                            │  ┌────────────────────────┐       │
                            │  │   RelayActivity        │       │
                            │  │   handleCommand()      │       │
                            │  │                        │       │
                            │  │  1. Parse JSON         │       │
                            │  │  2. Resolve source     │       │
                            │  │     (LocationEngine/   │       │
                            │  │      GPS/Manual)       │       │
                            │  │  3. Extract hkE, hkN   │       │
                            │  └───────┬────────────────┘       │
                            │          │                        │
                            │          ▼                        │
                            │  ┌─────────────────────┐          │
                            │  │  CoordinateMapper   │          │
                            │  │  HK1980 → temi map  │          │
                            │  │  (affine transform) │          │
                            │  └───────┬─────────────┘          │
                            │          │ Position(x, y, yaw)    │
                            │          ▼                        │
                            │  ┌─────────────────────┐          │
                            │  │  robot.repose()     │──→ temi  │
                            │  │                     │   robot  │
                            │  │  OnReposeStatus     │          │
                            │  │  ChangedListener    │          │
                            │  └───────┬─────────────┘          │
                            │          │ status                 │
                            │          ▼                        │
                            │  publishes temi/status            │
                            └───────────────────────────────────┘
```

## Features

### Source Resolution

The relay resolves the active location object based on `locationSource`:

| `locationSource` | Active Object | Behavior |
|------------------|---------------|----------|
| `"LocationEngine"` | `location` | Zeelo indoor positioning (high accuracy) |
| `"GPS"` | `gpsLocation` | GPS fallback (falls back to `location` if `gpsLocation` absent) |
| `"Manual"` | `location` | Manually entered coordinates |

### Coordinate Mapping

Converts Zeelo **HK1980 Grid** coordinates (hkE, hkN in metres) to temi **internal map frame** coordinates (x, y in metres) using a calibrated 2-D affine transform:

$$
\begin{pmatrix} x_{\text{temi}} \\ y_{\text{temi}} \end{pmatrix} = s \cdot \begin{pmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{pmatrix} \begin{pmatrix} E_{\text{HK}} \\ N_{\text{HK}} \end{pmatrix} + \begin{pmatrix} t_x \\ t_y \end{pmatrix}
$$

Where $s$ = scale, $\theta$ = rotation, $(t_x, t_y)$ = translation offset.

The transform is computed from **two calibration anchor points** known in both coordinate systems.

### Repose

Calls `robot.repose(Position(x, y, yaw, tiltAngle))` via the temi SDK. The robot uses this to update its believed position on its internal map.

Repose status transitions:

```
IDLE → REPOSE_REQUIRED → REPOSING_START → REPOSING_GOING → REPOSING_COMPLETE ✓
                                                        → REPOSING_OBSTACLE_DETECTED
                                                        → REPOSING_ABORT
```

Status updates are published to `temi/status`.

### On-Device Calibration UI

The app includes a calibration interface for establishing the coordinate transform:

- **Robot Position Display** — Shows the robot's current temi map coordinates (x, y, yaw)
- **Zeelo Position Display** — Shows the latest received HK1980 coordinates (hkE, hkN)
- **Capture A / Capture B** — Records paired anchor points (temi pos + Zeelo pos)
- **Calibrate** — Computes the affine transform from the two anchors
- **Reset Calibration** — Clears all calibration data

## Prerequisites

| Dependency | Version | Notes |
|------------|---------|-------|
| [Android Studio](https://developer.android.com/studio) | Hedgehog+ | IDE |
| JDK | 8+ | Kotlin compilation |
| Android SDK | API 34 (compileSdk), API 24 (minSdk) | |
| A temi robot | Launcher v134+ | Required for `repose()` API |
| MQTT Broker | Running | See [mosquitto/README.md](../mosquitto/README.md) |

## Installation

### 1. Open in Android Studio

Open the `temi-mqtt-system/temi-pad-relay` directory as an Android project.

### 2. Configure MQTT Broker IP

Edit `Config.kt`:

```kotlin
const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
```

Replace `YOUR_SERVER_IP` with the IP of the machine running Mosquitto.

### 3. Sync & Deploy

1. Sync Gradle in Android Studio
2. Connect to the temi robot's tablet via USB or wireless ADB:
   ```bash
   adb connect <temi-robot-ip>:5555
   ```
3. Run the app

> **Note:** The app registers as a kiosk app on temi (`com.robotemi.sdk.metadata.KIOSK = TRUE`).

## Configuration

### Config.kt

| Constant | Default | Description |
|----------|---------|-------------|
| `MQTT_BROKER_URL` | `tcp://YOUR_SERVER_IP:1883` | MQTT broker address |
| `MQTT_USERNAME` | `temi` | MQTT username |
| `MQTT_PASSWORD` | `temi2026` | MQTT password |
| `MQTT_CLIENT_ID` | `temi-pad-relay` | MQTT client identifier |
| `TOPIC_COMMAND` | `temi/command` | Topic for receiving location updates |
| `TOPIC_STATUS` | `temi/status` | Topic for publishing repose status |
| `USE_HK1980_MAPPING` | `true` | Use HK1980 coordinates + affine transform (requires calibration) |
| `DEFAULT_YAW` | `0f` | Fallback yaw (degrees) when direction is unavailable |

## Background / Keep-Alive

The relay app uses multiple mechanisms to stay alive on the temi tablet:

### Kiosk Mode

The app declares `com.robotemi.sdk.metadata.KIOSK = TRUE` in AndroidManifest.xml. Temi treats kiosk apps as the primary foreground app and will not allow the user to navigate away.

### Keep Screen On

`FLAG_KEEP_SCREEN_ON` is set on the Activity window so the temi tablet screen never turns off automatically.

### Foreground Service

`MqttForegroundService` runs as a persistent foreground service with a low-priority notification. This prevents Android from killing the MQTT connection. Uses `START_STICKY` for automatic restart.

### Partial Wake Lock

A `PARTIAL_WAKE_LOCK` inside the foreground service keeps the CPU running even if the screen is somehow turned off.

### Battery Optimization Exemption

On first launch, the app prompts to disable Doze battery optimization, preventing network throttling during idle.

### AndroidManifest.xml — Permissions

| Permission | Purpose |
|------------|----------|
| `INTERNET` | MQTT network access |
| `ACCESS_NETWORK_STATE` | Network status detection |
| `WAKE_LOCK` | Keep CPU awake while screen is off |
| `FOREGROUND_SERVICE` | Run persistent foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Foreground service type for MQTT relay |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt user to exempt app from Doze |
| `POST_NOTIFICATIONS` | Required on Android 13+ for foreground service notification |

### Additional Device-Specific Steps

While the temi tablet is typically less aggressive than phone OEMs, the following may still be relevant:

| Setting | Action |
|---------|--------|
| **Battery Optimization** | Settings → Battery → Exempt "Temi Relay" from optimization |
| **Lock in Recent Tasks** | In multitask view, lock the app card to prevent "clear all" from killing it |
| **temi Settings** | Ensure the app is set as the active kiosk app in temi's Settings → Apps |

## File Structure

```
temi-pad-relay/
├── build.gradle                ← Dependencies: temi SDK, Paho MQTT, Gson
└── src/main/
    ├── AndroidManifest.xml     ← Permissions, MqttService, ForegroundService, KIOSK meta-data
    └── java/com/example/temirelay/
        ├── RelayActivity.kt          ← Main UI: command handler, calibration, repose
        ├── CoordinateMapper.kt       ← HK1980 → temi affine transform + persistence
        ├── MqttRelayManager.kt       ← MQTT client (subscribe commands, publish status)
        ├── MqttForegroundService.kt  ← Foreground service with WakeLock for keep-alive
        └── Config.kt                 ← Constants: broker URL, topics, mapping settings
```

## Source Files

### RelayActivity.kt

Main activity — handles MQTT messages, calibration UI, and repose calls.

- **`handleCommand(message)`** — Parses JSON, resolves `locationSource`, extracts hkE/hkN, maps via `CoordinateMapper`, calls `repose()`
- **`captureAnchor(label)`** — Records the robot's current temi position + latest Zeelo HK1980 coords as anchor A or B
- **`setupCalibrationButtons()`** — Wires Capture A, Capture B, Calibrate, Reset buttons
- **`refreshCalibrationUI()`** — Updates calibration status display
- **`updateRobotPositionDisplay()`** — Shows current robot x/y/yaw from `Robot.getInstance().getPosition()`
- **`onReposeStatusChanged(status, description)`** — Callback for repose lifecycle events, publishes to `temi/status`
- **`requestBatteryOptimizationExemption()`** — Prompts user to exempt app from Doze battery optimization

### MqttForegroundService.kt

Persistent foreground service for keep-alive.

- **`start(context)` / `stop(context)`** — Static helpers to start/stop the service
- **`acquireWakeLock()`** — Acquires `PARTIAL_WAKE_LOCK` to keep CPU running while screen is off
- **`buildNotification(text)`** — Creates low-priority persistent notification
- **`updateNotification(text)`** — Updates notification text at runtime
- Uses `START_STICKY` to auto-restart if killed by the system

### CoordinateMapper.kt

Performs the HK1980 → temi map coordinate transformation.

- **`Anchor`** — Data class holding paired coordinates: `(hkE, hkN, temiX, temiY)`
- **`calibrate(a, b)`** — Computes scale, rotation, and offset from two anchor points. Requires anchors to be ≥0.5 m apart. Returns `true` on success.
- **`toTemiPosition(hkE, hkN, yawDeg)`** — Applies the transform and returns a temi `Position`. Returns `null` if not calibrated.
- **`resetCalibration()`** — Clears all calibration data
- **`getCalibrationSummary()`** — Returns a human-readable summary string
- **Persistence** — Calibration parameters (scale, rotation, offset, anchors) are saved to `SharedPreferences` and restored on app start

### MqttRelayManager.kt

MQTT client using Eclipse Paho `MqttAsyncClient`.

- Subscribes to `temi/command` on connect
- Publishes to any topic via `publish(topic, message)`
- Auto-reconnect enabled
- Connection and message listeners for UI integration

### Config.kt

Immutable configuration constants for MQTT and coordinate mapping.

## Calibration Procedure

The coordinate mapper must be calibrated before the relay can convert Zeelo locations to temi positions. This is a **one-time, on-site** procedure.

### Step-by-Step

1. **Start both apps.** Ensure the phone app is sending Zeelo location data (auto-poll running).

2. **Drive the robot to Anchor A** — choose a clearly identifiable spot in the environment.

3. **Tap "Capture A"** on the relay app.
   - This records:
     - The robot's current temi position from `Robot.getInstance().getPosition()` → `(temiX, temiY)`
     - The latest Zeelo HK1980 location received via MQTT → `(hkE, hkN)`
   - The button changes to "A ✓"

4. **Drive the robot to Anchor B** — a second spot, at least 1–2 metres away from A.

5. **Tap "Capture B"** on the relay app.
   - Same recording process as step 3.
   - The "Calibrate" button becomes enabled.

6. **Tap "Calibrate"**
   - The app computes the affine transform parameters: scale, rotation angle, and translation offset.
   - Calibration is saved to `SharedPreferences` and persists across app restarts.
   - Status shows "Calibrated ✓"

7. **Done!** Every incoming Zeelo location will now be automatically mapped to temi coordinates before calling `repose()`.

### Tips

- **Distance matters:** Choose anchors that are far apart (>2 m) for better accuracy. The minimum is 0.5 m.
- **Persistence:** Calibration survives app restarts. No need to recalibrate unless the temi's map changes.
- **Reset:** Tap "Reset Calibration" to clear all data and start over.
- **Debugging:** The relay UI displays:
  - Robot's current temi position
  - Latest Zeelo HK1980 coordinates
  - The mapped temi position after transform
  - Repose status

### When to Recalibrate

- After the temi's map is rescanned or the home-base is moved
- If the robot consistently repositions to incorrect locations
- If the Zeelo installation anchors are reconfigured

## Command Processing Flow

When a message arrives on `temi/command`:

```
1. Parse JSON → extract "action" (must be "update_location")
2. Read "location_data" object
3. Read "locationSource" string
4. Resolve active object:
   └─ "GPS" → use "gpsLocation" (fallback to "location")
   └─ else  → use "location"
5. Extract hkE, hkN from resolved object
6. Cache hkE, hkN for calibration capture
7. If USE_HK1980_MAPPING && calibrated:
   └─ CoordinateMapper.toTemiPosition(hkE, hkN, direction) → Position
8. Else if not calibrated:
   └─ Log warning, skip repose
9. Call robot.repose(position)
10. Publish status to temi/status
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `com.robotemi:sdk` | 0.10.77 | temi robot SDK — `repose()`, `getPosition()`, listeners |
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3` | 1.2.5 | MQTT client |
| `org.eclipse.paho:org.eclipse.paho.android.service` | 1.1.1 | MQTT Android service |
| `com.google.code.gson:gson` | 2.10.1 | JSON parsing |

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Robot not ready" | Wait for temi SDK initialization. The robot must complete startup. |
| "Not calibrated – calibrate first" | Run the calibration procedure (see above). |
| "No Zeelo location received yet" | Ensure the phone app is running and publishing location data. |
| MQTT: Disconnected | Verify broker IP in `Config.kt`. Ensure broker is running. Check network. |
| Repose aborted / obstacle detected | Clear the area around the robot. The robot can't repose if blocked. |
| Robot repositions to wrong location | Recalibrate with anchors farther apart. Verify phone is sending accurate data. |
| Anchors too close error | Choose calibration points at least 0.5 m (ideally 2+ m) apart. |
| App crashes on start | Ensure this app runs on the temi robot's tablet, not a regular phone. |
| MQTT disconnects when idle | Ensure foreground service is running (persistent notification visible). Disable battery optimization. See [Background / Keep-Alive](#background--keep-alive). |
| No notification on Android 13+ | Grant notification permission when prompted, or enable in Settings → Apps → Temi Relay → Notifications. |

## Related

- [Root README](../../README.md) — System architecture & data flow
- [MQTT Broker](../mosquitto/README.md) — Broker setup
- [Phone App](../temi-phone-app/README.md) — Location sender (Zeelo SDK)
