## Zeelo Location SDK - Quick Reference Guide

### API Initialization & Lifecycle

#### 1. Initialize SDK (in ControllerActivity)
```kotlin
private fun initializeZeeloLocationSDK() {
    locationApiClient = LocationApiClient(this)
    locationApiClient.initializeZeeloSDK(object : LocationApiClient.LocationCallback {
        override fun onLocationUpdated(location: Location?, gpsLocation: GPSLocation?, source: LocationSource?) {
            // Handle location updates
        }
        override fun onZoneIn(geofenceId: String, geofenceName: String) {
            // Handle zone entry
        }
        override fun onZoneOut(geofenceId: String, geofenceName: String) {
            // Handle zone exit
        }
        override fun onDirectionUpdate(direction: Double) {
            // Handle direction updates (0-360 degrees)
        }
        override fun onError(error: String) {
            // Handle errors
        }
        override fun onLocationsReceived(locations: List<String>) {
            // Legacy MQTT locations support
        }
    })
}
```

#### 2. Stop SDK (in Activity onDestroy)
```kotlin
override fun onDestroy() {
    super.onDestroy()
    locationApiClient.stopLocationService()
    mqttManager.disconnect()
}
```

### Location Data Access

#### Get Current Location
```kotlin
val currentLocation: Location? = locationApiClient.getCurrentLocation()
if (currentLocation != null) {
    val latitude = currentLocation.latitude
    val longitude = currentLocation.longitude
    val floor = currentLocation.floorLevel
    val geofence = currentLocation.geofenceName
    val hkE = currentLocation.hkE  // Hong Kong 1980 East
    val hkN = currentLocation.hkN  // Hong Kong 1980 North
}
```

#### Get GPS Location
```kotlin
val gpsLocation: GPSLocation? = locationApiClient.getCurrentGpsLocation()
if (gpsLocation != null) {
    val accuracy = gpsLocation.horizontalAccuracy
    val direction = gpsLocation.direction
    val directionAccuracy = gpsLocation.directionAccuracy
}
```

#### Get Current Direction
```kotlin
val direction: Double = locationApiClient.getCurrentDirection()  // 0-360 degrees
```

#### Get Geofence Info
```kotlin
val (geofenceId, geofenceName) = locationApiClient.getCurrentGeofence()
```

### Export & MQTT Publishing

#### Export Location as JSON
```kotlin
val locationJson = locationApiClient.exportCurrentLocationAsJson()
// Returns: JSON with location, gpsLocation, direction, and timestamp

// Example response structure:
{
  "location": {
    "latitude": 22.250763,
    "longitude": 113.565228,
    "hkE": 773218.82,
    "hkN": 812570.95,
    "floorLevel": 0,
    "isOutDoor": true,
    "geofenceName": "BLDG0001",
    "geofenceId": "4a0efbe0-05cb-11ee-850e-b18ae57d88fb",
    "floorName": "70",
    "coverageArea": true
  },
  "gpsLocation": { ... },
  "direction": 54.96,
  "locationSource": "LocationEngine",
  "timestamp": 1707575400000
}
```

#### Publish to MQTT
```kotlin
private fun publishCurrentLocationToMqtt() {
    val locationJson = locationApiClient.exportCurrentLocationAsJson()
    val command = JsonObject().apply {
        addProperty("action", "update_location")
        add("location_data", gson.fromJson(locationJson, JsonObject::class.java))
    }
    mqttManager.publishCommand(command.toString())
    // Published to: temi/location topic
}
```

### Callback Interface Definition

```kotlin
interface LocationCallback {
    fun onLocationsReceived(locations: List<String>)
    fun onLocationUpdated(location: Location?, gpsLocation: GPSLocation?, source: LocationSource?)
    fun onZoneIn(geofenceId: String, geofenceName: String)
    fun onZoneOut(geofenceId: String, geofenceName: String)
    fun onDirectionUpdate(direction: Double)
    fun onError(error: String)
}
```

### Location Object Properties

#### Location (Zeelo-determined position)
| Property | Type | Description |
|----------|------|-------------|
| latitude | Double | Latitude coordinate |
| longitude | Double | Longitude coordinate |
| hkE | Double | Hong Kong 1980 East coordinate (0 if disabled) |
| hkN | Double | Hong Kong 1980 North coordinate (0 if disabled) |
| floorLevel | Int | Floor level (e.g., 7 for floor 7) |
| isOutDoor | Boolean | Whether location is outdoors |
| geofenceName | String | Geofence identifier (e.g., "BLDG0001") |
| geofenceId | String | UUID of the geofence |
| floorName | String | Floor identifier (e.g., "70") |
| coverageArea | Boolean | Whether in fingerprinted coverage area |

#### GPSLocation (GPS satellite position)
| Property | Type | Description |
|----------|------|-------------|
| latitude | Double | GPS latitude |
| longitude | Double | GPS longitude |
| hkE | Double | Hong Kong 1980 East from GPS |
| hkN | Double | Hong Kong 1980 North from GPS |
| floorLevel | Int | Floor level |
| horizontalAccuracy | Double | GPS accuracy in meters |
| direction | Double | Device heading (0-360 degrees) |
| directionAccuracy | Double | Direction accuracy in degrees |

#### LocationSource
| Property | Type | Description |
|----------|------|-------------|
| source | Enum | "LocationEngine" or "GPS" |

### Important Notes

⚠️ **Network Requirement**
- Mobile network must be active before calling `startLocationService()`
- SDK won't function in airplane mode

⏱️ **Startup Delay**
- Wait approximately 5 seconds after `startLocationService()` for accurate data
- Initial location updates may have lower accuracy

📍 **Device Requirements**
- Gyroscope and magnetometer sensors required
- Not supported on all low-end devices (e.g., Redmi 10C)

🗺️ **Coverage**
- High accuracy in fingerprinted areas (coverage area = true)
- Falls back to GPS in non-coverage areas

🔑 **API Key**
- Must be configured in AndroidManifest.xml
- Required before deployment
- Obtain from Zeelo and set in build configuration

🛡️ **Permissions** (Runtime + Manifest)
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_BACKGROUND_LOCATION (optional, for background tracking)
- CHANGE_NETWORK_STATE

### Debugging

#### Enable Logcat Filtering
```bash
adb logcat | grep "LocationApiClient"
```

#### Check Location Updates
```kotlin
D/LocationApiClient: Location updated: lat=22.250763, lon=113.565228, floor=0, geofence=BLDG0001, source=LocationEngine
```

#### Common Issues

| Issue | Solution |
|-------|----------|
| No location updates | Check network connectivity, wait 5 seconds, verify API key |
| All outdoor detection | May be outside coverage area, verify location on map |
| High GPS variance | Wait longer after startup, try refreshing location service |
| Zone events not firing | Ensure geofences are configured in Zeelo CMS |
| Low accuracy | Device may lack gyroscope/magnetometer, check device specs |

---

For more details, see: [ZEELO_SDK_INTEGRATION.md](ZEELO_SDK_INTEGRATION.md)
