# temi-robot-poc

A proof-of-concept system for remotely controlling a temi robot via MQTT. The system consists of three components:

## Project Structure

```
temi-mqtt-system/
├── temi-phone-app/          ← Android app for your phone (controller)
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/temiphone/
│   │   │   ├── ControllerActivity.kt
│   │   │   ├── LocationApiClient.kt
│   │   │   ├── MqttManager.kt
│   │   │   └── Config.kt
│   │   └── res/layout/activity_controller.xml
│
├── temi-pad-relay/          ← Android app for the temi pad (relay)
│   ├── build.gradle
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/temirelay/
│   │   │   ├── RelayActivity.kt
│   │   │   ├── MqttRelayManager.kt
│   │   │   └── Config.kt
│   │   └── res/layout/activity_relay.xml
│
└── mosquitto/               ← MQTT broker config
    ├── docker-compose.yml
    ├── mosquitto.conf
    ├── setup.sh
    └── password.txt
```

## Prerequisites

Before installing any sub-system, ensure you have the following:

| Dependency | Version | Required For |
|------------|---------|--------------|
| [Docker](https://docs.docker.com/get-docker/) | 20.10+ | MQTT Broker |
| [Docker Compose](https://docs.docker.com/compose/install/) | v2+ | MQTT Broker |
| [Android Studio](https://developer.android.com/studio) | Hedgehog (2023.1.1) or later | Phone App, Pad Relay App |
| [JDK](https://developer.android.com/build/jdks) | 8+ | Phone App, Pad Relay App |
| Android SDK | API level 34 (compileSdk) | Phone App, Pad Relay App |
| Kotlin plugin | Bundled with Android Studio | Phone App, Pad Relay App |
| A temi robot | — | Pad Relay App |
| An Android phone or emulator | Android 7.0+ (API 24) | Phone App |

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

### 2. Temi Phone App (Controller)

Android app that runs on your phone. Provides a UI to:
- Navigate the robot to saved locations
- Make the robot speak
- Stop robot movement
- View robot status in real time

#### Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin Android extensions |
| `androidx.appcompat:appcompat` | 1.6.1 | Backward-compatible UI components |
| `com.google.android.material:material` | 1.11.0 | Material Design components |
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3` | 1.2.5 | MQTT client |
| `org.eclipse.paho:org.eclipse.paho.android.service` | 1.1.1 | MQTT Android service |
| `com.google.code.gson:gson` | 2.10.1 | JSON parsing |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP client |

All dependencies are managed via Gradle and will be downloaded automatically.

#### Steps

1. **Open the project in Android Studio:**
   - Launch Android Studio.
   - Select **File → Open** and navigate to `temi-mqtt-system/temi-phone-app`.

2. **Configure the MQTT broker address:**
   - Open `src/main/java/com/example/temiphone/Config.kt`.
   - Update `MQTT_BROKER_URL` with your server's IP address:
     ```kotlin
     const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
     ```

3. **Sync Gradle and build:**
   - Click **Sync Now** when prompted, or go to **File → Sync Project with Gradle Files**.
   - Wait for all dependencies to download.

4. **Run on your phone or emulator:**
   - Connect your Android phone via USB (enable USB debugging) or start an emulator.
   - Click the **Run ▶** button in Android Studio.
   - The app requires a minimum SDK of **API 24 (Android 7.0)**.

---

### 3. Temi Pad Relay App

Android app that runs on the temi robot's pad. It:
- Listens for MQTT commands from the phone app
- Executes commands on the temi robot via the temi SDK
- Reports status back via MQTT

#### Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| `androidx.core:core-ktx` | 1.12.0 | Kotlin Android extensions |
| `androidx.appcompat:appcompat` | 1.6.1 | Backward-compatible UI components |
| `com.google.android.material:material` | 1.11.0 | Material Design components |
| `org.eclipse.paho:org.eclipse.paho.client.mqttv3` | 1.2.5 | MQTT client |
| `org.eclipse.paho:org.eclipse.paho.android.service` | 1.1.1 | MQTT Android service |
| `com.robotemi:sdk` | 0.10.77 | temi robot SDK |
| `com.google.code.gson:gson` | 2.10.1 | JSON parsing |

All dependencies are managed via Gradle and will be downloaded automatically.

#### Steps

1. **Open the project in Android Studio:**
   - Launch Android Studio.
   - Select **File → Open** and navigate to `temi-mqtt-system/temi-pad-relay`.

2. **Configure the MQTT broker address:**
   - Open `src/main/java/com/example/temirelay/Config.kt`.
   - Update `MQTT_BROKER_URL` with your server's IP address:
     ```kotlin
     const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
     ```

3. **Sync Gradle and build:**
   - Click **Sync Now** when prompted, or go to **File → Sync Project with Gradle Files**.
   - Wait for all dependencies to download.

4. **Deploy to the temi robot:**
   - Connect the temi robot's pad to your computer via USB, or use wireless ADB.
   - Click the **Run ▶** button in Android Studio.
   - The app requires a minimum SDK of **API 24 (Android 7.0)**.

> **Note:** The temi pad relay app uses [temi SDK](https://github.com/robotemi/sdk) `0.10.77` and must be installed directly on the temi robot's tablet to access the robot's hardware features.

---

## Configuration

Update `Config.kt` in both apps with your MQTT broker IP address:
```kotlin
const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
```

Both apps use the following default MQTT credentials (set in the broker's `setup.sh`):
- **Username:** `temi`
- **Password:** `temi2026`

## MQTT Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `temi/command` | Phone → Pad | Robot commands (goto, speak, stop, get_locations) |
| `temi/status` | Pad → Phone | Robot status updates |
| `temi/location` | Pad → Phone | Available locations list |