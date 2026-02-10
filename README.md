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

## Components

### 1. MQTT Broker (Mosquitto)
Runs via Docker Compose. Provides the message broker that connects the phone app to the temi pad relay.

**Setup:**
```bash
cd temi-mqtt-system/mosquitto
chmod +x setup.sh
./setup.sh
```

### 2. Temi Phone App
Android app that runs on your phone. Provides a UI to:
- Navigate the robot to saved locations
- Make the robot speak
- Stop robot movement
- View robot status in real time

### 3. Temi Pad Relay App
Android app that runs on the temi robot's pad. It:
- Listens for MQTT commands from the phone app
- Executes commands on the temi robot via the temi SDK
- Reports status back via MQTT

## Configuration

Update `Config.kt` in both apps with your MQTT broker IP address:
```kotlin
const val MQTT_BROKER_URL = "tcp://YOUR_SERVER_IP:1883"
```

## MQTT Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `temi/command` | Phone → Pad | Robot commands (goto, speak, stop, get_locations) |
| `temi/status` | Pad → Phone | Robot status updates |
| `temi/location` | Pad → Phone | Available locations list |